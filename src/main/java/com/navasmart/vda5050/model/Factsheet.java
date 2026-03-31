package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 AGV 能力描述消息（Factsheet）。
 * <p>
 * 描述 AGV 的类型规格和物理参数等静态能力信息，用于调度系统了解 AGV 的硬件能力。
 * 此消息通常在 AGV 启动时发布，或由调度系统通过即时动作请求获取。
 * <p>
 * MQTT 主题方向：AGV -> 主控
 * <p>
 * 主题格式：{@code <interfaceName>/<majorVersion>/<manufacturer>/<serialNumber>/factsheet}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Factsheet {

    /** 消息头序号，每次发送递增，用于消息排序和去重。 */
    private int headerId;

    /** 消息时间戳，ISO 8601 格式。 */
    private String timestamp;

    /** VDA5050 协议版本号。 */
    private String version;

    /** AGV 制造商名称。 */
    private String manufacturer;

    /** AGV 唯一序列号。 */
    private String serialNumber;

    /** AGV 类型规格，包含车辆类型、系列名称、本地化信息等。可选字段。 */
    private TypeSpecification typeSpecification;

    /** AGV 物理参数，包含尺寸、重量、速度限制等。可选字段。 */
    private PhysicalParameters physicalParameters;

    public Factsheet() {}

    public int getHeaderId() { return headerId; }
    public void setHeaderId(int headerId) { this.headerId = headerId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public TypeSpecification getTypeSpecification() { return typeSpecification; }
    public void setTypeSpecification(TypeSpecification typeSpecification) { this.typeSpecification = typeSpecification; }

    public PhysicalParameters getPhysicalParameters() { return physicalParameters; }
    public void setPhysicalParameters(PhysicalParameters physicalParameters) { this.physicalParameters = physicalParameters; }
}
