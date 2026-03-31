package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 载荷包围盒参考点。
 * <p>
 * 描述载荷包围盒的参考点在 AGV 坐标系中的位置和朝向。
 * 包围盒的中心点由 x、y、z 坐标定义，可选的 theta 定义旋转角度。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoundingBoxReference {

    /**
     * 包围盒参考点的 X 坐标（相对于 AGV 坐标系），单位：米。
     */
    private double x;

    /**
     * 包围盒参考点的 Y 坐标（相对于 AGV 坐标系），单位：米。
     */
    private double y;

    /**
     * 包围盒参考点的 Z 坐标（相对于 AGV 坐标系），单位：米。
     */
    private double z;

    /**
     * 包围盒的旋转角度，单位：弧度。可选字段。
     */
    private Double theta;

    public BoundingBoxReference() {}

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public Double getTheta() { return theta; }
    public void setTheta(Double theta) { this.theta = theta; }
}
