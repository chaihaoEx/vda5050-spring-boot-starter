# Proxy 模式快速集成手册

本文档帮助你在 **10 分钟内**将 VDA5050 Proxy 模式集成到你的 Spring Boot 应用中。

Proxy 模式的作用：作为你的车辆和外部 VDA5050 调度系统之间的协议桥接层。

```
外部 VDA5050 调度系统 ──MQTT──▶ [本 Starter / Proxy 模式] ──回调──▶ 你的代码 ──自有协议──▶ 你的车辆
```

---

## 前置条件

- JDK 17+
- Spring Boot 4.x 应用
- 可访问的 MQTT Broker（如 EMQX、Mosquitto）

---

## 第一步：添加依赖

```xml
<dependency>
    <groupId>com.navasmart</groupId>
    <artifactId>vda5050-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 第二步：配置

在 `application.yml` 中添加最小配置：

```yaml
vda5050:
  mqtt:
    host: your-mqtt-broker.com   # MQTT Broker 地址
    port: 1883
  proxy:
    enabled: true                # 启用 Proxy 模式
    vehicles:
      - manufacturer: MyCompany  # 对应 VDA5050 Topic 中的 manufacturer
        serialNumber: forklift01 # 对应 VDA5050 Topic 中的 serialNumber
```

启动后，Starter 会自动：
- 连接 MQTT Broker
- 订阅 `uagv/v2/MyCompany/forklift01/order` 和 `.../instantActions`
- 发布 ONLINE 连接状态到 `.../connection`
- 每秒发布 AgvState 到 `.../state`

---

## 第三步：实现 VehicleAdapter

这是核心接口，调度系统的所有指令都通过这里转发给你。创建一个 Spring Bean 实现它：

```java
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.proxy.callback.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class MyVehicleAdapter implements Vda5050ProxyVehicleAdapter {

    /**
     * 收到导航指令 —— 订单中当前节点动作完成后触发。
     * 你需要控制车辆移动到 targetNode，到达后 complete Future。
     */
    @Override
    public CompletableFuture<NavigationResult> onNavigate(
            String vehicleId, Node targetNode,
            List<Node> waypoints, List<Edge> edges) {

        // TODO: 调用你的车辆控制接口，驱动车辆移动到 targetNode
        //   - targetNode.getNodePosition() 包含目标坐标 (x, y, theta, mapId)
        //   - edges 包含路径约束（最大速度等）
        //   - 到达后返回 success，失败返回 failure

        return CompletableFuture.completedFuture(NavigationResult.success());
    }

    /**
     * 取消导航 —— 发生 FATAL 错误时触发，应立即停车。
     */
    @Override
    public void onNavigationCancel(String vehicleId) {
        // TODO: 停止车辆移动
    }

    /**
     * 执行动作 —— 到达节点后，按 BlockingType 调度执行节点上的动作。
     * 常见 actionType: pick, drop, startCharging, stopCharging 等。
     */
    @Override
    public CompletableFuture<ActionResult> onActionExecute(
            String vehicleId, Action action) {

        // TODO: 根据 action.getActionType() 执行对应操作
        //   - action.getActionParameters() 包含动作参数
        //   - 完成后返回 success，失败返回 failure

        return CompletableFuture.completedFuture(ActionResult.success());
    }

    /**
     * 取消动作 —— 订单取消或中止时触发。
     */
    @Override
    public void onActionCancel(String vehicleId, String actionId) {
        // TODO: 取消指定动作
    }

    /**
     * 暂停 —— 收到 startPause 即时动作时触发，应暂停所有运动。
     */
    @Override
    public void onPause(String vehicleId) {
        // TODO: 暂停车辆
    }

    /**
     * 恢复 —— 收到 stopPause 即时动作时触发，应恢复运动。
     */
    @Override
    public void onResume(String vehicleId) {
        // TODO: 恢复车辆
    }

    /**
     * 取消订单 —— 收到 cancelOrder 即时动作时触发。
     */
    @Override
    public void onOrderCancel(String vehicleId) {
        // TODO: 停止当前订单相关的所有操作
    }
}
```

> **vehicleId 格式**：`"manufacturer:serialNumber"`，例如 `"MyCompany:forklift01"`。

---

## 第四步：实现 StateProvider

此接口每秒被调用一次（默认 1000ms），返回值自动发布为 VDA5050 State 消息：

```java
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.proxy.callback.*;
import org.springframework.stereotype.Component;

@Component
public class MyStateProvider implements Vda5050ProxyStateProvider {

    /**
     * 返回车辆当前状态 —— 每个心跳周期（默认 1 秒）调用一次。
     * 必须填充：mapId、x、y、theta、batteryCharge、eStop。
     */
    @Override
    public VehicleStatus getVehicleStatus(String vehicleId) {
        VehicleStatus status = new VehicleStatus();

        // 位置（必填）
        status.setPositionInitialized(true);
        status.setX(1.0);           // 从你的车辆获取实际坐标
        status.setY(2.0);
        status.setTheta(0.0);       // 朝向角（弧度）
        status.setMapId("map-1");

        // 电池（必填）
        status.setBatteryCharge(80.0);  // 0.0 ~ 100.0
        status.setCharging(false);

        // 安全状态（必填）
        status.setEStop("NONE");       // NONE / AUTOACK / MANUAL / REMOTE
        status.setFieldViolation(false);

        // 运动状态
        status.setDriving(false);      // 是否正在移动
        status.setVx(0.0);            // 前向速度 (m/s)

        return status;
    }

    /**
     * 返回车辆能力描述 —— 仅在收到 factsheetRequest 即时动作时调用。
     */
    @Override
    public Factsheet getFactsheet(String vehicleId) {
        // 可返回 null（不发布 factsheet）
        return null;
    }
}
```

---

## 第五步：验证

启动你的应用后，通过 MQTT 工具验证：

### 1. 检查连接状态

订阅 connection topic，应收到 ONLINE：

```bash
mosquitto_sub -h your-mqtt-broker.com -t "uagv/v2/MyCompany/forklift01/connection" -v
```

预期输出：
```json
{"connectionState":"ONLINE","timestamp":"2024-01-01T00:00:00.000Z",...}
```

### 2. 检查心跳

订阅 state topic，每秒应收到一条 AgvState：

```bash
mosquitto_sub -h your-mqtt-broker.com -t "uagv/v2/MyCompany/forklift01/state" -v
```

### 3. 发送测试订单

发布一个最简 Order（单节点，无动作）：

```bash
mosquitto_pub -h your-mqtt-broker.com \
  -t "uagv/v2/MyCompany/forklift01/order" \
  -m '{
    "headerId": 1,
    "timestamp": "2024-01-01T00:00:00.000Z",
    "version": "2.0.0",
    "manufacturer": "MyCompany",
    "serialNumber": "forklift01",
    "orderId": "test-order-001",
    "orderUpdateId": 0,
    "nodes": [{
      "nodeId": "node-1",
      "sequenceId": 0,
      "released": true,
      "nodePosition": {"x": 5.0, "y": 3.0, "theta": 0.0, "mapId": "map-1"},
      "actions": []
    }],
    "edges": []
  }'
```

验证：你的 `onNavigate` 方法应被调用，State 消息中的 `orderId` 应变为 `"test-order-001"`。

### 4. 测试即时动作

发送暂停指令：

```bash
mosquitto_pub -h your-mqtt-broker.com \
  -t "uagv/v2/MyCompany/forklift01/instantActions" \
  -m '{
    "headerId": 1,
    "timestamp": "2024-01-01T00:00:00.000Z",
    "version": "2.0.0",
    "manufacturer": "MyCompany",
    "serialNumber": "forklift01",
    "instantActions": [{
      "actionType": "startPause",
      "actionId": "pause-001",
      "blockingType": "NONE"
    }]
  }'
```

验证：你的 `onPause` 方法应被调用，State 中的 `paused` 应变为 `true`。

---

## 进阶：自定义 ActionHandler

如果你有特定的 actionType 需要独立处理（而非全部走 `onActionExecute`），可以注册 ActionHandler：

```java
@Component
public class ChargingHandler implements ActionHandler {

    @Override
    public Set<String> getSupportedActionTypes() {
        return Set.of("startCharging", "stopCharging");
    }

    @Override
    public CompletableFuture<ActionResult> execute(String vehicleId, Action action) {
        // 处理充电相关动作
        return CompletableFuture.completedFuture(ActionResult.success());
    }
}
```

ActionHandler 优先级高于 `VehicleAdapter.onActionExecute()`，注册为 Spring Bean 即自动生效。

---

## 配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `vda5050.mqtt.host` | `localhost` | MQTT Broker 地址 |
| `vda5050.mqtt.port` | `1883` | MQTT Broker 端口 |
| `vda5050.mqtt.transport` | `tcp` | 传输协议（`tcp` / `websocket`） |
| `vda5050.mqtt.username` | 空 | MQTT 认证用户名 |
| `vda5050.mqtt.password` | 空 | MQTT 认证密码 |
| `vda5050.mqtt.interfaceName` | `uagv` | Topic 前缀接口名 |
| `vda5050.mqtt.majorVersion` | `v2` | Topic 前缀版本号 |
| `vda5050.proxy.enabled` | `false` | **必须设为 true** |
| `vda5050.proxy.heartbeatIntervalMs` | `1000` | 状态上报间隔（毫秒） |
| `vda5050.proxy.orderLoopIntervalMs` | `200` | 订单执行循环间隔（毫秒） |
| `vda5050.proxy.navigationTimeoutMs` | `300000` | 导航超时（5 分钟） |
| `vda5050.proxy.actionTimeoutMs` | `120000` | 动作超时（2 分钟） |

---

## 常见问题

### 应用启动报 "Connection refused"

MQTT Broker 不可达。检查 `vda5050.mqtt.host` 和 `port` 配置，确认 Broker 已启动。

### onNavigate / onActionExecute 没有被调用

1. 检查 Order 消息的 `manufacturer` 和 `serialNumber` 是否与配置中的车辆一致
2. 检查 MQTT Topic 格式：`uagv/v2/{manufacturer}/{serialNumber}/order`
3. 确认 `vda5050.proxy.enabled=true`
4. 确认 VehicleAdapter 和 StateProvider 都注册为了 Spring Bean（`@Component`）

### State 消息不发布

1. 确认 `Vda5050ProxyStateProvider` 已注册为 Spring Bean
2. `getVehicleStatus()` 不要返回 `null`
3. 检查 MQTT 连接是否正常（查看启动日志中的 "Connected to MQTT broker" 信息）

### 订单被拒绝（日志显示 "Cannot accept order"）

- PAUSED 状态下不接受新订单 —— 先发送 `stopPause` 恢复
- RUNNING 状态下只接受相同 `orderId` 的订单更新

### 如何支持多辆车？

在配置中添加多个 vehicles，VehicleAdapter 通过 `vehicleId` 参数区分：

```yaml
vda5050:
  proxy:
    enabled: true
    vehicles:
      - manufacturer: MyCompany
        serialNumber: forklift01
      - manufacturer: MyCompany
        serialNumber: forklift02
```

---

## 下一步

- [04 Proxy 回调接口](04-proxy-callback-interface.md) — 接口设计详解
- [05 Proxy 订单状态机](05-proxy-order-state-machine.md) — 状态转换规则
- [06 Proxy 动作处理器](06-proxy-action-handler.md) — BlockingType 调度逻辑
- [10 配置指南](10-configuration-guide.md) — 完整配置参考
- [12 集成指南](12-integration-guide.md) — 详细集成示例
