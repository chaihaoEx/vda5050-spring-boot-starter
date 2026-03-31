package com.navasmart.vda5050.proxy.callback;

public class ActionResult {

    private final boolean success;
    private final String resultDescription;
    private final String failureReason;

    private ActionResult(boolean success, String resultDescription, String failureReason) {
        this.success = success;
        this.resultDescription = resultDescription;
        this.failureReason = failureReason;
    }

    public static ActionResult success() {
        return new ActionResult(true, null, null);
    }

    public static ActionResult success(String resultDescription) {
        return new ActionResult(true, resultDescription, null);
    }

    public static ActionResult failure(String reason) {
        return new ActionResult(false, null, reason);
    }

    public boolean isSuccess() { return success; }
    public String getResultDescription() { return resultDescription; }
    public String getFailureReason() { return failureReason; }
}
