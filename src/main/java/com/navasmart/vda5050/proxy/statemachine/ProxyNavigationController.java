package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 导航控制器，负责节点推进、航点构建和异步导航管理。
 *
 * <p>从 {@link ProxyOrderExecutor} 拆分而来，专注于：
 * <ul>
 *   <li>推进到下一个节点（更新 nodeStates/edgeStates/lastNodeId）</li>
 *   <li>构建导航航点列表和边列表</li>
 *   <li>启动边上的动作（与导航并发执行）</li>
 *   <li>通过 {@link Vda5050ProxyVehicleAdapter#onNavigate} 发起异步导航</li>
 *   <li>处理导航完成回调（成功标记到达，失败记录 FATAL 错误）</li>
 * </ul>
 *
 * <p>线程安全：所有方法假定调用方已持有 {@code VehicleContext.lock()}。
 * 导航完成回调使用 {@code tryLock(5s)} 获取锁。</p>
 *
 * @see ProxyOrderExecutor
 * @see ProxyNodeActionDispatcher
 */
public class ProxyNavigationController {

    private static final Logger log = LoggerFactory.getLogger(ProxyNavigationController.class);

    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final ErrorAggregator errorAggregator;
    private final ProxyNodeActionDispatcher actionDispatcher;

    public ProxyNavigationController(Vda5050ProxyVehicleAdapter vehicleAdapter,
                                     ErrorAggregator errorAggregator,
                                     ProxyNodeActionDispatcher actionDispatcher) {
        this.vehicleAdapter = vehicleAdapter;
        this.errorAggregator = errorAggregator;
        this.actionDispatcher = actionDispatcher;
    }

    /**
     * 推进到下一个节点：更新 nodeStates/edgeStates，设置 lastNodeId，然后发起异步导航。
     * 导航结果通过 CompletableFuture 回调处理：成功则标记到达，失败则记录 FATAL 错误。
     *
     * @param ctx 车辆上下文（调用方必须已持锁）
     * @return 订单完成信息（如果订单已完成），否则返回 null
     */
    OrderCompletionInfo advanceToNextNode(VehicleContext ctx) {
        Order order = ctx.getCurrentOrder();

        List<Node> nodeList = order.getNodes();
        if (nodeList == null || ctx.getCurrentNodeIndex() >= nodeList.size()) {
            log.warn("Vehicle {} node index {} out of bounds (nodes={}), aborting order",
                    ctx.getVehicleId(), ctx.getCurrentNodeIndex(),
                    nodeList == null ? "null" : nodeList.size());
            ctx.setClientState(ProxyClientState.IDLE);
            ctx.getAgvState().setDriving(false);
            return null;
        }

        int oldNodeIndex = ctx.getCurrentNodeIndex();
        int nextIndex = oldNodeIndex + 1;

        // 从 nodeStates 中移除已处理的节点（VDA5050 规范：已通过的节点不再出现在 State 中）
        if (!ctx.getAgvState().getNodeStates().isEmpty()) {
            ctx.getAgvState().getNodeStates().remove(0);
        }

        // Update last node
        Node currentNode = nodeList.get(ctx.getCurrentNodeIndex());
        ctx.getAgvState().setLastNodeId(currentNode.getNodeId());
        ctx.getAgvState().setLastNodeSequenceId(currentNode.getSequenceId());

        // Remove edge state if exists
        if (!ctx.getAgvState().getEdgeStates().isEmpty()) {
            ctx.getAgvState().getEdgeStates().remove(0);
        }

        ctx.setCurrentNodeIndex(nextIndex);

        if (nextIndex >= nodeList.size()) {
            // 所有节点已遍历；如果所有 action（含 edge action）均已终态，标记完成
            // 否则留待下一轮 executeForVehicle 循环检查
            if (actionDispatcher.allActionsTerminal(ctx)) {
                ctx.setClientState(ProxyClientState.IDLE);
                ctx.getAgvState().setDriving(false);
                log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
                return new OrderCompletionInfo(ctx.getVehicleId(), order.getOrderId(), false);
            }
            return null;
        }

        // 发起异步导航到下一个节点，记录导航开始时间用于超时检测
        ctx.setReachedWaypoint(false);
        ctx.setNavigationStartTime(System.currentTimeMillis());
        ctx.getAgvState().setDriving(true);

        Node targetNode = nodeList.get(nextIndex);
        List<Node> waypoints = buildWaypoints(nodeList, nextIndex);
        List<Edge> edges = buildEdges(order, ctx.getCurrentNodeIndex());

        // 启动当前被遍历 edge 上的所有 action（与导航并发执行）
        startEdgeActions(ctx, order, oldNodeIndex);

        String vehicleId = ctx.getVehicleId();
        String navOrderId = order.getOrderId();
        CompletableFuture<NavigationResult> navFuture =
                vehicleAdapter.onNavigate(vehicleId, targetNode, waypoints, edges);

        navFuture.whenComplete((result, ex) -> {
            try {
                if (!ctx.tryLock(5, TimeUnit.SECONDS)) {
                    log.error("Vehicle {} navigation callback failed to acquire lock within 5s",
                            vehicleId);
                    return;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Vehicle {} navigation callback interrupted while acquiring lock",
                        vehicleId);
                return;
            }
            try {
                // 如果订单已被取消，忽略导航结果，避免覆盖已取消订单的状态
                if (ctx.isCancelledOrder(navOrderId)) {
                    ctx.removeCancelledOrderId(navOrderId);
                    return;
                }

                ctx.setNavigationStartTime(0);
                if (ex != null) {
                    errorAggregator.addFatalError(ctx, "Navigation failed: " + ex.getMessage(),
                            "navigationError");
                    ctx.getAgvState().setDriving(false);
                } else if (result.isSuccess()) {
                    ctx.setReachedWaypoint(true);
                    ctx.getAgvState().setDriving(false);
                } else {
                    errorAggregator.addFatalError(ctx,
                            "Navigation failed: " + result.getFailureReason(), "navigationError");
                    ctx.getAgvState().setDriving(false);
                }
            } finally {
                ctx.unlock();
            }
        });
        return null;
    }

    /**
     * 收集从指定索引到下一个已释放（released）节点之间的所有途经点。
     */
    private List<Node> buildWaypoints(List<Node> nodeList, int startIndex) {
        List<Node> waypoints = new ArrayList<>();
        for (int i = startIndex; i < nodeList.size(); i++) {
            Node n = nodeList.get(i);
            waypoints.add(n);
            if (n.isReleased()) {
                break;
            }
        }
        return waypoints;
    }

    /**
     * 收集从指定索引开始的所有边。
     */
    private List<Edge> buildEdges(Order order, int fromEdgeIndex) {
        List<Edge> edges = new ArrayList<>();
        List<Edge> orderEdges = order.getEdges();
        if (orderEdges != null) {
            for (int i = fromEdgeIndex; i < orderEdges.size(); i++) {
                edges.add(orderEdges.get(i));
            }
        }
        return edges;
    }

    /**
     * 启动当前被遍历 edge 上的所有 WAITING 状态的 action。
     */
    private void startEdgeActions(VehicleContext ctx, Order order, int edgeIndex) {
        List<Edge> orderEdges = order.getEdges();
        if (orderEdges != null && edgeIndex < orderEdges.size()) {
            Edge traversedEdge = orderEdges.get(edgeIndex);
            for (Action edgeAction : traversedEdge.getActions()) {
                ActionState as = actionDispatcher.findActionState(ctx, edgeAction.getActionId());
                if (as != null && ActionStatus.WAITING.getValue().equals(as.getActionStatus())) {
                    actionDispatcher.startAction(ctx, edgeAction, as);
                }
            }
        }
    }

    /** 订单完成信息，用于在锁外发布事件 */
    record OrderCompletionInfo(String vehicleId, String orderId, boolean failed) {}
}
