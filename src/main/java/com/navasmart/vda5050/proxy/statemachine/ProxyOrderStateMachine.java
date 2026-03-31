package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ProxyOrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ProxyOrderStateMachine.class);

    private final ErrorAggregator errorAggregator;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;
    private final Vda5050ProxyStateProvider stateProvider;
    private final MqttGateway mqttGateway;

    public ProxyOrderStateMachine(ErrorAggregator errorAggregator,
                                  Vda5050ProxyVehicleAdapter vehicleAdapter,
                                  Vda5050ProxyStateProvider stateProvider,
                                  MqttGateway mqttGateway) {
        this.errorAggregator = errorAggregator;
        this.vehicleAdapter = vehicleAdapter;
        this.stateProvider = stateProvider;
        this.mqttGateway = mqttGateway;
    }

    public void receiveOrder(VehicleContext ctx, Order order) {
        ctx.lock();
        try {
            if (!canAcceptOrder(ctx, order)) {
                errorAggregator.addWarning(ctx, "Cannot accept order in current state",
                        "orderUpdateError", Map.of("orderId", order.getOrderId()));
                return;
            }

            log.info("Vehicle {} accepting order: {}", ctx.getVehicleId(), order.getOrderId());

            ctx.setCurrentOrder(order);
            ctx.setCurrentNodeIndex(0);
            ctx.setNextStopIndex(0);
            ctx.setReachedWaypoint(true);
            initAgvState(ctx, order);
            ctx.setClientState(ProxyClientState.RUNNING);
        } finally {
            ctx.unlock();
        }
    }

    public void receiveInstantActions(VehicleContext ctx, InstantActions instantActions) {
        ctx.lock();
        try {
            for (Action action : instantActions.getInstantActions()) {
                processInstantAction(ctx, action);
            }
        } finally {
            ctx.unlock();
        }
    }

    private boolean canAcceptOrder(VehicleContext ctx, Order order) {
        ProxyClientState state = ctx.getClientState();
        if (state == ProxyClientState.IDLE) return true;
        if (state == ProxyClientState.PAUSED) return false;

        // RUNNING: accept same orderId (order update) or if current order completed
        Order current = ctx.getCurrentOrder();
        if (current != null && current.getOrderId().equals(order.getOrderId())) {
            return true;
        }
        return isCurrentOrderCompleted(ctx);
    }

    private boolean isCurrentOrderCompleted(VehicleContext ctx) {
        Order current = ctx.getCurrentOrder();
        if (current == null) return true;
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

        // Initialize action states (all WAITING)
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
        agvState.setActionStates(actionStates);

        agvState.setDriving(false);
        agvState.setPaused(false);
        errorAggregator.clearAllErrors(ctx);
    }

    private void processInstantAction(VehicleContext ctx, Action action) {
        // Check for duplicate action IDs
        for (ActionState as : ctx.getAgvState().getActionStates()) {
            if (as.getActionId().equals(action.getActionId())) {
                return; // Already exists
            }
        }

        String actionType = action.getActionType();
        switch (actionType) {
            case "cancelOrder" -> handleCancelOrder(ctx, action);
            case "startPause", "stopPause" -> handlePause(ctx, action, actionType);
            case "factsheetRequest" -> handleFactsheetRequest(ctx, action);
            default -> {
                // Add to action states as instant action
                ActionState as = new ActionState();
                as.setActionId(action.getActionId());
                as.setActionType(action.getActionType());
                as.setActionDescription(action.getActionDescription());
                as.setActionStatus(ActionStatus.WAITING.getValue());
                ctx.getAgvState().getActionStates().add(as);
            }
        }
    }

    private void handleCancelOrder(VehicleContext ctx, Action action) {
        if (ctx.getClientState() == ProxyClientState.IDLE) {
            errorAggregator.addWarning(ctx, "No order to cancel", "noOrderToCancel", null);
            addInstantActionState(ctx, action, ActionStatus.FAILED, "No active order");
            return;
        }

        vehicleAdapter.onOrderCancel(ctx.getVehicleId());
        ctx.setClientState(ProxyClientState.IDLE);
        ctx.setCurrentOrder(null);
        addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
        log.info("Vehicle {} order cancelled", ctx.getVehicleId());
    }

    private void handlePause(VehicleContext ctx, Action action, String actionType) {
        if ("startPause".equals(actionType)) {
            if (ctx.getClientState() == ProxyClientState.RUNNING) {
                ctx.setClientState(ProxyClientState.PAUSED);
                ctx.getAgvState().setPaused(true);
                vehicleAdapter.onPause(ctx.getVehicleId());
                addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
            } else {
                addInstantActionState(ctx, action, ActionStatus.FAILED, "Not in RUNNING state");
            }
        } else { // stopPause
            if (ctx.getClientState() == ProxyClientState.PAUSED) {
                ctx.setClientState(ProxyClientState.RUNNING);
                ctx.getAgvState().setPaused(false);
                vehicleAdapter.onResume(ctx.getVehicleId());
                addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
            } else {
                addInstantActionState(ctx, action, ActionStatus.FAILED, "Not in PAUSED state");
            }
        }
    }

    private void handleFactsheetRequest(VehicleContext ctx, Action action) {
        try {
            Factsheet factsheet = stateProvider.getFactsheet(ctx.getVehicleId());
            if (factsheet != null) {
                factsheet.setHeaderId(ctx.nextStateHeaderId());
                factsheet.setManufacturer(ctx.getManufacturer());
                factsheet.setSerialNumber(ctx.getSerialNumber());
                mqttGateway.publishFactsheet(ctx.getManufacturer(), ctx.getSerialNumber(), factsheet);
            }
            addInstantActionState(ctx, action, ActionStatus.FINISHED, null);
        } catch (Exception e) {
            addInstantActionState(ctx, action, ActionStatus.FAILED, e.getMessage());
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
