package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.Load;

import java.util.List;

public class VehicleStatus {

    // Position
    private boolean positionInitialized;
    private double x;
    private double y;
    private double theta;
    private String mapId;
    private Double localizationScore;

    // Motion
    private double vx;
    private double vy;
    private double omega;
    private boolean driving;

    // Battery
    private double batteryCharge;
    private Double batteryVoltage;
    private Integer batteryHealth;
    private boolean charging;
    private Long reach;

    // Safety
    private String eStop;
    private boolean fieldViolation;

    // Optional
    private List<Load> loads;
    private List<Error> errors;

    public VehicleStatus() {}

    // Position
    public boolean isPositionInitialized() { return positionInitialized; }
    public void setPositionInitialized(boolean positionInitialized) { this.positionInitialized = positionInitialized; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getTheta() { return theta; }
    public void setTheta(double theta) { this.theta = theta; }

    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }

    public Double getLocalizationScore() { return localizationScore; }
    public void setLocalizationScore(Double localizationScore) { this.localizationScore = localizationScore; }

    // Motion
    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public double getOmega() { return omega; }
    public void setOmega(double omega) { this.omega = omega; }

    public boolean isDriving() { return driving; }
    public void setDriving(boolean driving) { this.driving = driving; }

    // Battery
    public double getBatteryCharge() { return batteryCharge; }
    public void setBatteryCharge(double batteryCharge) { this.batteryCharge = batteryCharge; }

    public Double getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(Double batteryVoltage) { this.batteryVoltage = batteryVoltage; }

    public Integer getBatteryHealth() { return batteryHealth; }
    public void setBatteryHealth(Integer batteryHealth) { this.batteryHealth = batteryHealth; }

    public boolean isCharging() { return charging; }
    public void setCharging(boolean charging) { this.charging = charging; }

    public Long getReach() { return reach; }
    public void setReach(Long reach) { this.reach = reach; }

    // Safety
    public String getEStop() { return eStop; }
    public void setEStop(String eStop) { this.eStop = eStop; }

    public boolean isFieldViolation() { return fieldViolation; }
    public void setFieldViolation(boolean fieldViolation) { this.fieldViolation = fieldViolation; }

    // Optional
    public List<Load> getLoads() { return loads; }
    public void setLoads(List<Load> loads) { this.loads = loads; }

    public List<Error> getErrors() { return errors; }
    public void setErrors(List<Error> errors) { this.errors = errors; }
}
