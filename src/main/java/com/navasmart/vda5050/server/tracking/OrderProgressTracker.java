package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderProgressTracker {

    private final VehicleRegistry vehicleRegistry;

    public OrderProgressTracker(VehicleRegistry vehicleRegistry) {
        this.vehicleRegistry = vehicleRegistry;
    }

    public OrderProgress getProgress(String vehicleId) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) return OrderProgress.idle(vehicleId);

        ctx.lock();
        try {
            AgvState state = ctx.getLastReceivedState();
            Order sentOrder = ctx.getLastSentOrder();

            if (sentOrder == null || state == null) {
                return OrderProgress.idle(vehicleId);
            }

            int totalNodes = sentOrder.getNodes().size();
            int remainingNodes = state.getNodeStates().size();
            int completedNodes = totalNodes - remainingNodes;

            long totalActions = sentOrder.getNodes().stream()
                    .flatMap(n -> n.getActions().stream())
                    .count();
            long completedActions = state.getActionStates().stream()
                    .filter(as -> ActionStatus.FINISHED.getValue().equals(as.getActionStatus()))
                    .count();

            return new OrderProgress(vehicleId, sentOrder.getOrderId(),
                    totalNodes, completedNodes, totalActions, completedActions,
                    state.isDriving(), state.getLastNodeId(), state.getErrors());
        } finally {
            ctx.unlock();
        }
    }
}
