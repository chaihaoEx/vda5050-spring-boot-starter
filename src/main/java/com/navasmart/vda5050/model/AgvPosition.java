package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvPosition {

    private boolean positionInitialized;
    private Double localizationScore;
    private Double deviationRange;
    private double x;
    private double y;
    private double theta;
    private String mapId;
    private String mapDescription;

    public AgvPosition() {}

    public boolean isPositionInitialized() { return positionInitialized; }
    public void setPositionInitialized(boolean positionInitialized) { this.positionInitialized = positionInitialized; }

    public Double getLocalizationScore() { return localizationScore; }
    public void setLocalizationScore(Double localizationScore) { this.localizationScore = localizationScore; }

    public Double getDeviationRange() { return deviationRange; }
    public void setDeviationRange(Double deviationRange) { this.deviationRange = deviationRange; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getTheta() { return theta; }
    public void setTheta(double theta) { this.theta = theta; }

    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }

    public String getMapDescription() { return mapDescription; }
    public void setMapDescription(String mapDescription) { this.mapDescription = mapDescription; }
}
