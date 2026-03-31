package com.navasmart.vda5050.server.callback;

public class SendResult {

    private final boolean success;
    private final String failureReason;

    private SendResult(boolean success, String failureReason) {
        this.success = success;
        this.failureReason = failureReason;
    }

    public static SendResult success() {
        return new SendResult(true, null);
    }

    public static SendResult failure(String reason) {
        return new SendResult(false, reason);
    }

    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}
