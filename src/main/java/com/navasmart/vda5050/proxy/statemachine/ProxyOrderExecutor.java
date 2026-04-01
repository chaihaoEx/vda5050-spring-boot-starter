package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.proxy.action.ActionHandler;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Proxy 模式下的订单执行器，以固定间隔（默认 200ms）轮询执行当前订单。
 *
 * <p>核心执行循环逻辑（每 200ms 触发一次）：
 * <ol>
 *   <li>检查是否存在 FATAL 错误 -> 如有则中止订单</li>
 *   <li>如果已到达当前路径点，处理当前节点上的动作（按 BlockingType 调度）</li>
 *   <li>当前节点所有动作完成后，推进到下一个节点并发起导航</li>
 *   <li>所有节点处理完毕后，订单完成，状态转为 IDLE</li>
 * </ol>
 *
 * <p><b>BlockingType 调度逻辑：</b>
 * <ul>
 *   <li>{@code HARD} - 阻塞型动作：一次只启动一个，必须等其完成后才能继续</li>
 *   <li>{@code SOFT} - 半阻塞型动作：可以同时启动多个，但在执行期间阻止车辆导航</li>
 *   <li>{@code NONE} - 非阻塞型动作：可以与导航并行执行</li>
 * </ul>
 *
 * <p><b>导航回调处理：</b>导航通过 {@link Vda5050ProxyVehicleAdapter#onNavigate} 异步执行。
 * 导航完成后通过 {@link java.util.concurrent.CompletableFuture#whenComplete} 回调更新状态：
 * 成功时标记 reachedWaypoint=true，失败时添加 FATAL 错误。</p>
 *
 * <p>线程安全：所有状态修改均在 {@code VehicleContext.lock()} 保护下执行。</p>
 *
 * @see ProxyOrderStateMachine
 * @see ProxyClientState
 */
@Component
public class ProxyOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProxyOrderExecutor.class);

    private final VehicleRegistry vehicleRegistry;
    private final ErrorAggregator errorAggregator;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final Vda5050Properties properties;

    public ProxyOrderExecutor(VehicleRegistry vehicleRegistry,
                              ErrorAggregator errorAggregator,
                              ActionHandlerRegistry actionHandlerRegistry,
                              Vda5050ProxyVehicleAdapter vehicleAdapter,
                              Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.errorAggregator = errorAggregator;
        this.actionHandlerRegistry = actionHandlerRegistry;
        this.vehicleAdapter = vehicleAdapter;
        this.properties = properties;
    }

    /**
     * 订单执行主循环，以固定间隔（默认 200ms）轮询所有 Proxy 模式的车辆。
     *
     * <p>间隔可通过 {@code vda5050.proxy.orderLoopIntervalMs} 配置项调整。</p>
     */
    @Scheduled(fixedDelayString = "${vda5050.proxy.orderLoopIntervalMs:200}")
    public void execute() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            executeForVehicle(ctx);
        }
    }

    /**
     * 对单辆车执行一次订单推进循环。
     * 流程：检查错误 -> 处理当前节点动作 -> 推进到下一节点。
     */
    private void executeForVehicle(VehicleContext ctx) {
        ctx.lock();
        try {
            // 只处理 RUNNING 状态的车辆，IDLE 和 PAUSED 跳过
            if (ctx.getClientState() != ProxyClientState.RUNNING) {
                return;
            }

            Order order = ctx.getCurrentOrder();
            if (order == null) {
                return;
            }

            // 第一步：检查是否存在 FATAL 错误，如有则中止订单并取消导航
            if (errorAggregator.hasFatalError(ctx)) {
                handleFatalError(ctx);
                return;
            }

            // 第二步：导航进行中时检查超时；未超时则等待导航完成回调
            if (!ctx.isReachedWaypoint()) {
                long start = ctx.getNavigationStartTime();
                long timeout = properties.getProxy().getNavigationTimeoutMs();
                if (start > 0 && timeout > 0 && (System.currentTimeMillis() - start) > timeout) {
                    log.warn("Vehicle {} navigation timeout ({}ms), aborting order",
                            ctx.getVehicleId(), timeout);
                    errorAggregator.addFatalError(ctx, "Navigation timeout", "navigationTimeout");
                    handleFatalError(ctx);
                }
                return;
            }

            // 第三步：车辆已到达当前路径点，处理节点动作
            int nodeIndex = ctx.getCurrentNodeIndex();
            List<Node> nodes = order.getNodes();

            if (nodes == null || nodeIndex >= nodes.size()) {
                // 所有节点已遍历，但需等待所有 action（含 edge action）到达终态
                if (allActionsTerminal(ctx)) {
                    log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
                    ctx.setClientState(ProxyClientState.IDLE);
                    ctx.getAgvState().setDriving(false);
                }
                return;
            }

            // 处理当前节点上的动作（按 BlockingType 调度）
            Node currentNode = nodes.get(nodeIndex);
            NodeProcessResult result = processNode(ctx, currentNode);

            if (result == NodeProcessResult.ALL_ACTIONS_DONE) {
                // 当前节点所有动作完成，推进到下一节点并发起导航
                advanceToNextNode(ctx);
            }
            // 如果 result == WAITING，说明还有动作在执行中，等下一轮循环再检查
        } finally {
            ctx.unlock();
        }
    }

    /**
     * 处理单个节点上的所有动作，按 BlockingType 调度执行。
     *
     * <p>调度规则：
     * <ul>
     *   <li>HARD 动作：串行执行，一次只运行一个，必须等其完成才能处理下一个动作或导航</li>
     *   <li>SOFT 动作：可并行启动多个，但在运行期间禁止导航（stopDriving=true）</li>
     *   <li>NONE 动作：可并行启动，不影响导航</li>
     * </ul>
     * </p>
     *
     * @param ctx  车辆上下文
     * @param node 当前处理的节点
     * @return WAITING 表示还有动作未完成，ALL_ACTIONS_DONE 表示可以推进到下一节点
     */
    private NodeProcessResult processNode(VehicleContext ctx, Node node) {
        boolean stopDriving = false;
        boolean hasRunningHard = false;

        for (Action action : node.getActions()) {
            ActionState actionState = findActionState(ctx, action.getActionId());
            if (actionState == null) {
                continue;
            }

            String status = actionState.getActionStatus();
            // BlockingType 默认为 HARD（VDA5050 规范要求）
            String blocking = action.getBlockingType();
            if (blocking == null || blocking.isEmpty()) {
                blocking = BlockingType.HARD.getValue();
            }

            // 检查正在运行的动作
            if (ActionStatus.RUNNING.getValue().equals(status)) {
                // 检查 action 超时
                Long actionStart = ctx.getActionStartTimes().get(action.getActionId());
                long actionTimeout = properties.getProxy().getActionTimeoutMs();
                if (actionStart != null && actionTimeout > 0
                        && (System.currentTimeMillis() - actionStart) > actionTimeout) {
                    log.warn("Vehicle {} action {} timed out ({}ms)",
                            ctx.getVehicleId(), action.getActionId(), actionTimeout);
                    actionState.setActionStatus(ActionStatus.FAILED.getValue());
                    actionState.setResultDescription("Action timeout");
                    ctx.getActionStartTimes().remove(action.getActionId());
                    vehicleAdapter.onActionCancel(ctx.getVehicleId(), action.getActionId());
                    // 超时后 action 变为 FAILED，下一轮循环继续推进
                    continue;
                }

                if (BlockingType.HARD.getValue().equals(blocking)) {
                    // HARD 动作正在运行：立即返回等待，不启动任何新动作
                    hasRunningHard = true;
                    return NodeProcessResult.WAITING;
                }
                if (BlockingType.SOFT.getValue().equals(blocking)) {
                    // SOFT 动作正在运行：允许其他动作继续，但阻止导航
                    stopDriving = true;
                }
                continue;
            }

            // 启动等待中的动作
            if (ActionStatus.WAITING.getValue().equals(status)) {
                if (BlockingType.HARD.getValue().equals(blocking) && !hasRunningHard) {
                    // 启动 HARD 动作并立即返回等待其完成
                    startAction(ctx, action, actionState);
                    return NodeProcessResult.WAITING;
                }
                if (BlockingType.SOFT.getValue().equals(blocking)
                        || BlockingType.NONE.getValue().equals(blocking)) {
                    // SOFT 和 NONE 动作可以直接启动，不阻塞后续动作的启动
                    startAction(ctx, action, actionState);
                    if (BlockingType.SOFT.getValue().equals(blocking)) {
                        stopDriving = true;
                    }
                }
            }
        }

        // 如果有 SOFT 动作在运行，阻止导航但继续等待
        if (stopDriving) {
            ctx.getAgvState().setDriving(false);
            return NodeProcessResult.WAITING;
        }

        // 检查是否所有动作都已进入终态（FINISHED 或 FAILED）
        boolean allDone = node.getActions().stream()
                .allMatch(a -> {
                    ActionState as = findActionState(ctx, a.getActionId());
                    if (as == null) {
                        return true;
                    }
                    String s = as.getActionStatus();
                    return ActionStatus.FINISHED.getValue().equals(s)
                            || ActionStatus.FAILED.getValue().equals(s);
                });

        return allDone ? NodeProcessResult.ALL_ACTIONS_DONE : NodeProcessResult.WAITING;
    }

    /**
     * 启动单个动作的执行。
     * 先查找 ActionHandlerRegistry 中是否有匹配的专用处理器，没有则回退到 VehicleAdapter。
     * 动作完成后通过 CompletableFuture 回调异步更新 ActionState。
     */
    private void startAction(VehicleContext ctx, Action action, ActionState actionState) {
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());
        ctx.getActionStartTimes().put(action.getActionId(), System.currentTimeMillis());
        log.debug("Vehicle {} starting action {} ({})", ctx.getVehicleId(),
                action.getActionId(), action.getActionType());

        // 优先使用已注册的 ActionHandler，未找到则回退到通用的 VehicleAdapter
        Optional<ActionHandler> handler = actionHandlerRegistry.getHandler(action.getActionType());
        CompletableFuture<ActionResult> future;

        if (handler.isPresent()) {
            future = handler.get().execute(ctx.getVehicleId(), action);
        } else {
            // 没有专用处理器，使用通用的 VehicleAdapter 兜底
            future = vehicleAdapter.onActionExecute(ctx.getVehicleId(), action);
        }

        String actionId = action.getActionId();
        String vehicleId = ctx.getVehicleId();
        future.whenComplete((result, ex) -> {
            ctx.lock();
            try {
                ctx.getActionStartTimes().remove(actionId);
                ActionState as = findActionState(ctx, actionId);
                if (as == null) {
                    return;
                }

                // 如果已被超时检测标记为 FAILED，不再覆盖结果
                if (ActionStatus.FAILED.getValue().equals(as.getActionStatus())
                        && "Action timeout".equals(as.getResultDescription())) {
                    return;
                }

                if (ex != null) {
                    as.setActionStatus(ActionStatus.FAILED.getValue());
                    as.setResultDescription(ex.getMessage());
                } else if (result.isSuccess()) {
                    as.setActionStatus(ActionStatus.FINISHED.getValue());
                    as.setResultDescription(result.getResultDescription());
                } else {
                    as.setActionStatus(ActionStatus.FAILED.getValue());
                    as.setResultDescription(result.getFailureReason());
                }

                log.debug("Vehicle {} action {} completed: {}", vehicleId, actionId,
                        as.getActionStatus());
            } finally {
                ctx.unlock();
            }
        });
    }

    /**
     * 推进到下一个节点：更新 nodeStates/edgeStates，设置 lastNodeId，然后发起异步导航。
     * 导航结果通过 CompletableFuture 回调处理：成功则标记到达，失败则记录 FATAL 错误。
     */
    private void advanceToNextNode(VehicleContext ctx) {
        Order order = ctx.getCurrentOrder();

        List<Node> nodeList = order.getNodes();
        if (nodeList == null || ctx.getCurrentNodeIndex() >= nodeList.size()) {
            log.warn("Vehicle {} node index {} out of bounds (nodes={}), aborting order",
                    ctx.getVehicleId(), ctx.getCurrentNodeIndex(),
                    nodeList == null ? "null" : nodeList.size());
            ctx.setClientState(ProxyClientState.IDLE);
            ctx.getAgvState().setDriving(false);
            return;
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
            if (allActionsTerminal(ctx)) {
                ctx.setClientState(ProxyClientState.IDLE);
                ctx.getAgvState().setDriving(false);
                log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
            }
            return;
        }

        // 发起异步导航到下一个节点，记录导航开始时间用于超时检测
        ctx.setReachedWaypoint(false);
        ctx.setNavigationStartTime(System.currentTimeMillis());
        ctx.getAgvState().setDriving(true);

        Node targetNode = nodeList.get(nextIndex);
        List<Node> waypoints = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // 收集从下一个节点到下一个已释放（released）节点之间的所有途经点和边
        for (int i = nextIndex; i < nodeList.size(); i++) {
            Node n = nodeList.get(i);
            waypoints.add(n);
            if (n.isReleased()) {
                break;
            }
        }
        List<Edge> orderEdges = order.getEdges();
        if (orderEdges != null) {
            for (int i = ctx.getCurrentNodeIndex(); i < orderEdges.size(); i++) {
                edges.add(orderEdges.get(i));
            }
        }

        // 启动当前被遍历 edge 上的所有 action（与导航并发执行）
        // edge[oldNodeIndex] 对应从 node[oldNodeIndex] 到 node[nextIndex] 的边
        if (orderEdges != null && oldNodeIndex < orderEdges.size()) {
            Edge traversedEdge = orderEdges.get(oldNodeIndex);
            for (Action edgeAction : traversedEdge.getActions()) {
                ActionState as = findActionState(ctx, edgeAction.getActionId());
                if (as != null && ActionStatus.WAITING.getValue().equals(as.getActionStatus())) {
                    startAction(ctx, edgeAction, as);
                }
            }
        }

        String vehicleId = ctx.getVehicleId();
        CompletableFuture<NavigationResult> navFuture =
                vehicleAdapter.onNavigate(vehicleId, targetNode, waypoints, edges);

        navFuture.whenComplete((result, ex) -> {
            ctx.lock();
            try {
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
    }

    private void handleFatalError(VehicleContext ctx) {
        log.error("Vehicle {} has fatal error, aborting order", ctx.getVehicleId());
        vehicleAdapter.onNavigationCancel(ctx.getVehicleId());
        ctx.setClientState(ProxyClientState.IDLE);
        ctx.getAgvState().setDriving(false);
        ctx.setNavigationStartTime(0);
        ctx.getActionStartTimes().clear();
    }

    /**
     * 检查车辆当前所有 actionStates（含 node action 和 edge action）是否均已到达终态。
     */
    private boolean allActionsTerminal(VehicleContext ctx) {
        return ctx.getAgvState().getActionStates().stream()
                .allMatch(as -> {
                    String s = as.getActionStatus();
                    return ActionStatus.FINISHED.getValue().equals(s)
                            || ActionStatus.FAILED.getValue().equals(s);
                });
    }

    private ActionState findActionState(VehicleContext ctx, String actionId) {
        return ctx.getAgvState().getActionStates().stream()
                .filter(as -> actionId.equals(as.getActionId()))
                .findFirst()
                .orElse(null);
    }

    /** 节点处理结果枚举 */
    enum NodeProcessResult {
        /** 还有动作未完成，需要等待 */
        WAITING,
        /** 所有动作已完成，可以推进到下一节点 */
        ALL_ACTIONS_DONE
    }
}
