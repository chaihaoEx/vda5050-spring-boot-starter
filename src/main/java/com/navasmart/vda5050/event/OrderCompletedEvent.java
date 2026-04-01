package com.navasmart.vda5050.event;

/**
 * 订单成功完成时发布的事件。
 */
public class OrderCompletedEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String orderId;

    public OrderCompletedEvent(Object source, String vehicleId, String orderId) {
        super(source, vehicleId);
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
}
