package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvState {

    private int headerId;
    private String timestamp;
    private String version;
    private String manufacturer;
    private String serialNumber;
    private String orderId;
    private long orderUpdateId;
    private String zoneSetId;
    private String lastNodeId;
    private int lastNodeSequenceId;
    private List<NodeState> nodeStates = new ArrayList<>();
    private List<EdgeState> edgeStates = new ArrayList<>();
    private AgvPosition agvPosition;
    private Velocity velocity;
    private List<Load> loads;
    private boolean driving;
    private boolean paused;
    private boolean newBaseRequested;
    private double distanceSinceLastNode;
    private List<ActionState> actionStates = new ArrayList<>();
    private BatteryState batteryState;
    private String operatingMode;
    private List<Error> errors = new ArrayList<>();
    private List<Info> informations = new ArrayList<>();
    private SafetyState safetyState;

    public AgvState() {}

    public int getHeaderId() { return headerId; }
    public void setHeaderId(int headerId) { this.headerId = headerId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public long getOrderUpdateId() { return orderUpdateId; }
    public void setOrderUpdateId(long orderUpdateId) { this.orderUpdateId = orderUpdateId; }

    public String getZoneSetId() { return zoneSetId; }
    public void setZoneSetId(String zoneSetId) { this.zoneSetId = zoneSetId; }

    public String getLastNodeId() { return lastNodeId; }
    public void setLastNodeId(String lastNodeId) { this.lastNodeId = lastNodeId; }

    public int getLastNodeSequenceId() { return lastNodeSequenceId; }
    public void setLastNodeSequenceId(int lastNodeSequenceId) { this.lastNodeSequenceId = lastNodeSequenceId; }

    public List<NodeState> getNodeStates() { return nodeStates; }
    public void setNodeStates(List<NodeState> nodeStates) { this.nodeStates = nodeStates; }

    public List<EdgeState> getEdgeStates() { return edgeStates; }
    public void setEdgeStates(List<EdgeState> edgeStates) { this.edgeStates = edgeStates; }

    public AgvPosition getAgvPosition() { return agvPosition; }
    public void setAgvPosition(AgvPosition agvPosition) { this.agvPosition = agvPosition; }

    public Velocity getVelocity() { return velocity; }
    public void setVelocity(Velocity velocity) { this.velocity = velocity; }

    public List<Load> getLoads() { return loads; }
    public void setLoads(List<Load> loads) { this.loads = loads; }

    public boolean isDriving() { return driving; }
    public void setDriving(boolean driving) { this.driving = driving; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public boolean isNewBaseRequested() { return newBaseRequested; }
    public void setNewBaseRequested(boolean newBaseRequested) { this.newBaseRequested = newBaseRequested; }

    public double getDistanceSinceLastNode() { return distanceSinceLastNode; }
    public void setDistanceSinceLastNode(double distanceSinceLastNode) { this.distanceSinceLastNode = distanceSinceLastNode; }

    public List<ActionState> getActionStates() { return actionStates; }
    public void setActionStates(List<ActionState> actionStates) { this.actionStates = actionStates; }

    public BatteryState getBatteryState() { return batteryState; }
    public void setBatteryState(BatteryState batteryState) { this.batteryState = batteryState; }

    public String getOperatingMode() { return operatingMode; }
    public void setOperatingMode(String operatingMode) { this.operatingMode = operatingMode; }

    public List<Error> getErrors() { return errors; }
    public void setErrors(List<Error> errors) { this.errors = errors; }

    public List<Info> getInformations() { return informations; }
    public void setInformations(List<Info> informations) { this.informations = informations; }

    public SafetyState getSafetyState() { return safetyState; }
    public void setSafetyState(SafetyState safetyState) { this.safetyState = safetyState; }
}
