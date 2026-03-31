package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 AGV 状态中的边状态（EdgeState）。
 * <p>
 * 出现在 {@link AgvState#getEdgeStates()} 中，表示 AGV 尚未通过的边。
 * AGV 通过一条边后，该边应从列表中移除。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdgeState {

    /** 边的唯一标识符。 */
    private String edgeId;

    /** 边在订单中的序列号。 */
    private int sequenceId;

    /** 边的人类可读描述。可选字段。 */
    private String edgeDescription;

    /** 边是否为已确认边。{@code true} 表示 base 边，{@code false} 表示 horizon 边。 */
    private boolean released;

    /** 该边的轨迹定义。可选字段。 */
    private Trajectory trajectory;

    public EdgeState() {}

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public String getEdgeDescription() { return edgeDescription; }
    public void setEdgeDescription(String edgeDescription) { this.edgeDescription = edgeDescription; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public Trajectory getTrajectory() { return trajectory; }
    public void setTrajectory(Trajectory trajectory) { this.trajectory = trajectory; }
}
