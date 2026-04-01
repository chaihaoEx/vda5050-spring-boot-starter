package com.navasmart.vda5050.event;

import java.util.List;

/**
 * 订单失败时发布的事件。
 */
public class OrderFailedEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final List<com.navasmart.vda5050.model.Error> errors;

    public OrderFailedEvent(Object source, String vehicleId, String orderId,
                            List<com.navasmart.vda5050.model.Error> errors) {
        super(source, vehicleId);
        this.orderId = orderId;
        this.errors = errors;
    }

    public String getOrderId() { return orderId; }

    public List<com.navasmart.vda5050.model.Error> getErrors() { return errors; }
}
