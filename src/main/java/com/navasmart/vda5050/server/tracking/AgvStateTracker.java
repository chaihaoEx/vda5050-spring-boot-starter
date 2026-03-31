package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AgvStateTracker {

    private static final Logger log = LoggerFactory.getLogger(AgvStateTracker.class);

    private final VehicleRegistry vehicleRegistry;
    private final Vda5050ServerAdapter serverAdapter;

    public AgvStateTracker(VehicleRegistry vehicleRegistry, Vda5050ServerAdapter serverAdapter) {
        this.vehicleRegistry = vehicleRegistry;
        this.serverAdapter = serverAdapter;
    }

    public void processState(String vehicleId, AgvState newState) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) return;

        ctx.lock();
        try {
            AgvState prevState = ctx.getLastReceivedState();
            ctx.setLastReceivedState(newState);
            ctx.setLastSeenTimestamp(System.currentTimeMillis());

            // Always notify state update
            serverAdapter.onStateUpdate(vehicleId, newState);

            // Detect node reached
            if (prevState == null || !Objects.equals(prevState.getLastNodeId(), newState.getLastNodeId())) {
                if (newState.getLastNodeId() != null) {
                    serverAdapter.onNodeReached(vehicleId, newState.getLastNodeId(),
                            newState.getLastNodeSequenceId());
                }
            }

            // Detect action state changes
            detectActionStateChanges(vehicleId, prevState, newState);

            // Detect order completion
            detectOrderCompletion(vehicleId, ctx, newState);

            // Detect error changes
            detectErrorChanges(vehicleId, prevState, newState);
        } finally {
            ctx.unlock();
        }
    }

    private void detectActionStateChanges(String vehicleId, AgvState prev, AgvState curr) {
        if (prev == null) return;

        Map<String, String> prevStatuses = prev.getActionStates().stream()
                .collect(Collectors.toMap(ActionState::getActionId, ActionState::getActionStatus,
                        (a, b) -> b));

        for (ActionState as : curr.getActionStates()) {
            String prevStatus = prevStatuses.get(as.getActionId());
            if (prevStatus == null || !prevStatus.equals(as.getActionStatus())) {
                serverAdapter.onActionStateChanged(vehicleId, as);
            }
        }
    }

    private void detectOrderCompletion(String vehicleId, VehicleContext ctx, AgvState state) {
        Order sentOrder = ctx.getLastSentOrder();
        if (sentOrder == null) return;

        if (!sentOrder.getOrderId().equals(state.getOrderId())) return;

        // Order complete when: nodeStates empty, not driving, all actions terminal
        if (!state.getNodeStates().isEmpty()) return;
        if (state.isDriving()) return;
        if (!allActionsTerminal(state.getActionStates())) return;

        boolean hasFailedActions = state.getActionStates().stream()
                .anyMatch(as -> ActionStatus.FAILED.getValue().equals(as.getActionStatus()));
        boolean hasFatalErrors = state.getErrors().stream()
                .anyMatch(e -> ErrorLevel.FATAL.getValue().equals(e.getErrorLevel()));

        if (hasFailedActions || hasFatalErrors) {
            serverAdapter.onOrderFailed(vehicleId, state.getOrderId(), state.getErrors());
        } else {
            serverAdapter.onOrderCompleted(vehicleId, state.getOrderId());
        }

        ctx.setLastSentOrder(null);
        log.info("Vehicle {} order {} completed (failed={})", vehicleId,
                state.getOrderId(), hasFailedActions || hasFatalErrors);
    }

    private void detectErrorChanges(String vehicleId, AgvState prev, AgvState curr) {
        if (prev == null) {
            curr.getErrors().forEach(e -> serverAdapter.onErrorReported(vehicleId, e));
            return;
        }

        Set<String> prevErrorTypes = prev.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());
        Set<String> currErrorTypes = curr.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());

        // New errors
        curr.getErrors().stream()
                .filter(e -> !prevErrorTypes.contains(e.getErrorType() + ":" + e.getErrorDescription()))
                .forEach(e -> serverAdapter.onErrorReported(vehicleId, e));

        // Cleared errors
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
