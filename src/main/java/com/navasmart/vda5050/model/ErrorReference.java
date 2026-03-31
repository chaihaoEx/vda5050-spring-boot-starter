package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorReference {

    private String referenceKey;
    private String referenceValue;

    public ErrorReference() {}

    public ErrorReference(String referenceKey, String referenceValue) {
        this.referenceKey = referenceKey;
        this.referenceValue = referenceValue;
    }

    public String getReferenceKey() { return referenceKey; }
    public void setReferenceKey(String referenceKey) { this.referenceKey = referenceKey; }

    public String getReferenceValue() { return referenceValue; }
    public void setReferenceValue(String referenceValue) { this.referenceValue = referenceValue; }
}
