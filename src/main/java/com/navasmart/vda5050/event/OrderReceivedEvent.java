package com.navasmart.vda5050.event;

/**
 * 收到新订单时发布的事件。
 */
public class OrderReceivedEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final long orderUpdateId;

    public OrderReceivedEvent(Object source, String vehicleId, String orderId, long orderUpdateId) {
        super(source, vehicleId);
        this.orderId = orderId;
        this.orderUpdateId = orderUpdateId;
    }

    public String getOrderId() { return orderId; }

    public long getOrderUpdateId() { return orderUpdateId; }
}
