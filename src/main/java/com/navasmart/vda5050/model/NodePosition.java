package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodePosition {

    private double x;
    private double y;
    private Double theta;
    private Double allowedDeviationXY;
    private Double allowedDeviationTheta;
    private String mapId;
    private String mapDescription;

    public NodePosition() {}

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public Double getTheta() { return theta; }
    public void setTheta(Double theta) { this.theta = theta; }

    public Double getAllowedDeviationXY() { return allowedDeviationXY; }
    public void setAllowedDeviationXY(Double allowedDeviationXY) { this.allowedDeviationXY = allowedDeviationXY; }

    public Double getAllowedDeviationTheta() { return allowedDeviationTheta; }
    public void setAllowedDeviationTheta(Double allowedDeviationTheta) { this.allowedDeviationTheta = allowedDeviationTheta; }

    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }

    public String getMapDescription() { return mapDescription; }
    public void setMapDescription(String mapDescription) { this.mapDescription = mapDescription; }
}
