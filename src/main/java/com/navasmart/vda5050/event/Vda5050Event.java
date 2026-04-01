package com.navasmart.vda5050.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * VDA5050 事件基类，所有 VDA5050 相关事件均继承此类。
 */
public abstract class Vda5050Event extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String vehicleId;
    private final Instant eventTimestamp;

    protected Vda5050Event(Object source, String vehicleId) {
        super(source);
        this.vehicleId = vehicleId;
        this.eventTimestamp = Instant.now();
    }

    public String getVehicleId() { return vehicleId; }

    public Instant getEventTimestamp() { return eventTimestamp; }
}
