package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node {

    private String nodeId;
    private int sequenceId;
    private String nodeDescription;
    private boolean released;
    private NodePosition nodePosition;
    private List<Action> actions = new ArrayList<>();

    public Node() {}

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public String getNodeDescription() { return nodeDescription; }
    public void setNodeDescription(String nodeDescription) { this.nodeDescription = nodeDescription; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public NodePosition getNodePosition() { return nodePosition; }
    public void setNodePosition(NodePosition nodePosition) { this.nodePosition = nodePosition; }

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions; }
}
