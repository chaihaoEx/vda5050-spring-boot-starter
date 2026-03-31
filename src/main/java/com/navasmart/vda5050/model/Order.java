package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    private int headerId;
    private String timestamp;
    private String version;
    private String manufacturer;
    private String serialNumber;
    private String orderId;
    private long orderUpdateId;
    private String zoneSetId;
    private List<Node> nodes = new ArrayList<>();
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
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    public List<Edge> getEdges() { return edges; }
    public void setEdges(List<Edge> edges) { this.edges = edges; }
}
