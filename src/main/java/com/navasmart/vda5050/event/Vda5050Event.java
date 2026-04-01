package com.navasmart.vda5050.event;

import org.springframework.context.ApplicationEvent;

/**
 * VDA5050 事件基类，所有 VDA5050 相关事件均继承此类。
 *
 * <p>事件时间戳可通过继承自 {@link ApplicationEvent#getTimestamp()} 获取。</p>
 */
public abstract class Vda5050Event extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String vehicleId;

    protected Vda5050Event(Object source, String vehicleId) {
        super(source);
        this.vehicleId = vehicleId;
    }

    public String getVehicleId() { return vehicleId; }
}
