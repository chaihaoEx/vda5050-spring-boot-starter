# 集成指南

---

## 1. 添加依赖

```xml
<dependency>
    <groupId>com.navasmart</groupId>
    <artifactId>vda5050-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Part A: Proxy 模式集成

### A.1 配置

```yaml
vda5050:
  mqtt:
    host: mqtt.example.com
    port: 1883
  proxy:
    enabled: true
    vehicles:
      - manufacturer: MyCompany
        serialNumber: forklift01
```

### A.2 实现 Vda5050ProxyVehicleAdapter

```java
@Component
public class MyVehicleAdapter implements Vda5050ProxyVehicleAdapter {

    @Override
    public CompletableFuture<NavigationResult> onNavigate(
            String vehicleId, Node targetNode,
            List<Node> waypoints, List<Edge> edges) {
        NodePosition pos = targetNode.getNodePosition();
        udpSender.send(vehicleId, buildMoveCmd(pos.getX(), pos.getY(), pos.getTheta()));
        return waitForArrival(vehicleId, targetNode.getNodeId());
    }

    @Override
    public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
        return dispatchToUdp(vehicleId, action);
    }

    @Override
    public void onNavigationCancel(String vehicleId) { udpSender.sendStop(vehicleId); }

    @Override
    public void onPause(String vehicleId) { udpSender.sendPause(vehicleId); }

    @Override
    public void onResume(String vehicleId) { udpSender.sendResume(vehicleId); }

    @Override
    public void onOrderCancel(String vehicleId) { udpSender.sendCancel(vehicleId); }
}
```

### A.3 实现 Vda5050ProxyStateProvider

```java
@Component
public class MyStateProvider implements Vda5050ProxyStateProvider {

    @Override
    public VehicleStatus getVehicleStatus(String vehicleId) {
        VehicleStatus s = new VehicleStatus();
        MyState cached = statusCache.getLatest(vehicleId);
        s.setPositionInitialized(true);
        s.setX(cached.getX()); s.setY(cached.getY()); s.setTheta(cached.getHeading());
        s.setMapId("warehouse"); s.setDriving(cached.isMoving());
        s.setBatteryCharge(cached.getBatteryPercent());
        s.setEStop("NONE"); s.setFieldViolation(false);
        return s;
    }

    @Override
    public Factsheet getFactsheet(String vehicleId) { ... }
}
```

### A.4 （可选）注册自定义 ActionHandler

```java
@Component
public class ForkLiftHandler implements ActionHandler {
    @Override
    public Set<String> getSupportedActionTypes() { return Set.of("pick", "place"); }

    @Override
    public CompletableFuture<ActionResult> execute(String vehicleId, Action action) { ... }
}
```

### A.5 验证

用 MQTTX 发送测试 Order 到 `uagv/v2/MyCompany/forklift01/order`：

```json
{
  "headerId": 1,
  "timestamp": "2025-06-11T20:46:59Z",
  "version": "2.0.0",
  "manufacturer": "MyCompany",
  "serialNumber": "forklift01",
  "orderId": "test_001",
  "orderUpdateId": 0,
  "nodes": [
    {
      "nodeId": "start", "sequenceId": 0, "released": true,
      "nodePosition": { "x": 0, "y": 0, "theta": 0, "mapId": "warehouse",
                        "mapDescription": "", "allowedDeviationXY": 1, "allowedDeviationTheta": 3.14 },
      "actions": [], "nodeDescription": ""
    },
    {
      "nodeId": "target", "sequenceId": 2, "released": true,
      "nodePosition": { "x": 10, "y": 5, "theta": 1.57, "mapId": "warehouse",
                        "mapDescription": "", "allowedDeviationXY": 0.3, "allowedDeviationTheta": 0.1 },
      "actions": [
        { "actionId": "pick_001", "actionType": "pick", "blockingType": "HARD",
          "actionParameters": [{"key": "loadId", "value": "pallet_42"}], "actionDescription": "" }
      ],
      "nodeDescription": ""
    }
  ],
  "edges": [
    { "edgeId": "e1", "sequenceId": 1, "released": true, "startNodeId": "start", "endNodeId": "target",
      "maxSpeed": 1.5, "maxHeight": 3, "minHeight": 0, "orientation": 0, "actions": [], "edgeDescription": "" }
  ]
}
```

订阅 `uagv/v2/MyCompany/forklift01/state` 验证 AgvState 输出。

---

## Part B: Server 模式集成

### B.1 配置

```yaml
vda5050:
  mqtt:
    host: mqtt.example.com
    port: 1883
  server:
    enabled: true
    vehicles:
      - manufacturer: ThirdParty
        serialNumber: agv01
```

### B.2 实现 Vda5050ServerAdapter

```java
@Component
public class MyDispatchAdapter implements Vda5050ServerAdapter {

    @Autowired
    private OrderDispatcher orderDispatcher;
    @Autowired
    private InstantActionSender actionSender;

    @Override
    public void onStateUpdate(String vehicleId, AgvState state) {
        fleetView.update(vehicleId, state);
    }

    @Override
    public void onNodeReached(String vehicleId, String nodeId, int sequenceId) {
        log.info("AGV {} 到达 {}", vehicleId, nodeId);
    }

    @Override
    public void onOrderCompleted(String vehicleId, String orderId) {
        log.info("AGV {} 完成订单 {}", vehicleId, orderId);
        // 分配下一个任务
        Task next = taskQueue.poll();
        if (next != null) {
            Order order = buildOrder(next);
            orderDispatcher.sendOrder(vehicleId, order);
        }
    }

    @Override
    public void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {
        log.error("订单失败 {}: {}", orderId, errors);
        alertService.notify(vehicleId, errors);
    }

    @Override
    public void onConnectionStateChanged(String vehicleId, String state) {
        if ("CONNECTIONBROKEN".equals(state)) {
            alertService.vehicleOffline(vehicleId);
        }
    }

    @Override
    public void onErrorReported(String vehicleId, Error error) {
        if ("FATAL".equals(error.getErrorLevel())) {
            alertService.critical(vehicleId, error);
        }
    }
}
```

### B.3 下发订单

```java
@Service
public class MySchedulerService {

    @Autowired
    private OrderDispatcher orderDispatcher;
    @Autowired
    private InstantActionSender actionSender;

    public void assignTask(String vehicleId, Task task) {
        Order order = OrderBuilder.create(task.getId())
            .addNode("start", task.getFromX(), task.getFromY(), 0, "warehouse", true)
            .addNode("target", task.getToX(), task.getToY(), 0, "warehouse", true)
                .withAction(UUID.randomUUID().toString(), "pick", "HARD",
                    Map.of("loadId", task.getLoadId()))
            .addEdge("e1", "start", "target", 1.5)
            .build();

        SendResult result = orderDispatcher.sendOrder(vehicleId, order);
        if (!result.isSuccess()) {
            log.error("订单发送失败: {}", result.getFailureReason());
        }
    }

    public void emergencyStop(String vehicleId) {
        actionSender.pauseVehicle(vehicleId);
    }

    public void cancelTask(String vehicleId) {
        actionSender.cancelOrder(vehicleId);
    }
}
```

### B.4 查询订单进度

```java
@Autowired
private OrderProgressTracker progressTracker;

OrderProgress progress = progressTracker.getProgress("ThirdParty:agv01");
log.info("订单 {} 进度: {}/{}节点, {}%",
    progress.getOrderId(),
    progress.getCompletedNodes(), progress.getTotalNodes(),
    progress.getCompletionPercent());
```

### B.5 验证

1. 启动应用，确认 MQTT 连接并订阅了 AGV 的 state/connection Topics
2. 使用 MQTTX 模拟 AGV 发布 AgvState 到 `uagv/v2/ThirdParty/agv01/state`
3. 确认 `onStateUpdate` 回调被触发
4. 调用 `orderDispatcher.sendOrder()` 并检查 MQTT 发布了 Order 消息

---

## Part C: 双模式并行

```yaml
vda5050:
  mqtt:
    host: mqtt.example.com
  proxy:
    enabled: true
    vehicles:
      - manufacturer: MyCompany
        serialNumber: myForklift01    # 你的叉车，被外部系统调度
  server:
    enabled: true
    vehicles:
      - manufacturer: PartnerCorp
        serialNumber: partnerAgv01    # 合作方的 AGV，你来调度
```

同时实现 `Vda5050ProxyVehicleAdapter`、`Vda5050ProxyStateProvider` 和 `Vda5050ServerAdapter`。

---

## 故障排查

| 症状 | 排查方向 |
|------|---------|
| 收不到订单 (Proxy) | 检查 Topic 中的 manufacturer/serialNumber |
| AgvState 不发布 (Proxy) | 检查 StateProvider 是否抛异常 |
| 收不到 AGV 状态 (Server) | 检查 AGV 是否发布到正确 Topic |
| 订单发送无响应 (Server) | 检查 AGV 是否订阅了 order Topic |
| 动作一直 WAITING (Proxy) | 检查 ActionHandler 是否注册 |

---

## 与外部系统对接清单

- [ ] MQTT Broker 地址和认证
- [ ] 协议版本（v1 / v2）
- [ ] manufacturer 和 serialNumber 值
- [ ] 支持的 actionType 列表及参数
- [ ] 坐标系是否一致
- [ ] mapId 命名约定
- [ ] 是否使用 Trajectory
- [ ] 心跳频率要求
