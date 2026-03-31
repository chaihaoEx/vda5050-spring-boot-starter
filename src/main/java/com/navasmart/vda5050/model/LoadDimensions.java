package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 载荷尺寸信息。
 * <p>
 * 描述载荷的三维尺寸（长、宽、高）。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoadDimensions {

    /**
     * 载荷长度，单位：米。
     */
    private double length;

    /**
     * 载荷宽度，单位：米。
     */
    private double width;

    /**
     * 载荷高度，单位：米。可选字段。
     */
    private Double height;

    public LoadDimensions() {}

    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
}
