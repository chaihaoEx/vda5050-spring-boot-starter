package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {

    private String infoType;
    private List<InfoReference> infoReferences = new ArrayList<>();
    private String infoDescription;
    private String infoLevel;

    public Info() {}

    public String getInfoType() { return infoType; }
    public void setInfoType(String infoType) { this.infoType = infoType; }

    public List<InfoReference> getInfoReferences() { return infoReferences; }
    public void setInfoReferences(List<InfoReference> infoReferences) { this.infoReferences = infoReferences; }

    public String getInfoDescription() { return infoDescription; }
    public void setInfoDescription(String infoDescription) { this.infoDescription = infoDescription; }

    public String getInfoLevel() { return infoLevel; }
    public void setInfoLevel(String infoLevel) { this.infoLevel = infoLevel; }
}
