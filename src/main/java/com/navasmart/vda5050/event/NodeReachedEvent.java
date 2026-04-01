package com.navasmart.vda5050.event;

/**
 * AGV 到达节点时发布的事件。
 */
public class NodeReachedEvent extends Vda5050Event {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final int sequenceId;

    public NodeReachedEvent(Object source, String vehicleId, String nodeId, int sequenceId) {
        super(source, vehicleId);
        this.nodeId = nodeId;
        this.sequenceId = sequenceId;
    }

    public String getNodeId() { return nodeId; }

    public int getSequenceId() { return sequenceId; }
}
