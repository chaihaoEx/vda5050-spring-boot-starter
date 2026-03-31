package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdgeState {

    private String edgeId;
    private int sequenceId;
    private String edgeDescription;
    private boolean released;
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
