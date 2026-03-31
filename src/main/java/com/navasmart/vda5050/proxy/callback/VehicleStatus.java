package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.Load;

import java.util.List;

/**
 * 车辆状态数据传输对象，封装了 VDA5050 State 消息所需的实时车辆信息。
 *
 * <p>用户在 {@link Vda5050ProxyStateProvider#getVehicleStatus(String)} 中构造并返回此对象。
 * 框架会将其中的字段映射到 VDA5050 State 消息的对应部分：
 * <ul>
 *   <li>位置信息（x, y, theta, mapId）-> AgvPosition</li>
 *   <li>运动信息（vx, vy, omega, driving）-> Velocity</li>
 *   <li>电池信息（batteryCharge, batteryVoltage 等）-> BatteryState</li>
 *   <li>安全信息（eStop, fieldViolation）-> SafetyState</li>
 *   <li>可选信息（loads, errors）-> 直接映射到 State 消息</li>
 * </ul>
 * </p>
 *
 * <p>线程安全：此类为简单 POJO，非线程安全。每次调用应创建新实例或确保外部同步。</p>
 *
 * @see Vda5050ProxyStateProvider
 */
public class VehicleStatus {

    // 位置信息
    private boolean positionInitialized;
    private double x;
    private double y;
    private double theta;
    private String mapId;
    private Double localizationScore;

    // 运动信息
    private double vx;
    private double vy;
    private double omega;
    private boolean driving;

    // 电池信息
    private double batteryCharge;
    private Double batteryVoltage;
    private Integer batteryHealth;
    private boolean charging;
    private Long reach;

    // 安全信息
    private String eStop;
    private boolean fieldViolation;

    // 可选信息
    private List<Load> loads;
    private List<Error> errors;

    public VehicleStatus() {}

    // 位置信息
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

    // 运动信息
    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }

    public double getOmega() { return omega; }
    public void setOmega(double omega) { this.omega = omega; }

    public boolean isDriving() { return driving; }
    public void setDriving(boolean driving) { this.driving = driving; }

    // 电池信息
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

    // 安全信息
    public String getEStop() { return eStop; }
    public void setEStop(String eStop) { this.eStop = eStop; }

    public boolean isFieldViolation() { return fieldViolation; }
    public void setFieldViolation(boolean fieldViolation) { this.fieldViolation = fieldViolation; }

    // 可选信息
    public List<Load> getLoads() { return loads; }
    public void setLoads(List<Load> loads) { this.loads = loads; }

    public List<Error> getErrors() { return errors; }
    public void setErrors(List<Error> errors) { this.errors = errors; }
}
