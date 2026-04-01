package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import com.navasmart.vda5050.event.ErrorOccurredEvent;
import com.navasmart.vda5050.event.NodeReachedEvent;
import com.navasmart.vda5050.event.OrderCompletedEvent;
import com.navasmart.vda5050.event.OrderFailedEvent;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server 模式下的 AGV 状态追踪器，负责解析收到的 State 消息并检测各种状态变更。
 *
 * <p>核心变更检测逻辑：
 * <ul>
 *   <li><b>节点到达检测</b>：对比 lastNodeId 的变化</li>
 *   <li><b>动作状态变更检测</b>：对比每个 actionId 的 actionStatus 变化</li>
 *   <li><b>订单完成检测</b>：nodeStates 为空 + 不在行驶 + 所有动作终态</li>
 *   <li><b>错误变更检测</b>：对比 errors 列表的新增和移除</li>
 * </ul>
 *
 * <p>检测到变更后，通过 {@link Vda5050ServerAdapter} 的对应回调方法通知用户。</p>
 *
 * <p>线程安全：所有操作均在 {@code VehicleContext.lock()} 保护下执行。</p>
 *
 * @see Vda5050ServerAdapter
 */
@Component
public class AgvStateTracker {

    private static final Logger log = LoggerFactory.getLogger(AgvStateTracker.class);

    private final VehicleRegistry vehicleRegistry;
    private final Vda5050ServerAdapter serverAdapter;
    private final ApplicationEventPublisher eventPublisher;

    public AgvStateTracker(VehicleRegistry vehicleRegistry, Vda5050ServerAdapter serverAdapter,
                           ApplicationEventPublisher eventPublisher) {
        this.vehicleRegistry = vehicleRegistry;
        this.serverAdapter = serverAdapter;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理收到的 AGV State 消息，执行变更检测并触发对应的回调。
     *
     * @param vehicleId 车辆标识符
     * @param newState  收到的新 State 消息
     */
    public void processState(String vehicleId, AgvState newState) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return;
        }

        ctx.lock();
        try {
            AgvState prevState = ctx.getLastReceivedState();
            ctx.setLastReceivedState(newState);
            ctx.setLastSeenTimestamp(System.currentTimeMillis());

            // 每次收到 State 消息都通知（无论是否有变更）
            serverAdapter.onStateUpdate(vehicleId, newState);

            // 节点到达检测：对比前后两次的 lastNodeId，不同则表示到达了新节点
            if (prevState == null || !Objects.equals(prevState.getLastNodeId(), newState.getLastNodeId())) {
                if (newState.getLastNodeId() != null) {
                    serverAdapter.onNodeReached(vehicleId, newState.getLastNodeId(),
                            newState.getLastNodeSequenceId());
                    eventPublisher.publishEvent(new NodeReachedEvent(
                            this, vehicleId, newState.getLastNodeId(), newState.getLastNodeSequenceId()));
                }
            }

            // 动作状态变更检测：逐个对比 actionId 的 status 变化
            detectActionStateChanges(vehicleId, prevState, newState);

            // 订单完成检测：nodeStates 空 + 不在行驶 + 所有动作终态
            detectOrderCompletion(vehicleId, ctx, newState);

            // 错误变更检测：对比 errorType:errorDescription 组合的新增和移除
            detectErrorChanges(vehicleId, prevState, newState);
        } finally {
            ctx.unlock();
        }
    }

    /**
     * 检测动作状态变更：将前一次的 actionId->status 映射与当前对比。
     * 新出现的 actionId 或 status 变化的 actionId 都会触发回调。
     */
    private void detectActionStateChanges(String vehicleId, AgvState prev, AgvState curr) {
        if (prev == null) {
            return;
        }

        // 构建前一次状态的 actionId -> actionStatus 映射
        Map<String, String> prevStatuses = prev.getActionStates().stream()
                .collect(Collectors.toMap(ActionState::getActionId, ActionState::getActionStatus,
                        (a, b) -> b));

        // 逐个对比当前状态中每个动作的 status 是否发生变化
        for (ActionState as : curr.getActionStates()) {
            String prevStatus = prevStatuses.get(as.getActionId());
            if (prevStatus == null || !prevStatus.equals(as.getActionStatus())) {
                serverAdapter.onActionStateChanged(vehicleId, as);
            }
        }
    }

    /**
     * 检测订单完成：三个条件同时满足时认为订单完成。
     * 根据是否有失败动作或 FATAL 错误区分成功完成和失败完成。
     */
    private void detectOrderCompletion(String vehicleId, VehicleContext ctx, AgvState state) {
        Order sentOrder = ctx.getLastSentOrder();
        if (sentOrder == null) {
            return;
        }

        // 只追踪自己发送的订单
        if (!sentOrder.getOrderId().equals(state.getOrderId())) {
            return;
        }

        // 订单完成判定条件：nodeStates 为空 + 不在行驶 + 所有动作处于终态
        if (!state.getNodeStates().isEmpty()) {
            return;
        }
        if (state.isDriving()) {
            return;
        }
        if (!allActionsTerminal(state.getActionStates())) {
            return;
        }

        boolean hasFailedActions = state.getActionStates().stream()
                .anyMatch(as -> ActionStatus.FAILED.getValue().equals(as.getActionStatus()));
        boolean hasFatalErrors = state.getErrors().stream()
                .anyMatch(e -> ErrorLevel.FATAL.getValue().equals(e.getErrorLevel()));

        if (hasFailedActions || hasFatalErrors) {
            serverAdapter.onOrderFailed(vehicleId, state.getOrderId(), state.getErrors());
            eventPublisher.publishEvent(new OrderFailedEvent(
                    this, vehicleId, state.getOrderId(), state.getErrors()));
        } else {
            serverAdapter.onOrderCompleted(vehicleId, state.getOrderId());
            eventPublisher.publishEvent(new OrderCompletedEvent(
                    this, vehicleId, state.getOrderId()));
        }

        ctx.setLastSentOrder(null);
        log.info("Vehicle {} order {} completed (failed={})", vehicleId,
                state.getOrderId(), hasFailedActions || hasFatalErrors);
    }

    /**
     * 检测错误变更：通过 errorType:errorDescription 组合作为唯一标识，
     * 对比前后两次 State 中的错误列表，识别新增和移除的错误。
     */
    private void detectErrorChanges(String vehicleId, AgvState prev, AgvState curr) {
        if (prev == null) {
            // 首次收到状态，所有错误都视为新错误
            curr.getErrors().forEach(e -> {
                serverAdapter.onErrorReported(vehicleId, e);
                eventPublisher.publishEvent(new ErrorOccurredEvent(this, vehicleId, e));
            });
            return;
        }

        // 使用 errorType:errorDescription 作为错误的唯一标识进行集合对比
        Set<String> prevErrorTypes = prev.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());
        Set<String> currErrorTypes = curr.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());

        // 在当前状态中存在但在前一次状态中不存在的 -> 新增错误
        curr.getErrors().stream()
                .filter(e -> !prevErrorTypes.contains(e.getErrorType() + ":" + e.getErrorDescription()))
                .forEach(e -> {
                    serverAdapter.onErrorReported(vehicleId, e);
                    eventPublisher.publishEvent(new ErrorOccurredEvent(this, vehicleId, e));
                });

        // 在前一次状态中存在但在当前状态中不存在的 -> 已清除的错误
        prev.getErrors().stream()
                .filter(e -> !currErrorTypes.contains(e.getErrorType() + ":" + e.getErrorDescription()))
                .forEach(e -> serverAdapter.onErrorCleared(vehicleId, e));
    }

    private boolean allActionsTerminal(List<ActionState> actionStates) {
        return actionStates.stream().allMatch(as -> {
            String status = as.getActionStatus();
            return ActionStatus.FINISHED.getValue().equals(status)
                    || ActionStatus.FAILED.getValue().equals(status);
        });
    }
}
