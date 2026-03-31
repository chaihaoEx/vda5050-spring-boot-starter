package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Edge {

    private String edgeId;
    private int sequenceId;
    private String edgeDescription;
    private boolean released;
    private String startNodeId;
    private String endNodeId;
    private Double maxSpeed;
    private Double maxHeight;
    private Double minHeight;
    private Double orientation;
    private String orientationType;
    private String direction;
    private Boolean rotationAllowed;
    private Double maxRotationSpeed;
    private Trajectory trajectory;
    private Double length;
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
    public void setActions(List<Action> actions) { this.actions = actions; }
}
