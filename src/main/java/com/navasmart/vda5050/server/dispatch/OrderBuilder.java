package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VDA5050 Order 消息的链式构建器，简化订单的构造过程。
 *
 * <p>自动管理 VDA5050 规范要求的序列号（sequenceId）分配规则：
 * 节点使用偶数序列号（0, 2, 4, ...），边使用奇数序列号（1, 3, 5, ...）。</p>
 *
 * <p>链式调用示例：</p>
 * <pre>{@code
 * Order order = OrderBuilder.create("order-001")
 *     .orderUpdateId(0)
 *     .zoneSetId("zone-1")
 *     .addNode("node-1", 0.0, 0.0, 0.0, "map-1", true)
 *     .withAction("action-1", "pick", "HARD", Map.of("station", "S1"))
 *     .addEdge("edge-1", "node-1", "node-2", 1.5, true)
 *     .addNode("node-2", 10.0, 0.0, 0.0, "map-1", true)
 *     .withAction("action-2", "drop", "HARD", null)
 *     .build();
 *
 * orderDispatcher.sendOrder("vehicle-001", order);
 * }</pre>
 *
 * <p>线程安全：此类非线程安全，应在单线程中构建订单。</p>
 *
 * @see OrderDispatcher
 */
public class OrderBuilder {

    private String orderId;
    private long orderUpdateId = 0;
    private String zoneSetId;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    /** 序列号计数器，节点每次递增 2（偶数：0, 2, 4, ...） */
    private int sequenceCounter = 0;

    private OrderBuilder(String orderId) {
        this.orderId = orderId;
    }

    /**
     * 创建一个新的 OrderBuilder 实例。
     *
     * @param orderId 订单唯一标识符
     * @return 新的 OrderBuilder 实例
     */
    public static OrderBuilder create(String orderId) {
        return new OrderBuilder(orderId);
    }

    /**
     * 设置订单更新 ID，用于区分同一 orderId 的不同版本。
     *
     * @param orderUpdateId 订单更新 ID，从 0 开始递增
     * @return 当前 Builder 实例
     */
    public OrderBuilder orderUpdateId(long orderUpdateId) {
        this.orderUpdateId = orderUpdateId;
        return this;
    }

    /**
     * 设置区域集 ID。
     *
     * @param zoneSetId 区域集标识符
     * @return 当前 Builder 实例
     */
    public OrderBuilder zoneSetId(String zoneSetId) {
        this.zoneSetId = zoneSetId;
        return this;
    }

    /**
     * 添加一个节点到订单中，自动分配偶数序列号。
     *
     * @param nodeId   节点 ID
     * @param x        节点 X 坐标（米）
     * @param y        节点 Y 坐标（米）
     * @param theta    节点朝向（弧度）
     * @param mapId    地图 ID
     * @param released 是否已释放（released=true 表示 AGV 可执行到此节点）
     * @return 当前 Builder 实例
     */
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

    /**
     * 为最后添加的节点附加一个动作。
     *
     * <p>必须在 {@link #addNode} 之后调用，否则抛出 {@link IllegalStateException}。</p>
     *
     * @param actionId     动作 ID
     * @param actionType   动作类型（如 "pick"、"drop"、"charge" 等）
     * @param blockingType 阻塞类型（"HARD"、"SOFT"、"NONE"）
     * @param params       动作参数键值对，可为 {@code null}
     * @return 当前 Builder 实例
     * @throws IllegalStateException 如果尚未添加任何节点
     */
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

    /**
     * 添加一条边到订单中，序列号自动分配为最后一个节点的序列号 +1（奇数）。
     *
     * @param edgeId      边 ID
     * @param startNodeId 起始节点 ID
     * @param endNodeId   终止节点 ID
     * @param maxSpeed    最大允许速度（米/秒）
     * @param released    是否已释放
     * @return 当前 Builder 实例
     */
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

    /**
     * 构建最终的 Order 对象。
     *
     * <p>注意：构建后的 Order 还需要通过 {@link OrderDispatcher#sendOrder} 发送，
     * 发送时框架会自动填充 headerId、timestamp 等消息头字段。</p>
     *
     * @return 构建好的 Order 对象
     */
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
