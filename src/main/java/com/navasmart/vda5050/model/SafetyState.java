package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SafetyState {

    private String eStop;
    private boolean fieldViolation;

    public SafetyState() {}

    public String getEStop() { return eStop; }
    public void setEStop(String eStop) { this.eStop = eStop; }

    public boolean isFieldViolation() { return fieldViolation; }
    public void setFieldViolation(boolean fieldViolation) { this.fieldViolation = fieldViolation; }
}
