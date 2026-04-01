package com.navasmart.vda5050.event;

/**
 * AGV 状态上报超时时发布的事件。
 */
public class VehicleTimeoutEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String lastSeenTimestamp;

    public VehicleTimeoutEvent(Object source, String vehicleId, String lastSeenTimestamp) {
        super(source, vehicleId);
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public String getLastSeenTimestamp() { return lastSeenTimestamp; }
}
