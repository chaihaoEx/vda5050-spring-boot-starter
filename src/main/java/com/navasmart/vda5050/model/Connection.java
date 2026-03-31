package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 连接状态消息（Connection）。
 * <p>
 * 由 AGV 上报，表示其与 MQTT Broker 的连接状态。
 * 此消息使用 QoS=1 发布，且设置 retained=true，以便调度系统随时获取 AGV 的最新连接状态。
 * AGV 的 MQTT 遗嘱消息（Last Will）也应设置为此主题，connectionState 为 {@code CONNECTIONBROKEN}。
 * <p>
 * MQTT 主题方向：AGV -> 主控
 * <p>
 * 主题格式：{@code <interfaceName>/<majorVersion>/<manufacturer>/<serialNumber>/connection}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Connection {

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

    /**
     * 连接状态。取值包括：
     * <ul>
     *   <li>{@code ONLINE} - AGV 与 Broker 连接正常</li>
     *   <li>{@code OFFLINE} - AGV 正常断开连接</li>
     *   <li>{@code CONNECTIONBROKEN} - 连接异常中断（由 Broker 的遗嘱消息发布）</li>
     * </ul>
     */
    private String connectionState;

    public Connection() {}

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

    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }
}
