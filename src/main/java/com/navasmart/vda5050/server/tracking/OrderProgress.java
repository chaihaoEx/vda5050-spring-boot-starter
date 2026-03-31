package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.Error;

import java.util.List;

public class OrderProgress {

    private final String vehicleId;
    private final String orderId;
    private final int totalNodes;
    private final int completedNodes;
    private final long totalActions;
    private final long completedActions;
    private final boolean driving;
    private final String currentNodeId;
    private final List<Error> errors;

    public OrderProgress(String vehicleId, String orderId, int totalNodes, int completedNodes,
                         long totalActions, long completedActions, boolean driving,
                         String currentNodeId, List<Error> errors) {
        this.vehicleId = vehicleId;
        this.orderId = orderId;
        this.totalNodes = totalNodes;
        this.completedNodes = completedNodes;
        this.totalActions = totalActions;
        this.completedActions = completedActions;
        this.driving = driving;
        this.currentNodeId = currentNodeId;
        this.errors = errors;
    }

    public static OrderProgress idle(String vehicleId) {
        return new OrderProgress(vehicleId, null, 0, 0, 0, 0, false, null, List.of());
    }

    public double getCompletionPercent() {
        if (totalNodes == 0) return 100.0;
        return (double) completedNodes / totalNodes * 100.0;
    }

    public String getVehicleId() { return vehicleId; }
    public String getOrderId() { return orderId; }
    public int getTotalNodes() { return totalNodes; }
    public int getCompletedNodes() { return completedNodes; }
    public long getTotalActions() { return totalActions; }
    public long getCompletedActions() { return completedActions; }
    public boolean isDriving() { return driving; }
    public String getCurrentNodeId() { return currentNodeId; }
    public List<Error> getErrors() { return errors; }
}
