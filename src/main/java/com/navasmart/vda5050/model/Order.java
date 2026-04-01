package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VDA5050 订单消息（Order）。
 * <p>
 * 由调度系统（主控）通过 MQTT 发送给 AGV，用于下发行驶路径和任务。
 * <p>
 * MQTT 主题方向：主控 -> AGV
 * <p>
 * 主题格式：{@code <interfaceName>/<majorVersion>/<manufacturer>/<serialNumber>/order}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    /** 消息头序号，每次发送递增，用于消息排序和去重。 */
    private int headerId;

    /** 消息时间戳，ISO 8601 格式（如 {@code 2024-01-01T12:00:00.000Z}）。 */
    private String timestamp;

    /** VDA5050 协议版本号（如 "2.0.0"）。 */
    private String version;

    /** AGV 制造商名称。 */
    private String manufacturer;

    /** AGV 唯一序列号。 */
    private String serialNumber;

    /** 订单唯一标识符。相同 orderId 的消息属于同一订单，通过 orderUpdateId 区分更新。 */
    private String orderId;

    /** 订单更新序号。同一 orderId 下，该值递增表示订单的增量更新。AGV 仅接受大于当前值的更新。 */
    private long orderUpdateId;

    /** 区域集 ID，指定 AGV 应使用的区域配置。可选字段。 */
    private String zoneSetId;

    /** 有序节点列表，定义 AGV 的行驶路径中必须经过的节点。至少包含一个节点。 */
    private List<Node> nodes = new ArrayList<>();

    /** 有序边列表，连接相邻节点。边的数量比节点少一个。 */
    private List<Edge> edges = new ArrayList<>();

    public Order() {}

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

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public long getOrderUpdateId() { return orderUpdateId; }
    public void setOrderUpdateId(long orderUpdateId) { this.orderUpdateId = orderUpdateId; }

    public String getZoneSetId() { return zoneSetId; }
    public void setZoneSetId(String zoneSetId) { this.zoneSetId = zoneSetId; }

    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>(); }

    public List<Edge> getEdges() { return edges; }
    public void setEdges(List<Edge> edges) { this.edges = edges != null ? new ArrayList<>(edges) : new ArrayList<>(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Order that = (Order) o;
        return orderUpdateId == that.orderUpdateId && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, orderUpdateId);
    }
}
