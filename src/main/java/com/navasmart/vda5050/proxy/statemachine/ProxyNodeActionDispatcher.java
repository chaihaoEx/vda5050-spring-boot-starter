package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.proxy.action.ActionHandler;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 节点动作调度器，负责按 BlockingType 规则调度和执行节点上的动作。
 *
 * <p>从 {@link ProxyOrderExecutor} 拆分而来，专注于：
 * <ul>
 *   <li>按 BlockingType 规则调度动作（HARD=串行独占, SOFT=并行但阻止导航, NONE=并行不阻塞）</li>
 *   <li>通过 {@link ActionHandlerRegistry} 或 {@link Vda5050ProxyVehicleAdapter} 启动动作执行</li>
 *   <li>检测动作超时并标记为 FAILED</li>
 *   <li>查询动作状态</li>
 * </ul>
 *
 * <p>线程安全：所有方法假定调用方已持有 {@code VehicleContext.lock()}。
 * 异步回调（动作完成）使用 {@code tryLock(5s)} 获取锁。</p>
 *
 * @see ProxyOrderExecutor
 * @see ProxyNavigationController
 */
public class ProxyNodeActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProxyNodeActionDispatcher.class);

    private final ActionHandlerRegistry actionHandlerRegistry;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final Vda5050Properties properties;

    public ProxyNodeActionDispatcher(ActionHandlerRegistry actionHandlerRegistry,
                                     Vda5050ProxyVehicleAdapter vehicleAdapter,
                                     Vda5050Properties properties) {
        this.actionHandlerRegistry = actionHandlerRegistry;
        this.vehicleAdapter = vehicleAdapter;
        this.properties = properties;
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
     *
     * @param ctx                车辆上下文（调用方必须已持锁）
     * @param node               当前处理的节点
     * @param cancelledActionIds 收集因超时被取消的 actionId，用于锁外回调
     * @return WAITING 表示还有动作未完成，ALL_ACTIONS_DONE 表示可以推进到下一节点
     */
    NodeProcessResult processNode(VehicleContext ctx, Node node, List<String> cancelledActionIds) {
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
                    ctx.removeActionStartTime(action.getActionId());
                    ctx.addTimedOutActionId(action.getActionId());
                    cancelledActionIds.add(action.getActionId());
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
     *
     * @param ctx         车辆上下文（调用方必须已持锁）
     * @param action      要执行的动作
     * @param actionState 对应的 ActionState
     */
    void startAction(VehicleContext ctx, Action action, ActionState actionState) {
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());
        ctx.putActionStartTime(action.getActionId(), System.currentTimeMillis());
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
        // Capture orderId so the async callback can check if order was cancelled
        Order currentOrder = ctx.getCurrentOrder();
        String orderId = currentOrder != null ? currentOrder.getOrderId() : null;
        future.whenComplete((result, ex) -> {
            try {
                if (!ctx.tryLock(5, TimeUnit.SECONDS)) {
                    log.error("Vehicle {} action {} callback failed to acquire lock within 5s",
                            vehicleId, actionId);
                    return;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Vehicle {} action {} callback interrupted while acquiring lock",
                        vehicleId, actionId);
                return;
            }
            try {
                // 如果订单已被取消，忽略回调，避免覆盖已取消订单的状态
                if (orderId != null && ctx.isCancelledOrder(orderId)) {
                    ctx.removeCancelledOrderId(orderId);
                    return;
                }

                ctx.removeActionStartTime(actionId);
                ActionState as = findActionState(ctx, actionId);
                if (as == null) {
                    return;
                }

                // 如果已被超时检测标记为 FAILED，不再覆盖结果
                if (ctx.isTimedOutAction(actionId)) {
                    ctx.removeTimedOutActionId(actionId);
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
     * 检查车辆当前所有 actionStates（含 node action 和 edge action）是否均已到达终态。
     *
     * @param ctx 车辆上下文（调用方必须已持锁）
     * @return 所有 action 均为 FINISHED 或 FAILED 时返回 true
     */
    boolean allActionsTerminal(VehicleContext ctx) {
        return ctx.getAgvState().getActionStates().stream()
                .allMatch(as -> {
                    String s = as.getActionStatus();
                    return ActionStatus.FINISHED.getValue().equals(s)
                            || ActionStatus.FAILED.getValue().equals(s);
                });
    }

    /**
     * 在车辆的 actionStates 中查找指定 actionId 的 ActionState。
     *
     * @param ctx      车辆上下文（调用方必须已持锁）
     * @param actionId 要查找的动作 ID
     * @return 找到的 ActionState，未找到返回 null
     */
    ActionState findActionState(VehicleContext ctx, String actionId) {
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
