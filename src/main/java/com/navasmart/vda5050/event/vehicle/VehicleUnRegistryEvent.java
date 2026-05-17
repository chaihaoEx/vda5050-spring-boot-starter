package com.navasmart.vda5050.event.vehicle;

import org.springframework.context.ApplicationEvent;

/**
 * 外部发布的车辆取消注册事件。
 */
public class VehicleUnRegistryEvent extends ApplicationEvent {

    private final String manufacturer;
    private final String serialNumber;
    private final boolean proxyMode;
    private final boolean serverMode;


    public VehicleUnRegistryEvent(Object source, String manufacturer, String serialNumber, boolean proxyMode, boolean serverMode) {
        super(source);

        this.manufacturer = manufacturer;
        this.serialNumber = serialNumber;

        this.proxyMode = proxyMode;
        this.serverMode = serverMode;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public boolean isProxyMode() {
        return proxyMode;
    }

    public boolean isServerMode() {
        return serverMode;
    }
}
