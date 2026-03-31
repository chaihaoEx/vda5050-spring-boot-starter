# Server 模式：订单下发与状态追踪

包路径：`com.navasmart.vda5050.server.dispatch`、`com.navasmart.vda5050.server.tracking`

---

## 1. 概述

Server 模式的核心职责：
1. **构建并下发** VDA5050 Order 消息
2. **追踪** AGV 的订单执行进度
3. **检测** 订单完成、失败和异常
4. **监控** AGV 连接状态

---

## 2. 订单构建

### 2.1 Order 构建辅助类

```java
/**
 * VDA5050 订单构建器。简化 Order 消息的组装。
 */
public class OrderBuilder {

    private String orderId;
    private long orderUpdateId = 0;
    private String zoneSetId;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private int sequenceCounter = 0;

    public static OrderBuilder create(String orderId) {
        OrderBuilder b = new OrderBuilder();
        b.orderId = orderId;
        return b;
    }

    /**
     * 添加节点（自动分配 sequenceId）
     */
    public OrderBuilder addNode(String nodeId, double x, double y, double theta,
                                String mapId, boolean released) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setSequenceId(sequenceCounter);
        sequenceCounter++;
        node.setReleased(released);
        node.setNodeDescription("");
        node.setActions(new ArrayList<>());

        NodePosition pos = new NodePosition();
        pos.setX(x); pos.setY(y); pos.setTheta(theta);
        pos.setMapId(mapId); pos.setMapDescription("");
        pos.setAllowedDeviationXY(0.5f);
        pos.setAllowedDeviationTheta(0.1f);
        node.setNodePosition(pos);

        nodes.add(node);
        return this;
    }

    /**
     * 在最近添加的节点上附加动作
     */
    public OrderBuilder withAction(String actionId, String actionType,
                                   String blockingType,
                                   Map<String, String> params) {
        Action action = new Action();
        action.setActionId(actionId);
        action.setActionType(actionType);
        action.setBlockingType(blockingType);
        action.setActionDescription("");
        List<ActionParameter> paramList = new ArrayList<>();
        if (params != null) {
            params.forEach((k, v) -> {
                ActionParameter p = new ActionParameter();
                p.setKey(k); p.setValue(v);
                paramList.add(p);
            });
        }
        action.setActionParameters(paramList);
        nodes.get(nodes.size() - 1).getActions().add(action);
        return this;
    }

    /**
     * 添加两个节点之间的边（自动分配 sequenceId）
     */
    public OrderBuilder addEdge(String edgeId, String startNodeId, String endNodeId,
                                double maxSpeed) {
        Edge edge = new Edge();
        edge.setEdgeId(edgeId);
        // Edge 的 sequenceId 在 node 之间插入
        // 需要在 addNode 之后手动设置或使用自动计算
        edge.setStartNodeId(startNodeId);
        edge.setEndNodeId(endNodeId);
        edge.setMaxSpeed(maxSpeed);
        edge.setReleased(true);
        edge.setEdgeDescription("");
        edge.setActions(new ArrayList<>());
        edges.add(edge);
        return this;
    }

    public Order build() {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(orderUpdateId);
        order.setZoneSetId(zoneSetId != null ? zoneSetId : "");
        order.setNodes(nodes);
        order.setEdges(edges);
        return order;
    }
}
```

### 2.2 使用示例

```java
Order order = OrderBuilder.create("order_001")
    .addNode("start", 0, 0, 0, "warehouse", true)
    .addNode("pickup_A", 10.5, 3.2, 1.57, "warehouse", true)
        .withAction("pick_001", "pick", "HARD", Map.of("loadId", "pallet_42"))
    .addNode("dropoff_B", 20.0, 8.0, 0, "warehouse", true)
        .withAction("place_001", "place", "HARD", Map.of("stationId", "dock_3"))
    .addEdge("e1", "start", "pickup_A", 1.5)
    .addEdge("e2", "pickup_A", "dropoff_B", 1.0)
    .build();

orderDispatcher.sendOrder("MyCompany:agv01", order);
```

---

## 3. AgvStateTracker — 状态追踪

追踪每辆 AGV 的最新状态，检测关键变化并触发回调。

```java
@Component
public class AgvStateTracker {

    /**
     * 处理收到的 AgvState 消息。
     * 由 MqttInboundRouter 在收到 /state 消息时调用。
     */
    public void processState(String vehicleId, AgvState newState) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        AgvState prevState = ctx.getLastReceivedState();

        // 更新最新状态
        ctx.setLastReceivedState(newState);
        ctx.setLastSeenTimestamp(System.currentTimeMillis());

        // 通知完整状态更新
        serverAdapter.onStateUpdate(vehicleId, newState);

        // 检测节点到达
        if (prevState == null || !Objects.equals(
                prevState.getLastNodeId(), newState.getLastNodeId())) {
            serverAdapter.onNodeReached(vehicleId,
                newState.getLastNodeId(), newState.getLastNodeSequenceId());
        }

        // 检测动作状态变化
        detectActionStateChanges(vehicleId, prevState, newState);

        // 检测订单完成
        detectOrderCompletion(vehicleId, ctx, newState);

        // 检测错误变化
        detectErrorChanges(vehicleId, prevState, newState);
    }

    private void detectOrderCompletion(String vehicleId, VehicleContext ctx,
                                       AgvState state) {
        Order sentOrder = ctx.getLastSentOrder();
        if (sentOrder == null) return;

        // 订单完成条件：
        // 1. orderId 匹配
        // 2. nodeStates 为空（所有节点已过）
        // 3. 所有 actionStates 都是 FINISHED 或 FAILED
        // 4. driving = false
        if (sentOrder.getOrderId().equals(state.getOrderId())
            && state.getNodeStates().isEmpty()
            && !state.getDriving()
            && allActionsTerminal(state.getActionStates())) {

            boolean hasFailedActions = state.getActionStates().stream()
                .anyMatch(as -> "FAILED".equals(as.getActionStatus()));

            if (hasFailedActions || hasFatalErrors(state)) {
                serverAdapter.onOrderFailed(vehicleId,
                    state.getOrderId(), state.getErrors());
            } else {
                serverAdapter.onOrderCompleted(vehicleId, state.getOrderId());
            }
            ctx.setLastSentOrder(null);
        }
    }

    private void detectActionStateChanges(String vehicleId,
                                          AgvState prev, AgvState curr) {
        if (prev == null) return;
        Map<String, String> prevStatuses = prev.getActionStates().stream()
            .collect(Collectors.toMap(ActionState::getActionId,
                                     ActionState::getActionStatus));

        for (ActionState as : curr.getActionStates()) {
            String prevStatus = prevStatuses.get(as.getActionId());
            if (prevStatus == null || !prevStatus.equals(as.getActionStatus())) {
                serverAdapter.onActionStateChanged(vehicleId, as);
            }
        }
    }

    private void detectErrorChanges(String vehicleId, AgvState prev, AgvState curr) {
        Set<String> prevErrors = prev == null ? Set.of()
            : prev.getErrors().stream()
                .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
                .collect(Collectors.toSet());

        Set<String> currErrors = curr.getErrors().stream()
            .map(e -> e.getErrorType() + ":" + e.getErrorDescription())
            .collect(Collectors.toSet());

        // 新增错误
        for (Error error : curr.getErrors()) {
            String key = error.getErrorType() + ":" + error.getErrorDescription();
            if (!prevErrors.contains(key)) {
                serverAdapter.onErrorReported(vehicleId, error);
            }
        }

        // 已清除错误
        if (prev != null) {
            for (Error error : prev.getErrors()) {
                String key = error.getErrorType() + ":" + error.getErrorDescription();
                if (!currErrors.contains(key)) {
                    serverAdapter.onErrorCleared(vehicleId, error);
                }
            }
        }
    }
}
```

---

## 4. OrderProgressTracker — 订单进度

```java
@Component
public class OrderProgressTracker {

    /**
     * 查询订单当前进度。
     */
    public OrderProgress getProgress(String vehicleId) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        AgvState state = ctx.getLastReceivedState();
        Order sentOrder = ctx.getLastSentOrder();

        if (sentOrder == null || state == null) {
            return OrderProgress.idle(vehicleId);
        }

        int totalNodes = sentOrder.getNodes().size();
        int remainingNodes = state.getNodeStates().size();
        int completedNodes = totalNodes - remainingNodes;

        long totalActions = sentOrder.getNodes().stream()
            .flatMap(n -> n.getActions().stream()).count();
        long completedActions = state.getActionStates().stream()
            .filter(as -> "FINISHED".equals(as.getActionStatus())).count();

        return new OrderProgress(
            vehicleId,
            sentOrder.getOrderId(),
            totalNodes, completedNodes,
            totalActions, completedActions,
            state.getDriving(),
            state.getLastNodeId(),
            state.getErrors()
        );
    }
}
```

```java
public class OrderProgress {
    private final String vehicleId;
    private final String orderId;
    private final int totalNodes;
    private final int completedNodes;
    private final long totalActions;
    private final long completedActions;
    private final boolean driving;
    private final String currentNodeId;
    private final List<Error> errors;

    /** 完成百分比 */
    public double getCompletionPercent() {
        if (totalNodes == 0) return 100.0;
        return (double) completedNodes / totalNodes * 100.0;
    }
}
```

---

## 5. ServerConnectionMonitor — 连接监控

```java
@Component
public class ServerConnectionMonitor {

    /** 检查间隔（默认 30 秒） */
    @Scheduled(fixedDelayString = "${vda5050.server.connectionCheckMs:30000}")
    public void checkConnections() {
        long now = System.currentTimeMillis();
        long timeout = properties.getServer().getStateTimeoutMs(); // 默认 60 秒

        for (VehicleContext ctx : vehicleRegistry.getServerVehicles()) {
            long lastSeen = ctx.getLastSeenTimestamp();
            if (lastSeen > 0 && (now - lastSeen) > timeout) {
                serverAdapter.onVehicleTimeout(ctx.getVehicleId(),
                    TimestampUtil.format(lastSeen));
            }
        }
    }

    /**
     * 处理 Connection 消息。
     */
    public void processConnection(String vehicleId, Connection connection) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        String prevState = ctx.getConnectionState();
        ctx.setConnectionState(connection.getConnectionState());

        if (!connection.getConnectionState().equals(prevState)) {
            serverAdapter.onConnectionStateChanged(vehicleId,
                connection.getConnectionState());
        }
    }
}
```

---

## 6. 典型调度流程

```
1. 调度系统决定分配任务给 AGV-01
    │
2. 构建 Order（使用 OrderBuilder）
    │
3. orderDispatcher.sendOrder("Company:agv01", order)
    │  → MQTT 发布到 uagv/v2/Company/agv01/order
    │
4. AGV-01 收到订单，开始执行
    │
5. AGV-01 定期发布 AgvState
    │  → Server 收到，AgvStateTracker 处理
    │
6. AgvStateTracker 检测到节点到达
    │  → 回调 onNodeReached()
    │
7. AgvStateTracker 检测到动作完成
    │  → 回调 onActionStateChanged()
    │
8a. 所有节点和动作完成
    │  → 回调 onOrderCompleted()
    │  → 调度系统分配下一个任务
    │
8b. 出现 FATAL 错误
    │  → 回调 onOrderFailed()
    │  → 调度系统告警或重试
    │
9. (可选) 调度系统发送即时动作
    │  instantActionSender.pauseVehicle("Company:agv01")
    │  instantActionSender.cancelOrder("Company:agv01")
```
