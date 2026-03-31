package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderBuilder {

    private String orderId;
    private long orderUpdateId = 0;
    private String zoneSetId;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private int sequenceCounter = 0;

    private OrderBuilder(String orderId) {
        this.orderId = orderId;
    }

    public static OrderBuilder create(String orderId) {
        return new OrderBuilder(orderId);
    }

    public OrderBuilder orderUpdateId(long orderUpdateId) {
        this.orderUpdateId = orderUpdateId;
        return this;
    }

    public OrderBuilder zoneSetId(String zoneSetId) {
        this.zoneSetId = zoneSetId;
        return this;
    }

    public OrderBuilder addNode(String nodeId, double x, double y, double theta,
                                String mapId, boolean released) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setSequenceId(sequenceCounter);
        sequenceCounter += 2; // VDA5050: nodes have even sequence IDs
        node.setReleased(released);

        NodePosition pos = new NodePosition();
        pos.setX(x);
        pos.setY(y);
        pos.setTheta(theta);
        pos.setMapId(mapId);
        node.setNodePosition(pos);

        nodes.add(node);
        return this;
    }

    public OrderBuilder withAction(String actionId, String actionType,
                                   String blockingType, Map<String, String> params) {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Must add a node before adding actions");
        }
        Node lastNode = nodes.get(nodes.size() - 1);

        Action action = new Action();
        action.setActionId(actionId);
        action.setActionType(actionType);
        action.setBlockingType(blockingType);
        if (params != null) {
            List<ActionParameter> actionParams = new ArrayList<>();
            params.forEach((k, v) -> actionParams.add(new ActionParameter(k, v)));
            action.setActionParameters(actionParams);
        }

        lastNode.getActions().add(action);
        return this;
    }

    public OrderBuilder addEdge(String edgeId, String startNodeId, String endNodeId,
                                double maxSpeed, boolean released) {
        Edge edge = new Edge();
        edge.setEdgeId(edgeId);
        // Edges have odd sequence IDs (between nodes)
        edge.setSequenceId(nodes.isEmpty() ? 1 : (nodes.get(nodes.size() - 1).getSequenceId() + 1));
        edge.setStartNodeId(startNodeId);
        edge.setEndNodeId(endNodeId);
        edge.setMaxSpeed(maxSpeed);
        edge.setReleased(released);
        edges.add(edge);
        return this;
    }

    public Order build() {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(orderUpdateId);
        order.setZoneSetId(zoneSetId);
        order.setNodes(new ArrayList<>(nodes));
        order.setEdges(new ArrayList<>(edges));
        return order;
    }
}
