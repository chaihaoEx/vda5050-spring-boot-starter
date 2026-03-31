package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Velocity {

    private double vx;
    private double vy;
    private double omega;

    public Velocity() {}

    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public double getOmega() { return omega; }
    public void setOmega(double omega) { this.omega = omega; }
}
