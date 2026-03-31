package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 节点位置坐标（NodePosition）。
 * <p>
 * 描述节点在地图中的精确坐标位置，包括 X/Y 坐标、朝向角度以及允许的偏差范围。
 * 坐标系符合 VDA5050 标准定义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodePosition {

    /** 节点在地图坐标系中的 X 坐标（单位：米）。 */
    private double x;

    /** 节点在地图坐标系中的 Y 坐标（单位：米）。 */
    private double y;

    /**
     * AGV 到达该节点时的朝向角度（单位：弧度，范围 [-PI, PI]）。
     * 可选字段。如果为 null，表示 AGV 可以任意朝向到达。
     */
    private Double theta;

    /** 允许的 XY 平面位置偏差（单位：米）。可选字段。如果为 null，使用 AGV 默认值。 */
    private Double allowedDeviationXY;

    /** 允许的朝向角偏差（单位：弧度）。可选字段。如果为 null，使用 AGV 默认值。 */
    private Double allowedDeviationTheta;

    /** 地图唯一标识符，用于标识节点所在的地图。 */
    private String mapId;

    /** 地图的人类可读描述。可选字段。 */
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
