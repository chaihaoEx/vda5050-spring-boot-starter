package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VDA5050 路径边（Edge）。
 * <p>
 * 定义 AGV 行驶路径中连接两个相邻节点的边，包含行驶约束（速度、高度、方向等）
 * 以及可选的轨迹定义。通过 {@code released} 标志区分已确认边和预览边。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Edge {

    /** 边的唯一标识符，对应地图中的边 ID。 */
    private String edgeId;

    /** 边在订单中的序列号。边的序号为奇数（1, 3, 5, ...），位于两个节点之间。 */
    private int sequenceId;

    /** 边的人类可读描述。可选字段。 */
    private String edgeDescription;

    /**
     * 边的释放标志。
     * {@code true} 表示已确认边（base），AGV 必须通过；
     * {@code false} 表示预览边（horizon），AGV 仅用于路径规划。
     */
    private boolean released;

    /** 起始节点 ID，对应该边起点的 {@link Node#getNodeId()}。 */
    private String startNodeId;

    /** 结束节点 ID，对应该边终点的 {@link Node#getNodeId()}。 */
    private String endNodeId;

    /** 该边上允许的最大行驶速度（单位：米/秒）。可选字段。 */
    private Double maxSpeed;

    /** 该边上允许的最大负载高度（单位：米）。可选字段。 */
    private Double maxHeight;

    /** 该边上允许的最小负载高度（单位：米）。可选字段。 */
    private Double minHeight;

    /** AGV 在该边上行驶时的朝向角度（单位：弧度）。可选字段。 */
    private Double orientation;

    /**
     * 朝向类型。可选字段。取值包括：
     * <ul>
     *   <li>{@code GLOBAL} - 全局朝向，AGV 在整条边上保持指定朝向</li>
     *   <li>{@code TANGENTIAL} - 切线朝向，AGV 朝向沿轨迹切线方向</li>
     * </ul>
     */
    private String orientationType;

    /** 行驶方向。可选字段。如 "left"、"right"、"straight" 等，由 AGV 厂商定义。 */
    private String direction;

    /** 是否允许旋转。可选字段。{@code true} 表示 AGV 可在该边上旋转。 */
    private Boolean rotationAllowed;

    /** 最大旋转速度（单位：弧度/秒）。可选字段。 */
    private Double maxRotationSpeed;

    /** NURBS 轨迹定义。可选字段。如果不提供，AGV 自行规划起点到终点的路径。 */
    private Trajectory trajectory;

    /** 边的长度（单位：米）。可选字段，用于路径规划。 */
    private Double length;

    /** 经过该边时需要执行的动作列表。可以为空列表。 */
    private List<Action> actions = new ArrayList<>();

    public Edge() {}

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public String getEdgeDescription() { return edgeDescription; }
    public void setEdgeDescription(String edgeDescription) { this.edgeDescription = edgeDescription; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public String getStartNodeId() { return startNodeId; }
    public void setStartNodeId(String startNodeId) { this.startNodeId = startNodeId; }

    public String getEndNodeId() { return endNodeId; }
    public void setEndNodeId(String endNodeId) { this.endNodeId = endNodeId; }

    public Double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(Double maxSpeed) { this.maxSpeed = maxSpeed; }

    public Double getMaxHeight() { return maxHeight; }
    public void setMaxHeight(Double maxHeight) { this.maxHeight = maxHeight; }

    public Double getMinHeight() { return minHeight; }
    public void setMinHeight(Double minHeight) { this.minHeight = minHeight; }

    public Double getOrientation() { return orientation; }
    public void setOrientation(Double orientation) { this.orientation = orientation; }

    public String getOrientationType() { return orientationType; }
    public void setOrientationType(String orientationType) { this.orientationType = orientationType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Boolean getRotationAllowed() { return rotationAllowed; }
    public void setRotationAllowed(Boolean rotationAllowed) { this.rotationAllowed = rotationAllowed; }

    public Double getMaxRotationSpeed() { return maxRotationSpeed; }
    public void setMaxRotationSpeed(Double maxRotationSpeed) { this.maxRotationSpeed = maxRotationSpeed; }

    public Trajectory getTrajectory() { return trajectory; }
    public void setTrajectory(Trajectory trajectory) { this.trajectory = trajectory; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Edge that = (Edge) o;
        return sequenceId == that.sequenceId && Objects.equals(edgeId, that.edgeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeId, sequenceId);
    }
}
