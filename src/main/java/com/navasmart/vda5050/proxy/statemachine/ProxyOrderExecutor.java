package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.navasmart.vda5050.event.OrderCompletedEvent;
import com.navasmart.vda5050.event.OrderFailedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * <p>动作调度委托给 {@link ProxyNodeActionDispatcher}，导航控制委托给 {@link ProxyNavigationController}。</p>
 *
 * <p>线程安全：所有状态修改均在 {@code VehicleContext.lock()} 保护下执行。
 * 适配器回调和事件发布在锁外执行以避免死锁。</p>
 *
 * @see ProxyOrderStateMachine
 * @see ProxyNodeActionDispatcher
 * @see ProxyNavigationController
 */
@Component
public class ProxyOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProxyOrderExecutor.class);

    private final VehicleRegistry vehicleRegistry;
    private final ErrorAggregator errorAggregator;
    private final ProxyNodeActionDispatcher actionDispatcher;
    private final ProxyNavigationController navigationController;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final Vda5050Properties properties;
    private final ApplicationEventPublisher eventPublisher;

    private volatile boolean shuttingDown = false;

    public ProxyOrderExecutor(VehicleRegistry vehicleRegistry,
                              ErrorAggregator errorAggregator,
                              ProxyNodeActionDispatcher actionDispatcher,
                              ProxyNavigationController navigationController,
                              Vda5050ProxyVehicleAdapter vehicleAdapter,
                              Vda5050Properties properties,
                              ApplicationEventPublisher eventPublisher) {
        this.vehicleRegistry = vehicleRegistry;
        this.errorAggregator = errorAggregator;
        this.actionDispatcher = actionDispatcher;
        this.navigationController = navigationController;
        this.vehicleAdapter = vehicleAdapter;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 订单执行主循环，以固定间隔（默认 200ms）轮询所有 Proxy 模式的车辆。
     *
     * <p>间隔可通过 {@code vda5050.proxy.orderLoopIntervalMs} 配置项调整。</p>
     */
    @Scheduled(fixedDelayString = "${vda5050.proxy.orderLoopIntervalMs:200}")
    public void execute() {
        if (shuttingDown) {
            return;
        }
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            executeForVehicle(ctx);
        }
    }

    /**
     * 通知执行器进入关闭流程，停止接受新的执行循环。
     */
    public void shutdown() {
        this.shuttingDown = true;
        log.info("ProxyOrderExecutor shutdown initiated");
    }

    /**
     * 检查所有 Proxy 车辆是否都处于 IDLE 状态（无进行中的工作）。
     */
    public boolean isIdle() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            ctx.lock();
            try {
                if (ctx.getClientState() != ProxyClientState.IDLE) {
                    return false;
                }
            } finally {
                ctx.unlock();
            }
        }
        return true;
    }

    /**
     * 对单辆车执行一次订单推进循环。
     * 流程：检查错误 -> 处理当前节点动作 -> 推进到下一节点。
     */
    private void executeForVehicle(VehicleContext ctx) {
        ProxyNavigationController.OrderCompletionInfo completionInfo = null;
        boolean needsCancelNavigation = false;
        String cancelVehicleId = null;
        List<String> cancelledActionIds = new ArrayList<>();

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

            cancelVehicleId = ctx.getVehicleId();

            // 第一步：检查是否存在 FATAL 错误，如有则中止订单并取消导航
            if (errorAggregator.hasFatalError(ctx)) {
                needsCancelNavigation = true;
                handleFatalError(ctx);
                completionInfo = new ProxyNavigationController.OrderCompletionInfo(
                        ctx.getVehicleId(), order.getOrderId(), true);
            } else if (!ctx.isReachedWaypoint()) {
                // 第二步：导航进行中时检查超时；未超时则等待导航完成回调
                long start = ctx.getNavigationStartTime();
                long timeout = properties.getProxy().getNavigationTimeoutMs();
                if (start > 0 && timeout > 0 && (System.currentTimeMillis() - start) > timeout) {
                    log.warn("Vehicle {} navigation timeout ({}ms), aborting order",
                            ctx.getVehicleId(), timeout);
                    errorAggregator.addFatalError(ctx, "Navigation timeout", "navigationTimeout");
                    needsCancelNavigation = true;
                    handleFatalError(ctx);
                    completionInfo = new ProxyNavigationController.OrderCompletionInfo(
                            ctx.getVehicleId(), order.getOrderId(), true);
                }
            } else {
                // 第三步：车辆已到达当前路径点，处理节点动作
                int nodeIndex = ctx.getCurrentNodeIndex();
                List<Node> nodes = order.getNodes();

                if (nodes == null || nodeIndex >= nodes.size()) {
                    // 所有节点已遍历，但需等待所有 action（含 edge action）到达终态
                    if (actionDispatcher.allActionsTerminal(ctx)) {
                        log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
                        ctx.setClientState(ProxyClientState.IDLE);
                        ctx.getAgvState().setDriving(false);
                        completionInfo = new ProxyNavigationController.OrderCompletionInfo(
                                ctx.getVehicleId(), order.getOrderId(), false);
                    }
                } else {
                    // 处理当前节点上的动作（按 BlockingType 调度）
                    Node currentNode = nodes.get(nodeIndex);
                    ProxyNodeActionDispatcher.NodeProcessResult result =
                            actionDispatcher.processNode(ctx, currentNode, cancelledActionIds);

                    if (result == ProxyNodeActionDispatcher.NodeProcessResult.ALL_ACTIONS_DONE) {
                        // 当前节点所有动作完成，推进到下一节点并发起导航
                        completionInfo = navigationController.advanceToNextNode(ctx);
                    }
                    // 如果 result == WAITING，说明还有动作在执行中，等下一轮循环再检查
                }
            }
        } finally {
            ctx.unlock();
        }

        // 适配器回调和事件在锁外执行
        if (needsCancelNavigation) {
            vehicleAdapter.onNavigationCancel(cancelVehicleId);
        }
        for (String actionId : cancelledActionIds) {
            vehicleAdapter.onActionCancel(cancelVehicleId, actionId);
        }
        if (completionInfo != null) {
            if (completionInfo.failed()) {
                eventPublisher.publishEvent(new OrderFailedEvent(
                        this, completionInfo.vehicleId(), completionInfo.orderId(), Collections.emptyList()));
            } else {
                eventPublisher.publishEvent(new OrderCompletedEvent(
                        this, completionInfo.vehicleId(), completionInfo.orderId()));
            }
        }
    }

    private void handleFatalError(VehicleContext ctx) {
        log.error("Vehicle {} has fatal error, aborting order", ctx.getVehicleId());
        ctx.setClientState(ProxyClientState.IDLE);
        ctx.setCurrentOrder(null);
        ctx.getAgvState().setDriving(false);
        ctx.setNavigationStartTime(0);
        ctx.clearActionStartTimes();
    }
}
