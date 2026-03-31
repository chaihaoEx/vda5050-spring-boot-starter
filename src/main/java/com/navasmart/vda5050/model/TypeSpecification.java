package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 AGV 类型规格信息（Factsheet 的一部分）。
 * <p>
 * 描述 AGV 的型号系列、运动学类型、车辆等级、最大载重以及支持的定位和导航方式。
 * 该对象通常包含在 AGV Factsheet 消息中，用于向主控系统描述 AGV 的基本能力。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TypeSpecification {

    /**
     * AGV 系列名称。
     */
    private String seriesName;

    /**
     * AGV 系列描述信息。可选字段。
     */
    private String seriesDescription;

    /**
     * AGV 运动学类型。
     * <p>
     * 例如 {@code "DIFF"}（差速驱动）、{@code "OMNI"}（全向驱动）、{@code "THREEWHEEL"}（三轮驱动）等。
     * </p>
     */
    private String agvKinematics;

    /**
     * AGV 等级/分类。可选字段。
     */
    private String agvClass;

    /**
     * AGV 最大载重质量，单位：千克（kg）。可选字段。
     */
    private Double maxLoadMass;

    /**
     * AGV 支持的定位类型列表。
     * <p>
     * 例如 {@code "NATURAL"}（自然导航）、{@code "REFLECTOR"}（反射板）、{@code "RFID"} 等。
     * </p>
     */
    private List<String> localizationTypes = new ArrayList<>();

    /**
     * AGV 支持的导航类型列表。
     * <p>
     * 例如 {@code "PHYSICAL_LINE_GUIDED"}（物理线引导）、{@code "VIRTUAL_LINE_GUIDED"}（虚拟线引导）、{@code "AUTONOMOUS"}（自主导航）等。
     * </p>
     */
    private List<String> navigationTypes = new ArrayList<>();

    public TypeSpecification() {}

    public String getSeriesName() { return seriesName; }
    public void setSeriesName(String seriesName) { this.seriesName = seriesName; }

    public String getSeriesDescription() { return seriesDescription; }
    public void setSeriesDescription(String seriesDescription) { this.seriesDescription = seriesDescription; }

    public String getAgvKinematics() { return agvKinematics; }
    public void setAgvKinematics(String agvKinematics) { this.agvKinematics = agvKinematics; }

    public String getAgvClass() { return agvClass; }
    public void setAgvClass(String agvClass) { this.agvClass = agvClass; }

    public Double getMaxLoadMass() { return maxLoadMass; }
    public void setMaxLoadMass(Double maxLoadMass) { this.maxLoadMass = maxLoadMass; }

    public List<String> getLocalizationTypes() { return localizationTypes; }
    public void setLocalizationTypes(List<String> localizationTypes) { this.localizationTypes = localizationTypes; }

    public List<String> getNavigationTypes() { return navigationTypes; }
    public void setNavigationTypes(List<String> navigationTypes) { this.navigationTypes = navigationTypes; }
}
