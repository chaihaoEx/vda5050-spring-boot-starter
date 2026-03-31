package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhysicalParameters {

    private Double speedMin;
    private Double speedMax;
    private Double accelerationMax;
    private Double decelerationMax;
    private Double heightMin;
    private Double heightMax;
    private Double width;
    private Double length;

    public PhysicalParameters() {}

    public Double getSpeedMin() { return speedMin; }
    public void setSpeedMin(Double speedMin) { this.speedMin = speedMin; }

    public Double getSpeedMax() { return speedMax; }
    public void setSpeedMax(Double speedMax) { this.speedMax = speedMax; }

    public Double getAccelerationMax() { return accelerationMax; }
    public void setAccelerationMax(Double accelerationMax) { this.accelerationMax = accelerationMax; }

    public Double getDecelerationMax() { return decelerationMax; }
    public void setDecelerationMax(Double decelerationMax) { this.decelerationMax = decelerationMax; }

    public Double getHeightMin() { return heightMin; }
    public void setHeightMin(Double heightMin) { this.heightMin = heightMin; }

    public Double getHeightMax() { return heightMax; }
    public void setHeightMax(Double heightMax) { this.heightMax = heightMax; }

    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }
}
