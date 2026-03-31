package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 AGV 位置信息。
 * <p>
 * 描述 AGV 在地图坐标系中的当前位置，包括坐标、朝向角、定位质量等信息。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvPosition {

    /**
     * AGV 定位是否已初始化。
     * <p>
     * {@code true} 表示 AGV 已完成定位初始化，位置数据可信；
     * {@code false} 表示位置数据可能不准确。
     * </p>
     */
    private boolean positionInitialized;

    /**
     * 定位质量评分。
     * <p>
     * 取值范围：0.0 ~ 1.0，其中 0.0 表示定位质量最差，1.0 表示定位质量最佳。
     * 可选字段，如果 AGV 无法提供定位质量信息则为 {@code null}。
     * </p>
     */
    private Double localizationScore;

    /**
     * 位置偏差范围，单位：米。
     * <p>
     * 表示 AGV 当前定位的不确定性半径。可选字段。
     * </p>
     */
    private Double deviationRange;

    /**
     * AGV 在地图坐标系中的 X 坐标，单位：米。
     */
    private double x;

    /**
     * AGV 在地图坐标系中的 Y 坐标，单位：米。
     */
    private double y;

    /**
     * AGV 的朝向角，单位：弧度。
     * <p>
     * 取值范围：-PI ~ PI，表示 AGV 在地图坐标系中的航向角。
     * </p>
     */
    private double theta;

    /**
     * AGV 当前所在地图的唯一标识符。
     */
    private String mapId;

    /**
     * 地图的可读描述信息。可选字段。
     */
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
