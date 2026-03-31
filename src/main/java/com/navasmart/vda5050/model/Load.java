package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Load {

    private String loadId;
    private String loadType;
    private String loadPosition;
    private BoundingBoxReference boundingBoxReference;
    private LoadDimensions loadDimensions;
    private Double weight;

    public Load() {}

    public String getLoadId() { return loadId; }
    public void setLoadId(String loadId) { this.loadId = loadId; }

    public String getLoadType() { return loadType; }
    public void setLoadType(String loadType) { this.loadType = loadType; }

    public String getLoadPosition() { return loadPosition; }
    public void setLoadPosition(String loadPosition) { this.loadPosition = loadPosition; }

    public BoundingBoxReference getBoundingBoxReference() { return boundingBoxReference; }
    public void setBoundingBoxReference(BoundingBoxReference boundingBoxReference) { this.boundingBoxReference = boundingBoxReference; }

    public LoadDimensions getLoadDimensions() { return loadDimensions; }
    public void setLoadDimensions(LoadDimensions loadDimensions) { this.loadDimensions = loadDimensions; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
