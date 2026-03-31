package com.navasmart.vda5050.server.tracking;

import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.springframework.stereotype.Component;

/**
 * Server 模式下的订单进度追踪器，根据 AGV 上报的 State 消息计算订单执行进度。
 *
 * <p>通过对比已发送的订单（节点/动作总数）和 AGV 上报的状态（剩余节点/已完成动作数），
 * 计算订单的完成百分比和当前位置信息。</p>
 *
 * <p>线程安全：通过 VehicleContext 的锁机制保证线程安全。</p>
 *
 * @see OrderProgress
 */
@Component
public class OrderProgressTracker {

    private final VehicleRegistry vehicleRegistry;

    public OrderProgressTracker(VehicleRegistry vehicleRegistry) {
        this.vehicleRegistry = vehicleRegistry;
    }

    /**
     * 获取指定车辆当前订单的执行进度。
     *
     * <p>如果车辆没有活动订单或未收到状态消息，返回 {@link OrderProgress#idle(String)}。</p>
     *
     * @param vehicleId 车辆标识符
     * @return 当前订单进度信息
     */
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
