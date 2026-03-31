package com.navasmart.vda5050.proxy.statemachine;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.*;
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

@Component
public class ProxyOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProxyOrderExecutor.class);

    private final VehicleRegistry vehicleRegistry;
    private final ErrorAggregator errorAggregator;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final Vda5050ProxyVehicleAdapter vehicleAdapter;

    public ProxyOrderExecutor(VehicleRegistry vehicleRegistry,
                              ErrorAggregator errorAggregator,
                              ActionHandlerRegistry actionHandlerRegistry,
                              Vda5050ProxyVehicleAdapter vehicleAdapter) {
        this.vehicleRegistry = vehicleRegistry;
        this.errorAggregator = errorAggregator;
        this.actionHandlerRegistry = actionHandlerRegistry;
        this.vehicleAdapter = vehicleAdapter;
    }

    @Scheduled(fixedDelayString = "${vda5050.proxy.orderLoopIntervalMs:200}")
    public void execute() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            executeForVehicle(ctx);
        }
    }

    private void executeForVehicle(VehicleContext ctx) {
        ctx.lock();
        try {
            if (ctx.getClientState() != ProxyClientState.RUNNING) return;

            Order order = ctx.getCurrentOrder();
            if (order == null) return;

            // 1. Check FATAL errors
            if (errorAggregator.hasFatalError(ctx)) {
                handleFatalError(ctx);
                return;
            }

            // 2. Process current node if waypoint reached
            if (ctx.isReachedWaypoint()) {
                int nodeIndex = ctx.getCurrentNodeIndex();
                List<Node> nodes = order.getNodes();

                if (nodeIndex >= nodes.size()) {
                    // Order complete
                    log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
                    ctx.setClientState(ProxyClientState.IDLE);
                    ctx.getAgvState().setDriving(false);
                    return;
                }

                Node currentNode = nodes.get(nodeIndex);
                NodeProcessResult result = processNode(ctx, currentNode);

                if (result == NodeProcessResult.ALL_ACTIONS_DONE) {
                    advanceToNextNode(ctx);
                }
            }
        } finally {
            ctx.unlock();
        }
    }

    private NodeProcessResult processNode(VehicleContext ctx, Node node) {
        boolean stopDriving = false;
        boolean hasRunningHard = false;

        for (Action action : node.getActions()) {
            ActionState actionState = findActionState(ctx, action.getActionId());
            if (actionState == null) continue;

            String status = actionState.getActionStatus();
            String blocking = action.getBlockingType();
            if (blocking == null || blocking.isEmpty()) blocking = BlockingType.HARD.getValue();

            // Check running actions
            if (ActionStatus.RUNNING.getValue().equals(status)) {
                if (BlockingType.HARD.getValue().equals(blocking)) {
                    hasRunningHard = true;
                    return NodeProcessResult.WAITING; // Wait for HARD action to complete
                }
                if (BlockingType.SOFT.getValue().equals(blocking)) {
                    stopDriving = true;
                }
                continue;
            }

            // Start waiting actions
            if (ActionStatus.WAITING.getValue().equals(status)) {
                if (BlockingType.HARD.getValue().equals(blocking) && !hasRunningHard) {
                    startAction(ctx, action, actionState);
                    return NodeProcessResult.WAITING; // Wait for this HARD action
                }
                if (BlockingType.SOFT.getValue().equals(blocking)
                        || BlockingType.NONE.getValue().equals(blocking)) {
                    startAction(ctx, action, actionState);
                    if (BlockingType.SOFT.getValue().equals(blocking)) {
                        stopDriving = true;
                    }
                }
            }
        }

        if (stopDriving) {
            ctx.getAgvState().setDriving(false);
            return NodeProcessResult.WAITING;
        }

        // Check if all actions are terminal
        boolean allDone = node.getActions().stream()
                .allMatch(a -> {
                    ActionState as = findActionState(ctx, a.getActionId());
                    if (as == null) return true;
                    String s = as.getActionStatus();
                    return ActionStatus.FINISHED.getValue().equals(s)
                            || ActionStatus.FAILED.getValue().equals(s);
                });

        return allDone ? NodeProcessResult.ALL_ACTIONS_DONE : NodeProcessResult.WAITING;
    }

    private void startAction(VehicleContext ctx, Action action, ActionState actionState) {
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());
        log.debug("Vehicle {} starting action {} ({})", ctx.getVehicleId(),
                action.getActionId(), action.getActionType());

        // Try registered handler first
        Optional<ActionHandler> handler = actionHandlerRegistry.getHandler(action.getActionType());
        CompletableFuture<ActionResult> future;

        if (handler.isPresent()) {
            future = handler.get().execute(ctx.getVehicleId(), action);
        } else {
            // Fallback to vehicle adapter
            future = vehicleAdapter.onActionExecute(ctx.getVehicleId(), action);
        }

        String actionId = action.getActionId();
        String vehicleId = ctx.getVehicleId();
        future.whenComplete((result, ex) -> {
            ctx.lock();
            try {
                ActionState as = findActionState(ctx, actionId);
                if (as == null) return;

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

    private void advanceToNextNode(VehicleContext ctx) {
        Order order = ctx.getCurrentOrder();
        int nextIndex = ctx.getCurrentNodeIndex() + 1;

        // Remove processed node from nodeStates
        if (!ctx.getAgvState().getNodeStates().isEmpty()) {
            ctx.getAgvState().getNodeStates().remove(0);
        }

        // Update last node
        Node currentNode = order.getNodes().get(ctx.getCurrentNodeIndex());
        ctx.getAgvState().setLastNodeId(currentNode.getNodeId());
        ctx.getAgvState().setLastNodeSequenceId(currentNode.getSequenceId());

        // Remove edge state if exists
        if (!ctx.getAgvState().getEdgeStates().isEmpty()) {
            ctx.getAgvState().getEdgeStates().remove(0);
        }

        ctx.setCurrentNodeIndex(nextIndex);

        if (nextIndex >= order.getNodes().size()) {
            // Order complete
            ctx.setClientState(ProxyClientState.IDLE);
            ctx.getAgvState().setDriving(false);
            log.info("Vehicle {} order {} completed", ctx.getVehicleId(), order.getOrderId());
            return;
        }

        // Navigate to next node
        ctx.setReachedWaypoint(false);
        ctx.getAgvState().setDriving(true);

        Node targetNode = order.getNodes().get(nextIndex);
        List<Node> waypoints = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // Collect waypoints up to next released node
        for (int i = nextIndex; i < order.getNodes().size(); i++) {
            Node n = order.getNodes().get(i);
            waypoints.add(n);
            if (n.isReleased()) break;
        }
        for (int i = ctx.getCurrentNodeIndex(); i < order.getEdges().size(); i++) {
            edges.add(order.getEdges().get(i));
        }

        String vehicleId = ctx.getVehicleId();
        CompletableFuture<NavigationResult> navFuture =
                vehicleAdapter.onNavigate(vehicleId, targetNode, waypoints, edges);

        navFuture.whenComplete((result, ex) -> {
            ctx.lock();
            try {
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
    }

    private ActionState findActionState(VehicleContext ctx, String actionId) {
        return ctx.getAgvState().getActionStates().stream()
                .filter(as -> actionId.equals(as.getActionId()))
                .findFirst()
                .orElse(null);
    }

    enum NodeProcessResult {
        WAITING,
        ALL_ACTIONS_DONE
    }
}
