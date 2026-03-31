package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Action {

    private String actionType;
    private String actionId;
    private String actionDescription;
    private String blockingType;
    private List<ActionParameter> actionParameters = new ArrayList<>();

    public Action() {}

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }

    public String getBlockingType() { return blockingType; }
    public void setBlockingType(String blockingType) { this.blockingType = blockingType; }

    public List<ActionParameter> getActionParameters() { return actionParameters; }
    public void setActionParameters(List<ActionParameter> actionParameters) { this.actionParameters = actionParameters; }
}
