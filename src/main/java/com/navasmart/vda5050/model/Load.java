package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 载荷信息。
 * <p>
 * 描述 AGV 当前承载的载荷详细信息，包括载荷标识、类型、位置、尺寸和重量等。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Load {

    /**
     * 载荷的唯一标识符。可选字段。
     */
    private String loadId;

    /**
     * 载荷类型名称。可选字段。
     */
    private String loadType;

    /**
     * 载荷在 AGV 上的放置位置标识。可选字段。
     * <p>
     * 例如 AGV 有多个载荷位时，用于区分载荷放在哪个位置。
     * </p>
     */
    private String loadPosition;

    /**
     * 载荷包围盒参考点，描述载荷包围盒相对于 AGV 坐标系的位置。可选字段。
     */
    private BoundingBoxReference boundingBoxReference;

    /**
     * 载荷尺寸信息（长、宽、高）。可选字段。
     */
    private LoadDimensions loadDimensions;

    /**
     * 载荷重量，单位：千克（kg）。可选字段。
     */
    private Double weight;

    public Load() {}

    public String getLoadId() { return loadId; }
    public void setLoadId(String loadId) { this.loadId = loadId; }

    public String getLoadType() { return loadType; }
    public void setLoadType(String loadType) { this.loadType = loadType; }

    public String getLoadPosition() { return loadPosition; }
    public void setLoadPosition(String loadPosition) { this.loadPosition = loadPosition; }

    public BoundingBoxReference getBoundingBoxReference() { return boundingBoxReference; }
    public void setBoundingBoxReference(BoundingBoxReference boundingBoxReference) { this.boundingBoxReference = boundingBoxReference; }

    public LoadDimensions getLoadDimensions() { return loadDimensions; }
    public void setLoadDimensions(LoadDimensions loadDimensions) { this.loadDimensions = loadDimensions; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
