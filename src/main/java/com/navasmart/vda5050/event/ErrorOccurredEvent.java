package com.navasmart.vda5050.event;

/**
 * 发生错误时发布的事件。
 */
public class ErrorOccurredEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final com.navasmart.vda5050.model.Error error;

    public ErrorOccurredEvent(Object source, String vehicleId, com.navasmart.vda5050.model.Error error) {
        super(source, vehicleId);
        this.error = error;
    }

    public com.navasmart.vda5050.model.Error getError() { return error; }
}
