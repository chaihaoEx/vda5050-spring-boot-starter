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
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server 模式下的 AGV 状态追踪器，负责解析收到的 State 消息并检测各种状态变更。
 *
 * <p>线程安全：状态读写在 {@code VehicleContext.lock()} 保护下执行，
 * 回调和事件发布在锁外执行以避免死锁。</p>
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
     * 锁内仅做状态比对和收集，所有回调和事件在锁外执行。
     */
    public void processState(String vehicleId, AgvState newState) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return;
        }

        // 在锁内收集需要执行的回调和事件
        List<Runnable> callbacks = new ArrayList<>();
        List<ApplicationEvent> events = new ArrayList<>();

        ctx.lock();
        try {
            AgvState prevState = ctx.getLastReceivedState();
            ctx.setLastReceivedState(newState);
            ctx.setLastSeenTimestamp(System.currentTimeMillis());

            // 每次收到 State 消息都通知
            callbacks.add(() -> serverAdapter.onStateUpdate(vehicleId, newState));

            // 节点到达检测
            if (prevState == null || !Objects.equals(prevState.getLastNodeId(), newState.getLastNodeId())) {
                if (newState.getLastNodeId() != null) {
                    String nodeId = newState.getLastNodeId();
                    int seqId = newState.getLastNodeSequenceId();
                    callbacks.add(() -> serverAdapter.onNodeReached(vehicleId, nodeId, seqId));
                    events.add(new NodeReachedEvent(this, vehicleId, nodeId, seqId));
                }
            }

            // 动作状态变更检测
            collectActionStateChanges(vehicleId, prevState, newState, callbacks);

            // 订单完成检测
            collectOrderCompletion(vehicleId, ctx, newState, callbacks, events);

            // 错误变更检测
            collectErrorChanges(vehicleId, prevState, newState, callbacks, events);
        } finally {
            ctx.unlock();
        }

        // 在锁外执行所有回调和发布事件
        callbacks.forEach(Runnable::run);
        events.forEach(eventPublisher::publishEvent);
    }

    private void collectActionStateChanges(String vehicleId, AgvState prev, AgvState curr,
                                           List<Runnable> callbacks) {
        if (prev == null) {
            return;
        }

        Map<String, String> prevStatuses = prev.getActionStates().stream()
                .collect(Collectors.toMap(ActionState::getActionId, ActionState::getActionStatus,
                        (a, b) -> b));

        for (ActionState as : curr.getActionStates()) {
            String prevStatus = prevStatuses.get(as.getActionId());
            if (prevStatus == null || !prevStatus.equals(as.getActionStatus())) {
                callbacks.add(() -> serverAdapter.onActionStateChanged(vehicleId, as));
            }
        }
    }

    private void collectOrderCompletion(String vehicleId, VehicleContext ctx, AgvState state,
                                        List<Runnable> callbacks, List<ApplicationEvent> events) {
        Order sentOrder = ctx.getLastSentOrder();
        if (sentOrder == null) {
            return;
        }
        if (!sentOrder.getOrderId().equals(state.getOrderId())) {
            return;
        }
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

        String orderId = state.getOrderId();
        if (hasFailedActions || hasFatalErrors) {
            callbacks.add(() -> serverAdapter.onOrderFailed(vehicleId, orderId, state.getErrors()));
            events.add(new OrderFailedEvent(this, vehicleId, orderId, state.getErrors()));
        } else {
            callbacks.add(() -> serverAdapter.onOrderCompleted(vehicleId, orderId));
            events.add(new OrderCompletedEvent(this, vehicleId, orderId));
        }

        ctx.setLastSentOrder(null);
        log.info("Vehicle {} order {} completed (failed={})", vehicleId,
                orderId, hasFailedActions || hasFatalErrors);
    }

    private void collectErrorChanges(String vehicleId, AgvState prev, AgvState curr,
                                     List<Runnable> callbacks, List<ApplicationEvent> events) {
        if (prev == null) {
            curr.getErrors().forEach(e -> {
                callbacks.add(() -> serverAdapter.onErrorReported(vehicleId, e));
                events.add(new ErrorOccurredEvent(this, vehicleId, e));
            });
            return;
        }

        Set<String> prevErrorTypes = prev.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());
        Set<String> currErrorTypes = curr.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());

        curr.getErrors().stream()
                .filter(e -> !prevErrorTypes.contains(e.getErrorType() + ":" + e.getErrorDescription()))
                .forEach(e -> {
                    callbacks.add(() -> serverAdapter.onErrorReported(vehicleId, e));
                    events.add(new ErrorOccurredEvent(this, vehicleId, e));
                });

        prev.getErrors().stream()
                .filter(e -> !currErrorTypes.contains(e.getErrorType() + ":" + e.getErrorDescription()))
                .forEach(e -> callbacks.add(() -> serverAdapter.onErrorCleared(vehicleId, e)));
    }

    private boolean allActionsTerminal(List<ActionState> actionStates) {
        return actionStates.stream().allMatch(as -> {
            String status = as.getActionStatus();
            return ActionStatus.FINISHED.getValue().equals(status)
                    || ActionStatus.FAILED.getValue().equals(status);
        });
    }
}
