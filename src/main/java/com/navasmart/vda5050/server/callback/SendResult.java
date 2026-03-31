package com.navasmart.vda5050.server.callback;

/**
 * 消息发送操作的结果封装类，用于表示订单下发或即时动作发送的结果。
 *
 * <p>通过静态工厂方法创建实例：
 * <ul>
 *   <li>{@link #success()} - 发送成功</li>
 *   <li>{@link #failure(String)} - 发送失败，附带原因（如车辆未注册、订单 ID 不匹配等）</li>
 * </ul>
 *
 * <p>线程安全：此类为不可变对象，天然线程安全。</p>
 *
 * @see com.navasmart.vda5050.server.dispatch.OrderDispatcher
 * @see com.navasmart.vda5050.server.dispatch.InstantActionSender
 */
public class SendResult {

    private final boolean success;
    private final String failureReason;

    private SendResult(boolean success, String failureReason) {
        this.success = success;
        this.failureReason = failureReason;
    }

    /**
     * 创建发送成功的结果。
     *
     * @return 表示发送成功的结果实例
     */
    public static SendResult success() {
        return new SendResult(true, null);
    }

    /**
     * 创建发送失败的结果。
     *
     * @param reason 失败原因描述
     * @return 表示发送失败的结果实例
     */
    public static SendResult failure(String reason) {
        return new SendResult(false, reason);
    }

    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}
