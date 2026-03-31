package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.Error;

import java.util.List;

/**
 * 订单执行进度的不可变快照对象，封装了订单执行的各项进度指标。
 *
 * <p>包含的信息：
 * <ul>
 *   <li>节点进度：已完成节点数 / 总节点数</li>
 *   <li>动作进度：已完成动作数 / 总动作数</li>
 *   <li>当前状态：是否在行驶中、当前节点 ID</li>
 *   <li>错误信息：当前存在的错误列表</li>
 * </ul>
 *
 * <p>线程安全：此类为不可变对象，天然线程安全。</p>
 *
 * @see OrderProgressTracker
 */
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

    /**
     * 构造订单进度对象。
     *
     * @param vehicleId        车辆标识符
     * @param orderId          订单 ID（空闲时为 null）
     * @param totalNodes       总节点数
     * @param completedNodes   已完成节点数
     * @param totalActions     总动作数
     * @param completedActions 已完成动作数
     * @param driving          是否正在行驶
     * @param currentNodeId    当前（最后到达的）节点 ID
     * @param errors           当前错误列表
     */
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

    /**
     * 创建空闲状态的进度对象，表示车辆没有活动订单。
     *
     * @param vehicleId 车辆标识符
     * @return 空闲状态的进度对象（orderId 为 null，所有计数为 0）
     */
    public static OrderProgress idle(String vehicleId) {
        return new OrderProgress(vehicleId, null, 0, 0, 0, 0, false, null, List.of());
    }

    /**
     * 计算订单完成百分比（基于节点进度）。
     *
     * <p>空闲时（totalNodes=0）返回 100.0。</p>
     *
     * @return 完成百分比，范围 0.0 - 100.0
     */
    public double getCompletionPercent() {
        if (totalNodes == 0) {
            return 100.0;
        }
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
