package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 电池状态信息。
 * <p>
 * 描述 AGV 当前的电池状态，包括电量、电压、健康度、充电状态以及预估续航距离。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatteryState {

    /**
     * 电池电量百分比。
     * <p>
     * 取值范围：0.0 ~ 100.0，表示当前剩余电量的百分比。
     * </p>
     */
    private double batteryCharge;

    /**
     * 电池电压，单位：伏特（V）。
     * <p>
     * 可选字段，如果 AGV 无法提供电压信息则为 {@code null}。
     * </p>
     */
    private Double batteryVoltage;

    /**
     * 电池健康度，取值范围：0 ~ 100。
     * <p>
     * 表示电池的整体健康状况，100 为最佳状态。可选字段。
     * </p>
     */
    private Integer batteryHealth;

    /**
     * AGV 是否正在充电。
     * <p>
     * {@code true} 表示正在充电，{@code false} 表示未充电。
     * </p>
     */
    private boolean charging;

    /**
     * 预估续航距离，单位：米。
     * <p>
     * 表示当前电量下 AGV 可行驶的预估剩余距离。可选字段。
     * </p>
     */
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
