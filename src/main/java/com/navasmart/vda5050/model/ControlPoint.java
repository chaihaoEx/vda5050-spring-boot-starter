package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 NURBS 轨迹控制点（ControlPoint）。
 * <p>
 * 定义 {@link Trajectory} 中 NURBS 曲线的一个控制点坐标及其权重。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ControlPoint {

    /** 控制点在地图坐标系中的 X 坐标（单位：米）。 */
    private double x;

    /** 控制点在地图坐标系中的 Y 坐标（单位：米）。 */
    private double y;

    /**
     * 控制点权重，影响曲线向该控制点靠近的程度。
     * 可选字段。默认值为 1.0。权重越大，曲线越接近该控制点。
     */
    private Double weight;

    public ControlPoint() {}

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
