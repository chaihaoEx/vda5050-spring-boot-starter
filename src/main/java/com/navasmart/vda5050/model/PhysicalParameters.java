package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 AGV 物理参数（Factsheet 的一部分）。
 * <p>
 * 描述 AGV 的物理特性参数，包括速度范围、加减速能力以及车体尺寸。
 * 该对象通常包含在 AGV Factsheet 消息中，用于向主控系统描述 AGV 的物理能力。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhysicalParameters {

    /**
     * AGV 最小速度，单位：m/s。可选字段。
     */
    private Double speedMin;

    /**
     * AGV 最大速度，单位：m/s。可选字段。
     */
    private Double speedMax;

    /**
     * AGV 最大加速度，单位：m/s²。可选字段。
     */
    private Double accelerationMax;

    /**
     * AGV 最大减速度，单位：m/s²。可选字段。
     */
    private Double decelerationMax;

    /**
     * AGV 最小高度（载荷平台收缩时），单位：米。可选字段。
     */
    private Double heightMin;

    /**
     * AGV 最大高度（载荷平台升起时），单位：米。可选字段。
     */
    private Double heightMax;

    /**
     * AGV 车体宽度，单位：米。可选字段。
     */
    private Double width;

    /**
     * AGV 车体长度，单位：米。可选字段。
     */
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
