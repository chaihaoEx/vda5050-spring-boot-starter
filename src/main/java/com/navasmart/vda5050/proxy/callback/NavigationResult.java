package com.navasmart.vda5050.proxy.callback;

public class NavigationResult {

    private final boolean success;
    private final String failureReason;
    private final String reachedNodeId;

    private NavigationResult(boolean success, String failureReason, String reachedNodeId) {
        this.success = success;
        this.failureReason = failureReason;
        this.reachedNodeId = reachedNodeId;
    }

    public static NavigationResult success() {
        return new NavigationResult(true, null, null);
    }

    public static NavigationResult success(String reachedNodeId) {
        return new NavigationResult(true, null, reachedNodeId);
    }

    public static NavigationResult failure(String reason) {
        return new NavigationResult(false, reason, null);
    }

    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
    public String getReachedNodeId() { return reachedNodeId; }
}
