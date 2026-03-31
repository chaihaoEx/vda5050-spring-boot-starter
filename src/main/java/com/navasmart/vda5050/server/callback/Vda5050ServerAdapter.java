package com.navasmart.vda5050.server.callback;

import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.Factsheet;

import java.util.List;

/**
 * VDA5050 Server 模式下的回调适配器接口，用于接收来自 AGV 的状态变更通知。
 *
 * <p>用户需实现此接口并注册为 Spring Bean。框架通过 {@link com.navasmart.vda5050.server.tracking.AgvStateTracker}
 * 解析收到的 State 消息，检测各种变更，并调用对应的回调方法通知用户。</p>
 *
 * <p>所有回调方法（除 {@link #onStateUpdate}）均有默认空实现，用户可按需覆盖关注的事件。</p>
 *
 * <p>线程安全：回调方法在消息处理线程中被调用，同一辆车的回调通过 VehicleContext 锁串行化。</p>
 *
 * @see com.navasmart.vda5050.server.tracking.AgvStateTracker
 */
public interface Vda5050ServerAdapter {

    /**
     * 每次收到 AGV 的 State 消息时被调用。
     *
     * <p>触发条件：每收到一条 State MQTT 消息即触发，调用频率取决于 AGV 的心跳间隔。
     * 这是最基础的回调，即使没有任何状态变更也会被调用。</p>
     *
     * @param vehicleId 车辆标识符
     * @param state     收到的完整 AgvState 对象
     */
    void onStateUpdate(String vehicleId, AgvState state);

    /**
     * 当检测到 AGV 到达新节点时被调用。
     *
     * <p>触发条件：State 消息中的 lastNodeId 与前一次不同时触发。</p>
     *
     * @param vehicleId  车辆标识符
     * @param nodeId     到达的节点 ID
     * @param sequenceId 到达的节点序列号
     */
    default void onNodeReached(String vehicleId, String nodeId, int sequenceId) {}

    /**
     * 当检测到某个动作的状态发生变化时被调用。
     *
     * <p>触发条件：对比前后两次 State 消息中的 ActionState，
     * 当某个 actionId 的 actionStatus 值变化时触发。</p>
     *
     * @param vehicleId   车辆标识符
     * @param actionState 状态发生变化的 ActionState 对象（包含最新状态）
     */
    default void onActionStateChanged(String vehicleId, ActionState actionState) {}

    /**
     * 当检测到已发送的订单成功完成时被调用。
     *
     * <p>触发条件：nodeStates 为空、driving 为 false、所有动作处于终态（FINISHED/FAILED），
     * 且没有 FAILED 动作或 FATAL 错误时触发。触发后清除 lastSentOrder。</p>
     *
     * @param vehicleId 车辆标识符
     * @param orderId   完成的订单 ID
     */
    default void onOrderCompleted(String vehicleId, String orderId) {}

    /**
     * 当检测到已发送的订单执行失败时被调用。
     *
     * <p>触发条件：与 {@link #onOrderCompleted} 相同的完成检测条件，
     * 但存在 FAILED 动作或 FATAL 级别错误时触发。</p>
     *
     * @param vehicleId 车辆标识符
     * @param orderId   失败的订单 ID
     * @param errors    当前的错误列表
     */
    default void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {}

    /**
     * 当检测到 AGV 的连接状态发生变化时被调用。
     *
     * <p>触发条件：收到 Connection 消息且 connectionState 与之前记录的不同时触发。</p>
     *
     * @param vehicleId       车辆标识符
     * @param connectionState 新的连接状态（ONLINE/OFFLINE/CONNECTIONBROKEN）
     */
    default void onConnectionStateChanged(String vehicleId, String connectionState) {}

    /**
     * 当 AGV 超过配置的超时时间未发送 State 消息时被调用。
     *
     * <p>触发条件：由 {@link com.navasmart.vda5050.server.heartbeat.ServerConnectionMonitor}
     * 定期检查（默认 30s），当距上次收到 State 消息超过
     * {@code vda5050.server.stateTimeoutMs} 配置的阈值时触发。</p>
     *
     * @param vehicleId         车辆标识符
     * @param lastSeenTimestamp  最后一次收到消息的 ISO 8601 格式时间戳
     */
    default void onVehicleTimeout(String vehicleId, String lastSeenTimestamp) {}

    /**
     * 当收到 AGV 发布的 Factsheet 消息时被调用。
     *
     * <p>触发条件：收到 Factsheet MQTT 消息时触发，通常是对 factsheetRequest 即时动作的响应。</p>
     *
     * @param vehicleId 车辆标识符
     * @param factsheet 收到的 Factsheet 对象
     */
    default void onFactsheetReceived(String vehicleId, Factsheet factsheet) {}

    /**
     * 当检测到 AGV 报告了新错误时被调用。
     *
     * <p>触发条件：对比前后两次 State 消息的 errors 列表，
     * 出现了之前不存在的 errorType:errorDescription 组合时触发。</p>
     *
     * @param vehicleId 车辆标识符
     * @param error     新报告的错误
     */
    default void onErrorReported(String vehicleId, Error error) {}

    /**
     * 当检测到 AGV 之前报告的错误被清除时被调用。
     *
     * <p>触发条件：对比前后两次 State 消息的 errors 列表，
     * 之前存在的 errorType:errorDescription 组合在新消息中消失时触发。</p>
     *
     * @param vehicleId 车辆标识符
     * @param error     被清除的错误
     */
    default void onErrorCleared(String vehicleId, Error error) {}
}
