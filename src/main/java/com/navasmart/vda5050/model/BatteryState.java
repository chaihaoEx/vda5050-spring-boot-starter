package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatteryState {

    private double batteryCharge;
    private Double batteryVoltage;
    private Integer batteryHealth;
    private boolean charging;
    private Long reach;

    public BatteryState() {}

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
}
