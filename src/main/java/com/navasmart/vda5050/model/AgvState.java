package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 AGV 状态消息（State）。
 * <p>
 * 由 AGV 周期性上报给调度系统，包含 AGV 的当前位置、速度、电池状态、
 * 驾驶状态、订单执行进度、动作执行状态、错误和信息等。
 * <p>
 * MQTT 主题方向：AGV -> 主控
 * <p>
 * 主题格式：{@code <interfaceName>/<majorVersion>/<manufacturer>/<serialNumber>/state}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvState {

    /** 消息头序号，每次发送递增，用于消息排序和去重。 */
    private int headerId;

    /** 消息时间戳，ISO 8601 格式。 */
    private String timestamp;

    /** VDA5050 协议版本号。 */
    private String version;

    /** AGV 制造商名称。 */
    private String manufacturer;

    /** AGV 唯一序列号。 */
    private String serialNumber;

    /** 当前正在执行的订单 ID。如果没有订单，为空字符串。 */
    private String orderId;

    /** 当前订单的更新序号，与 Order 中的 orderUpdateId 对应。 */
    private long orderUpdateId;

    /** 当前使用的区域集 ID。可选字段。 */
    private String zoneSetId;

    /** AGV 最近经过或驶离的节点 ID。 */
    private String lastNodeId;

    /** 最近经过节点的 sequenceId。 */
    private int lastNodeSequenceId;

    /** 尚未经过的节点状态列表（按行驶顺序排列）。 */
    private List<NodeState> nodeStates = new ArrayList<>();

    /** 尚未通过的边状态列表（按行驶顺序排列）。 */
    private List<EdgeState> edgeStates = new ArrayList<>();

    /** AGV 当前位置信息。可选字段，如果 AGV 无法提供定位则为 null。 */
    private AgvPosition agvPosition;

    /** AGV 当前速度信息。可选字段。 */
    private Velocity velocity;

    /** AGV 当前承载的负载列表。可选字段。 */
    private List<Load> loads;

    /** AGV 是否正在行驶。{@code true} 表示 AGV 当前正在移动。 */
    private boolean driving;

    /** AGV 是否处于暂停状态。{@code true} 表示 AGV 因暂停指令而停止。 */
    private boolean paused;

    /** AGV 是否请求新的 base 节点。{@code true} 表示 AGV 即将到达最后一个 base 节点，需要新的路径。 */
    private boolean newBaseRequested;

    /** 自最后一个节点以来行驶的距离（单位：米）。可选字段。 */
    private double distanceSinceLastNode;

    /** 当前所有动作的执行状态列表。 */
    private List<ActionState> actionStates = new ArrayList<>();

    /** 电池状态信息，包含电量百分比、电压、是否充电等。 */
    private BatteryState batteryState;

    /**
     * AGV 运行模式。取值包括：
     * <ul>
     *   <li>{@code AUTOMATIC} - 自动模式，AGV 由调度系统控制</li>
     *   <li>{@code SEMIAUTOMATIC} - 半自动模式</li>
     *   <li>{@code MANUAL} - 手动模式</li>
     *   <li>{@code SERVICE} - 维护模式</li>
     *   <li>{@code TEACHIN} - 示教模式</li>
     * </ul>
     */
    private String operatingMode;

    /** 当前错误列表。错误解决后应从列表中移除。 */
    private List<Error> errors = new ArrayList<>();

    /** 信息列表，用于上报非错误性质的通知或调试信息。 */
    private List<Info> informations = new ArrayList<>();

    /** 安全状态，包含急停状态和安全场使能状态。 */
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
    public void setNodeStates(List<NodeState> nodeStates) { this.nodeStates = nodeStates != null ? new ArrayList<>(nodeStates) : new ArrayList<>(); }

    public List<EdgeState> getEdgeStates() { return edgeStates; }
    public void setEdgeStates(List<EdgeState> edgeStates) { this.edgeStates = edgeStates != null ? new ArrayList<>(edgeStates) : new ArrayList<>(); }

    public AgvPosition getAgvPosition() { return agvPosition; }
    public void setAgvPosition(AgvPosition agvPosition) { this.agvPosition = agvPosition; }

    public Velocity getVelocity() { return velocity; }
    public void setVelocity(Velocity velocity) { this.velocity = velocity; }

    public List<Load> getLoads() { return loads; }
    public void setLoads(List<Load> loads) { this.loads = loads != null ? new ArrayList<>(loads) : new ArrayList<>(); }

    public boolean isDriving() { return driving; }
    public void setDriving(boolean driving) { this.driving = driving; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public boolean isNewBaseRequested() { return newBaseRequested; }
    public void setNewBaseRequested(boolean newBaseRequested) { this.newBaseRequested = newBaseRequested; }

    public double getDistanceSinceLastNode() { return distanceSinceLastNode; }
    public void setDistanceSinceLastNode(double distanceSinceLastNode) { this.distanceSinceLastNode = distanceSinceLastNode; }

    public List<ActionState> getActionStates() { return actionStates; }
    public void setActionStates(List<ActionState> actionStates) {
        this.actionStates = actionStates != null ? new ArrayList<>(actionStates) : new ArrayList<>();
    }

    public BatteryState getBatteryState() { return batteryState; }
    public void setBatteryState(BatteryState batteryState) { this.batteryState = batteryState; }

    public String getOperatingMode() { return operatingMode; }
    public void setOperatingMode(String operatingMode) { this.operatingMode = operatingMode; }

    public List<Error> getErrors() { return errors; }
    public void setErrors(List<Error> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public List<Info> getInformations() { return informations; }
    public void setInformations(List<Info> informations) {
        this.informations = informations != null ? new ArrayList<>(informations) : new ArrayList<>();
    }

    public SafetyState getSafetyState() { return safetyState; }
    public void setSafetyState(SafetyState safetyState) { this.safetyState = safetyState; }
}
