package com.navasmart.vda5050.proxy.callback;

/**
 * 导航操作的结果封装类，用于表示 {@link Vda5050ProxyVehicleAdapter#onNavigate} 的异步返回值。
 *
 * <p>通过静态工厂方法创建实例：
 * <ul>
 *   <li>{@link #success()} - 导航成功到达目标节点</li>
 *   <li>{@link #success(String)} - 导航成功，并指定实际到达的节点 ID</li>
 *   <li>{@link #failure(String)} - 导航失败，附带失败原因</li>
 * </ul>
 * </p>
 *
 * <p>导航失败时，框架将记录 FATAL 级别错误并中止当前订单执行。</p>
 *
 * <p>线程安全：此类为不可变对象，天然线程安全。</p>
 */
public class NavigationResult {

    private final boolean success;
    private final String failureReason;
    private final String reachedNodeId;

    private NavigationResult(boolean success, String failureReason, String reachedNodeId) {
        this.success = success;
        this.failureReason = failureReason;
        this.reachedNodeId = reachedNodeId;
    }

    /**
     * 创建导航成功的结果。
     *
     * @return 表示导航成功的结果实例
     */
    public static NavigationResult success() {
        return new NavigationResult(true, null, null);
    }

    /**
     * 创建导航成功的结果，并指定实际到达的节点 ID。
     *
     * @param reachedNodeId 实际到达的节点 ID
     * @return 表示导航成功的结果实例
     */
    public static NavigationResult success(String reachedNodeId) {
        return new NavigationResult(true, null, reachedNodeId);
    }

    /**
     * 创建导航失败的结果。
     *
     * <p>失败原因将被记录到 VDA5050 错误列表中，错误类型为 {@code navigationError}。</p>
     *
     * @param reason 导航失败的原因描述
     * @return 表示导航失败的结果实例
     */
    public static NavigationResult failure(String reason) {
        return new NavigationResult(false, reason, null);
    }

    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
    public String getReachedNodeId() { return reachedNodeId; }
}
