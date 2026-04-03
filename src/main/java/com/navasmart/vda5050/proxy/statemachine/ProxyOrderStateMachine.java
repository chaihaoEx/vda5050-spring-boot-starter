package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.EdgeState;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.NodeState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.validation.OrderValidator;
import com.navasmart.vda5050.event.OrderReceivedEvent;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Proxy 模式下的订单状态机，负责处理收到的 VDA5050 订单和即时动作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>接收并验证新订单（{@link #receiveOrder}）</li>
 *   <li>处理即时动作：cancelOrder、startPause/stopPause、factsheetRequest 等（{@link #receiveInstantActions}）</li>
 *   <li>初始化 AgvState 中的 nodeStates、edgeStates、actionStates</li>
 *   <li>管理 {@link ProxyClientState} 状态转换</li>
 * </ul>
 *
 * <p><b>订单接受条件：</b>
 * <ul>
 *   <li>IDLE 状态：无条件接受</li>
 *   <li>RUNNING 状态：仅接受同 orderId 的订单更新，或当前订单已完成时接受新订单</li>
 *   <li>PAUSED 状态：拒绝任何新订单</li>
 * </ul>
 *
 * <p>线程安全：所有操作均在 {@code VehicleContext.lock()} 保护下执行。</p>
 *
 * @see ProxyClientState
 * @see ProxyOrderExecutor
 */
@Component
public class ProxyOrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ProxyOrderStateMachine.class);

    private final ErrorAggregator errorAggregator;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final Vda5050ProxyStateProvider stateProvider;
    private final MqttGateway mqttGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderValidator orderValidator;

    public ProxyOrderStateMachine(ErrorAggregator errorAggregator,
                                  Vda5050ProxyVehicleAdapter vehicleAdapter,
                                  Vda5050ProxyStateProvider stateProvider,
                                  MqttGateway mqttGateway,
                                  ApplicationEventPublisher eventPublisher,
                                  OrderValidator orderValidator) {
        this.errorAggregator = errorAggregator;
        this.vehicleAdapter = vehicleAdapter;
        this.stateProvider = stateProvider;
        this.mqttGateway = mqttGateway;
        this.eventPublisher = eventPublisher;
        this.orderValidator = orderValidator;
    }

    /**
     * 接收并处理新的 VDA5050 订单。
     *
     * <p>订单接受条件：IDLE 状态无条件接受；RUNNING 状态仅接受同 orderId 的更新或当前订单已完成；
     * PAUSED 状态拒绝。接受后初始化 AgvState 并将状态转为 RUNNING。</p>
     *
     * @param ctx   车辆上下文
     * @param order 收到的订单消息
     */
    public void receiveOrder(VehicleContext ctx, Order order) {
        boolean accepted = false;
        String vehicleId;

        ctx.lock();
        try {
            vehicleId = ctx.getVehicleId();

            // 入站校验：结构和序列一致性
            List<String> validationErrors = orderValidator.validate(order);
            if (!validationErrors.isEmpty()) {
                for (String error : validationErrors) {
                    errorAggregator.addWarning(ctx, error, "validationError",
                            Map.of("orderId", String.valueOf(order.getOrderId())));
                }
                log.warn("Vehicle {} rejected order due to validation errors: {}",
                        vehicleId, validationErrors);
                return;
            }

            // 订单更新时的 orderUpdateId 单调递增校验
            List<String> updateErrors = orderValidator.validateUpdate(order, ctx.getCurrentOrder());
            if (!updateErrors.isEmpty()) {
                for (String error : updateErrors) {
                    errorAggregator.addWarning(ctx, error, "validationError",
                            Map.of("orderId", order.getOrderId()));
                }
                log.warn("Vehicle {} rejected order update: {}", vehicleId, updateErrors);
                return;
            }

            if (!canAcceptOrder(ctx, order)) {
                errorAggregator.addWarning(ctx, "Cannot accept order in current state",
                        "orderUpdateError", Map.of("orderId", order.getOrderId()));
                return;
            }

            log.info("Vehicle {} accepting order: {}", vehicleId, order.getOrderId());

            ctx.setCurrentOrder(order);
            ctx.setCurrentNodeIndex(0);
            ctx.setNextStopIndex(0);
            ctx.setReachedWaypoint(true);
            ctx.clearCancelledOrderIds();
            initAgvState(ctx, order);
            ctx.setClientState(ProxyClientState.RUNNING);
            accepted = true;
        } finally {
            ctx.unlock();
        }

        // 事件在锁外发布，避免死锁
        if (accepted) {
            eventPublisher.publishEvent(new OrderReceivedEvent(
                    this, vehicleId, order.getOrderId(), order.getOrderUpdateId()));
        }
    }

    /**
     * 接收并处理 VDA5050 即时动作消息。
     *
     * <p>逐个处理消息中的每个动作：
     * <ul>
     *   <li>{@code cancelOrder} - 取消当前订单，状态转为 IDLE</li>
     *   <li>{@code startPause} - 暂停执行，RUNNING -> PAUSED</li>
     *   <li>{@code stopPause} - 恢复执行，PAUSED -> RUNNING</li>
     *   <li>{@code factsheetRequest} - 获取并发布车辆 Factsheet</li>
     *   <li>其他类型 - 作为即时动作添加到 actionStates，由执行器处理</li>
     * </ul>
     *
     * @param ctx            车辆上下文
     * @param instantActions 即时动作消息
     */
    public void receiveInstantActions(VehicleContext ctx, InstantActions instantActions) {
        String manufacturer;
        String serialNumber;
        String vehicleId;
        // Deferred adapter callbacks to execute outside lock
        boolean deferredCancel = false;
        boolean deferredPause = false;
        boolean deferredResume = false;
        Action deferredFactsheetAction = null;

        ctx.lock();
        try {
            manufacturer = ctx.getManufacturer();
            serialNumber = ctx.getSerialNumber();
            vehicleId = ctx.getVehicleId();
            for (Action action : instantActions.getInstantActions()) {
                InstantActionResult result = processInstantAction(ctx, action);
                if (result.deferredCallback != null) {
                    switch (result.deferredCallback) {
                        case CANCEL -> deferredCancel = true;
                        case PAUSE -> deferredPause = true;
                        case RESUME -> deferredResume = true;
                        case FACTSHEET -> deferredFactsheetAction = result.sourceAction;
                        default -> { }
                    }
                }
            }
        } finally {
            ctx.unlock();
        }

        // Adapter callbacks and MQTT publish outside lock
        if (deferredCancel) {
            vehicleAdapter.onOrderCancel(vehicleId);
        }
        if (deferredPause) {
            vehicleAdapter.onPause(vehicleId);
        }
        if (deferredResume) {
            vehicleAdapter.onResume(vehicleId);
        }
        if (deferredFactsheetAction != null) {
            handleFactsheetRequest(ctx, deferredFactsheetAction, manufacturer, serialNumber);
        }
    }

    /** Deferred callback type for adapter calls that must execute outside lock */
    private enum DeferredCallback { CANCEL, PAUSE, RESUME, FACTSHEET }

    /** Result of processing a single instant action — carries deferred work to execute outside lock */
    private record InstantActionResult(Factsheet factsheet, DeferredCallback deferredCallback,
                                       Action sourceAction) {
        static final InstantActionResult NONE = new InstantActionResult(null, null, null);
        static InstantActionResult withCallback(DeferredCallback cb) {
            return new InstantActionResult(null, cb, null);
        }
        static InstantActionResult withFactsheetRequest(Action action) {
            return new InstantActionResult(null, DeferredCallback.FACTSHEET, action);
        }
    }

    /**
     * 判断当前状态下是否可以接受新订单。
     * IDLE -> 无条件接受；PAUSED -> 拒绝；RUNNING -> 同 orderId 的更新或当前订单已完成。
     */
    private boolean canAcceptOrder(VehicleContext ctx, Order order) {
        ProxyClientState state = ctx.getClientState();
        // IDLE 状态：无条件接受任何新订单
        if (state == ProxyClientState.IDLE) {
            return true;
        }
        // PAUSED 状态：不允许接受新订单，必须先恢复
        if (state == ProxyClientState.PAUSED) {
            return false;
        }

        // RUNNING 状态：允许同 orderId 的订单更新（追加 horizon），或当前订单已全部完成
        Order current = ctx.getCurrentOrder();
        if (current != null && current.getOrderId().equals(order.getOrderId())) {
            return true;
        }
        return isCurrentOrderCompleted(ctx);
    }

    private boolean isCurrentOrderCompleted(VehicleContext ctx) {
        Order current = ctx.getCurrentOrder();
        if (current == null) {
            return true;
        }
        AgvState agvState = ctx.getAgvState();
        return agvState.getNodeStates().isEmpty() && !agvState.isDriving();
    }

    private void initAgvState(VehicleContext ctx, Order order) {
        AgvState agvState = ctx.getAgvState();
        agvState.setOrderId(order.getOrderId());
        agvState.setOrderUpdateId(order.getOrderUpdateId());

        // Initialize node states
        List<NodeState> nodeStates = new ArrayList<>();
        for (Node node : order.getNodes()) {
            NodeState ns = new NodeState();
            ns.setNodeId(node.getNodeId());
            ns.setSequenceId(node.getSequenceId());
            ns.setNodeDescription(node.getNodeDescription());
            ns.setPosition(node.getNodePosition());
            ns.setReleased(node.isReleased());
            nodeStates.add(ns);
        }
        agvState.setNodeStates(nodeStates);

        // Initialize edge states
        List<EdgeState> edgeStates = new ArrayList<>();
        for (Edge edge : order.getEdges()) {
            EdgeState es = new EdgeState();
            es.setEdgeId(edge.getEdgeId());
            es.setSequenceId(edge.getSequenceId());
            es.setEdgeDescription(edge.getEdgeDescription());
            es.setReleased(edge.isReleased());
            es.setTrajectory(edge.getTrajectory());
            edgeStates.add(es);
        }
        agvState.setEdgeStates(edgeStates);

        // Initialize action states (all WAITING) — node actions first, then edge actions
        List<ActionState> actionStates = new ArrayList<>();
        for (Node node : order.getNodes()) {
            for (Action action : node.getActions()) {
                ActionState as = new ActionState();
                as.setActionId(action.getActionId());
                as.setActionType(action.getActionType());
                as.setActionDescription(action.getActionDescription());
                as.setActionStatus(ActionStatus.WAITING.getValue());
                actionStates.add(as);
            }
        }
        for (Edge edge : order.getEdges()) {
            for (Action action : edge.getActions()) {
                ActionState as = new ActionState();
                as.setActionId(action.getActionId());
                as.setActionType(action.getActionType());
                as.setActionDescription(action.getActionDescription());
                as.setActionStatus(ActionStatus.WAITING.getValue());
                actionStates.add(as);
            }
        }
        agvState.setActionStates(actionStates);

        agvState.setDriving(false);
        agvState.setPaused(false);
        errorAggregator.clearAllErrors(ctx);
    }

    /**
     * 处理单个即时动作：内置动作（cancelOrder 等）直接处理，其他加入 actionStates 由执行器异步处理。
     *
     * @return 处理结果，包含需要在锁外执行的 adapter 回调和/或待发布的 Factsheet
     */
    private InstantActionResult processInstantAction(VehicleContext ctx, Action action) {
        // 检查重复的 actionId，避免重复处理同一动作
        for (ActionState as : ctx.getAgvState().getActionStates()) {
            if (as.getActionId().equals(action.getActionId())) {
                return InstantActionResult.NONE;
            }
        }

        String actionType = action.getActionType();
        // 根据 actionType 分发处理：内置动作由状态机直接处理，自定义动作加入等待队列
        switch (actionType) {
            case "cancelOrder" -> { return handleCancelOrder(ctx, action); }
            case "startPause", "stopPause" -> { return handlePause(ctx, action, actionType); }
            case "factsheetRequest" -> {
                // Defer stateProvider.getFactsheet() to outside lock (user callback)
                return InstantActionResult.withFactsheetRequest(action);
            }
            default -> {
                // 非内置动作：添加到 actionStates 队列，由 ProxyOrderExecutor 在执行循环中处理
                ActionState as = new ActionState();
                as.setActionId(action.getActionId());
                as.setActionType(action.getActionType());
                as.setActionDescription(action.getActionDescription());
                as.setActionStatus(ActionStatus.WAITING.getValue());
                ctx.getAgvState().getActionStates().add(as);
            }
        }
        return InstantActionResult.NONE;
    }

    private InstantActionResult handleCancelOrder(VehicleContext ctx, Action action) {
        if (ctx.getClientState() == ProxyClientState.IDLE) {
            errorAggregator.addWarning(ctx, "No order to cancel", "noOrderToCancel", null);
            addInstantActionState(ctx, action, ActionStatus.FAILED, "No active order");
            return InstantActionResult.NONE;
        }

        // Record cancelled orderId to prevent async callbacks from overwriting state
        Order currentOrder = ctx.getCurrentOrder();
        if (currentOrder != null) {
            ctx.addCancelledOrderId(currentOrder.getOrderId());
        }

        ctx.setClientState(ProxyClientState.IDLE);
        ctx.setCurrentOrder(null);
        ctx.clearActionStartTimes();
        ctx.setNavigationStartTime(0);
        addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
        log.info("Vehicle {} order cancelled", ctx.getVehicleId());
        // Adapter callback deferred to execute outside lock
        return InstantActionResult.withCallback(DeferredCallback.CANCEL);
    }

    private InstantActionResult handlePause(VehicleContext ctx, Action action, String actionType) {
        if ("startPause".equals(actionType)) {
            if (ctx.getClientState() == ProxyClientState.RUNNING) {
                ctx.setClientState(ProxyClientState.PAUSED);
                ctx.getAgvState().setPaused(true);
                addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
                return InstantActionResult.withCallback(DeferredCallback.PAUSE);
            } else {
                addInstantActionState(ctx, action, ActionStatus.FAILED, "Not in RUNNING state");
            }
        } else { // stopPause
            if (ctx.getClientState() == ProxyClientState.PAUSED) {
                ctx.setClientState(ProxyClientState.RUNNING);
                ctx.getAgvState().setPaused(false);
                addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
                return InstantActionResult.withCallback(DeferredCallback.RESUME);
            } else {
                addInstantActionState(ctx, action, ActionStatus.FAILED, "Not in PAUSED state");
            }
        }
        return InstantActionResult.NONE;
    }

    /**
     * 处理 factsheetRequest：调用 stateProvider 和 MQTT publish 均在锁外执行。
     * 仅在设置 actionState 时短暂获取锁。
     *
     * @param ctx          车辆上下文
     * @param action       factsheetRequest 动作
     * @param manufacturer 制造商（锁外传入，避免重新获取锁）
     * @param serialNumber 序列号（锁外传入）
     */
    private void handleFactsheetRequest(VehicleContext ctx, Action action,
                                        String manufacturer, String serialNumber) {
        try {
            // stateProvider.getFactsheet() 是用户回调，在锁外执行
            Factsheet factsheet = stateProvider.getFactsheet(ctx.getVehicleId());
            if (factsheet != null) {
                // nextStateHeaderId() 基于 AtomicInteger，无需持锁
                factsheet.setHeaderId(ctx.nextStateHeaderId());
                factsheet.setManufacturer(manufacturer);
                factsheet.setSerialNumber(serialNumber);
            }
            // 短暂获取锁设置 actionState
            ctx.lock();
            try {
                addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
            } finally {
                ctx.unlock();
            }
            // MQTT publish 在锁外
            if (factsheet != null) {
                mqttGateway.publishFactsheet(manufacturer, serialNumber, factsheet);
            }
        } catch (Exception e) {
            ctx.lock();
            try {
                addInstantActionState(ctx, action, ActionStatus.FAILED, e.getMessage());
            } finally {
                ctx.unlock();
            }
        }
    }

    private void addInstantActionState(VehicleContext ctx, Action action,
                                       ActionStatus status, String resultDescription) {
        ActionState as = new ActionState();
        as.setActionId(action.getActionId());
        as.setActionType(action.getActionType());
        as.setActionDescription(action.getActionDescription());
        as.setActionStatus(status.getValue());
        as.setResultDescription(resultDescription);
        ctx.getAgvState().getActionStates().add(as);
    }
}
