package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeState {

    private String nodeId;
    private int sequenceId;
    private String nodeDescription;
    private NodePosition position;
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
}
