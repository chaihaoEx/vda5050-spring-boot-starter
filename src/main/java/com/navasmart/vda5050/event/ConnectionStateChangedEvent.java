package com.navasmart.vda5050.event;

/**
 * AGV 连接状态变更时发布的事件。
 */
public class ConnectionStateChangedEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String previousState;
    private final String newState;

    public ConnectionStateChangedEvent(Object source, String vehicleId,
                                       String previousState, String newState) {
        super(source, vehicleId);
        this.previousState = previousState;
        this.newState = newState;
    }

    public String getPreviousState() { return previousState; }

    public String getNewState() { return newState; }
}
