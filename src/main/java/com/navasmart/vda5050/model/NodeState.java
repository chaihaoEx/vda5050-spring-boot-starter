package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * VDA5050 AGV 状态中的节点状态（NodeState）。
 * <p>
 * 出现在 {@link AgvState#getNodeStates()} 中，表示 AGV 尚未经过的节点。
 * AGV 经过一个节点后，该节点应从列表中移除。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeState {

    /** 节点唯一标识符。 */
    private String nodeId;

    /** 节点在订单中的序列号。 */
    private int sequenceId;

    /** 节点的人类可读描述。可选字段。 */
    private String nodeDescription;

    /** 节点位置坐标信息。可选字段。 */
    private NodePosition position;

    /** 节点是否为已确认节点。{@code true} 表示 base 节点，{@code false} 表示 horizon 节点。 */
    private boolean released;

    public NodeState() {}

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public String getNodeDescription() { return nodeDescription; }
    public void setNodeDescription(String nodeDescription) { this.nodeDescription = nodeDescription; }

    public NodePosition getPosition() { return position; }
    public void setPosition(NodePosition position) { this.position = position; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        NodeState that = (NodeState) o;
        return sequenceId == that.sequenceId && Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, sequenceId);
    }
}
