package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoundingBoxReference {

    private double x;
    private double y;
    private double z;
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
