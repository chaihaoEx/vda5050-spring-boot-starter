package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class VehicleContext {

    // Identity (immutable)
    private final String manufacturer;
    private final String serialNumber;
    private final String vehicleId;

    // Thread-safe locking
    private final ReentrantLock stateLock = new ReentrantLock();

    // Mode flags
    private boolean proxyMode;
    private boolean serverMode;

    // Proxy mode state
    private AgvState agvState = new AgvState();
    private Order currentOrder;
    private ProxyClientState clientState = ProxyClientState.IDLE;
    private int currentNodeIndex;
    private int nextStopIndex;
    private boolean reachedWaypoint;

    // Server mode state
    private AgvState lastReceivedState;
    private Order lastSentOrder;
    private String connectionState = "OFFLINE";
    private long lastSeenTimestamp;

    // Header ID generators
    private final AtomicInteger stateHeaderId = new AtomicInteger(0);
    private final AtomicInteger connectionHeaderId = new AtomicInteger(0);
    private final AtomicInteger orderHeaderId = new AtomicInteger(0);
    private final AtomicInteger instantActionsHeaderId = new AtomicInteger(0);

    public VehicleContext(String manufacturer, String serialNumber) {
        this.manufacturer = manufacturer;
        this.serialNumber = serialNumber;
        this.vehicleId = manufacturer + ":" + serialNumber;
    }

    public void lock() { stateLock.lock(); }
    public void unlock() { stateLock.unlock(); }

    public int nextStateHeaderId() { return stateHeaderId.incrementAndGet(); }
    public int nextConnectionHeaderId() { return connectionHeaderId.incrementAndGet(); }
    public int nextOrderHeaderId() { return orderHeaderId.incrementAndGet(); }
    public int nextInstantActionsHeaderId() { return instantActionsHeaderId.incrementAndGet(); }

    // Identity getters
    public String getManufacturer() { return manufacturer; }
    public String getSerialNumber() { return serialNumber; }
    public String getVehicleId() { return vehicleId; }

    // Mode flags
    public boolean isProxyMode() { return proxyMode; }
    public void setProxyMode(boolean proxyMode) { this.proxyMode = proxyMode; }

    public boolean isServerMode() { return serverMode; }
    public void setServerMode(boolean serverMode) { this.serverMode = serverMode; }

    // Proxy state
    public AgvState getAgvState() { return agvState; }
    public void setAgvState(AgvState agvState) { this.agvState = agvState; }

    public Order getCurrentOrder() { return currentOrder; }
    public void setCurrentOrder(Order currentOrder) { this.currentOrder = currentOrder; }

    public ProxyClientState getClientState() { return clientState; }
    public void setClientState(ProxyClientState clientState) { this.clientState = clientState; }

    public int getCurrentNodeIndex() { return currentNodeIndex; }
    public void setCurrentNodeIndex(int currentNodeIndex) { this.currentNodeIndex = currentNodeIndex; }

    public int getNextStopIndex() { return nextStopIndex; }
    public void setNextStopIndex(int nextStopIndex) { this.nextStopIndex = nextStopIndex; }

    public boolean isReachedWaypoint() { return reachedWaypoint; }
    public void setReachedWaypoint(boolean reachedWaypoint) { this.reachedWaypoint = reachedWaypoint; }

    // Server state
    public AgvState getLastReceivedState() { return lastReceivedState; }
    public void setLastReceivedState(AgvState lastReceivedState) { this.lastReceivedState = lastReceivedState; }

    public Order getLastSentOrder() { return lastSentOrder; }
    public void setLastSentOrder(Order lastSentOrder) { this.lastSentOrder = lastSentOrder; }

    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }

    public long getLastSeenTimestamp() { return lastSeenTimestamp; }
    public void setLastSeenTimestamp(long lastSeenTimestamp) { this.lastSeenTimestamp = lastSeenTimestamp; }
}
