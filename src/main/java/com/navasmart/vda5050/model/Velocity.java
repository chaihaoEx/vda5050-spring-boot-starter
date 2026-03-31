package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 AGV 速度信息。
 * <p>
 * 描述 AGV 当前的运动速度，包括前向速度、横向速度和角速度。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Velocity {

    /**
     * AGV 前向（纵向）速度，单位：m/s。
     */
    private double vx;

    /**
     * AGV 横向速度，单位：m/s。
     * <p>
     * 对于非全向运动的 AGV，该值通常为 0。
     * </p>
     */
    private double vy;

    /**
     * AGV 角速度（绕 Z 轴旋转），单位：rad/s。
     */
    private double omega;

    public Velocity() {}

    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public double getOmega() { return omega; }
    public void setOmega(double omega) { this.omega = omega; }
}
