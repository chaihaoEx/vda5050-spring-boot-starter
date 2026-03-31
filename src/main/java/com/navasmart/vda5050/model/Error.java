package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Error {

    private String errorType;
    private List<ErrorReference> errorReferences = new ArrayList<>();
    private String errorDescription;
    private String errorLevel;

    public Error() {}

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public List<ErrorReference> getErrorReferences() { return errorReferences; }
    public void setErrorReferences(List<ErrorReference> errorReferences) { this.errorReferences = errorReferences; }

    public String getErrorDescription() { return errorDescription; }
    public void setErrorDescription(String errorDescription) { this.errorDescription = errorDescription; }

    public String getErrorLevel() { return errorLevel; }
    public void setErrorLevel(String errorLevel) { this.errorLevel = errorLevel; }
}
