package com.navasmart.vda5050.proxy.callback;

/**
 * 动作（Action）执行结果的封装类，用于表示
 * {@link Vda5050ProxyVehicleAdapter#onActionExecute} 或
 * {@link com.navasmart.vda5050.proxy.action.ActionHandler#execute} 的异步返回值。
 *
 * <p>通过静态工厂方法创建实例：
 * <ul>
 *   <li>{@link #success()} - 动作执行成功</li>
 *   <li>{@link #success(String)} - 动作执行成功，附带结果描述</li>
 *   <li>{@link #failure(String)} - 动作执行失败，附带失败原因</li>
 * </ul>
 *
 * <p>成功时，对应的 ActionState 将被设置为 FINISHED；
 * 失败时，对应的 ActionState 将被设置为 FAILED，失败原因写入 resultDescription。</p>
 *
 * <p>线程安全：此类为不可变对象，天然线程安全。</p>
 */
public class ActionResult {

    private final boolean success;
    private final String resultDescription;
    private final String failureReason;

    private ActionResult(boolean success, String resultDescription, String failureReason) {
        this.success = success;
        this.resultDescription = resultDescription;
        this.failureReason = failureReason;
    }

    /**
     * 创建动作执行成功的结果。
     *
     * @return 表示动作执行成功的结果实例
     */
    public static ActionResult success() {
        return new ActionResult(true, null, null);
    }

    /**
     * 创建动作执行成功的结果，附带结果描述信息。
     *
     * @param resultDescription 动作执行的结果描述，将写入 ActionState.resultDescription
     * @return 表示动作执行成功的结果实例
     */
    public static ActionResult success(String resultDescription) {
        return new ActionResult(true, resultDescription, null);
    }

    /**
     * 创建动作执行失败的结果。
     *
     * @param reason 失败原因描述，将写入 ActionState.resultDescription
     * @return 表示动作执行失败的结果实例
     */
    public static ActionResult failure(String reason) {
        return new ActionResult(false, null, reason);
    }

    public boolean isSuccess() { return success; }
    public String getResultDescription() { return resultDescription; }
    public String getFailureReason() { return failureReason; }
}
