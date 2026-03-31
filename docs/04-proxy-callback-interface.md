# Proxy 模式：FMS 回调接口设计

本文档定义 Proxy 模式下 FMS 需要实现的回调接口。

包路径：`com.navasmart.vda5050.proxy.callback`

---

## 1. 设计理念

```
VDA5050 协议 (Starter 处理)  →  业务回调  →  FMS 业务逻辑 (你实现)  →  车辆控制
```

FMS 开发者不需要了解 VDA5050 协议细节，只关心"收到什么指令"和"车辆当前什么状态"。

---

## 2. Vda5050ProxyVehicleAdapter — 核心回调接口

```java
/**
 * Proxy 模式车辆适配器。FMS 必须实现此接口。
 * vehicleId 格式为 "{manufacturer}:{serialNumber}"
 */
public interface Vda5050ProxyVehicleAdapter {

    // ===== 导航 =====

    /** 导航到目标节点 */
    CompletableFuture<NavigationResult> onNavigate(
        String vehicleId, Node targetNode, List<Node> waypoints, List<Edge> edges);

    /** 取消当前导航 */
    void onNavigationCancel(String vehicleId);

    // ===== 动作 =====

    /** 执行自定义动作 */
    CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action);

    /** 取消动作 */
    void onActionCancel(String vehicleId, String actionId);

    /** 暂停动作（可选） */
    default void onActionPause(String vehicleId, String actionId) {}

    /** 恢复动作（可选） */
    default void onActionResume(String vehicleId, String actionId) {}

    // ===== 订单生命周期 =====

    /** 暂停车辆 */
    void onPause(String vehicleId);

    /** 恢复车辆 */
    void onResume(String vehicleId);

    /** 取消订单 */
    void onOrderCancel(String vehicleId);

    // ===== 遥控（可选） =====

    default void onTeleopStart(String vehicleId) {}
    default void onTeleopStop(String vehicleId) {}
}
```

---

## 3. Vda5050ProxyStateProvider — 状态提供接口

```java
/**
 * HeartbeatScheduler 按配置间隔（默认 1 秒）调用，获取车辆状态填充 AgvState。
 */
public interface Vda5050ProxyStateProvider {

    /** 获取车辆当前状态 */
    VehicleStatus getVehicleStatus(String vehicleId);

    /** 获取车辆能力信息（factsheetRequest 时调用） */
    Factsheet getFactsheet(String vehicleId);
}
```

---

## 4. 数据传输对象

### 4.1 VehicleStatus

```java
public class VehicleStatus {
    // 位置
    private boolean positionInitialized;
    private double x, y, theta;
    private String mapId;
    private Double localizationScore;

    // 运动
    private double vx, vy, omega;
    private boolean driving;

    // 电池
    private double batteryCharge, batteryVoltage;
    private int batteryHealth;
    private boolean charging;
    private long reach;

    // 安全
    private String eStop = "NONE";
    private boolean fieldViolation;

    // 载荷（可选）
    private List<Load> loads;

    // FMS 侧错误（可选）
    private List<Error> errors;
}
```

### 4.2 NavigationResult

```java
public class NavigationResult {
    private final boolean success;
    private final String failureReason;
    private final String reachedNodeId;

    public static NavigationResult success() { ... }
    public static NavigationResult success(String reachedNodeId) { ... }
    public static NavigationResult failure(String reason) { ... }
}
```

### 4.3 ActionResult

```java
public class ActionResult {
    private final boolean success;
    private final String resultDescription;
    private final String failureReason;

    public static ActionResult success() { ... }
    public static ActionResult success(String resultDescription) { ... }
    public static ActionResult failure(String reason) { ... }
}
```

---

## 5. 回调线程模型

```
MQTT 线程 → MqttInboundRouter → ProxyOrderStateMachine.receiveOrder()
                                         │
                         ScheduledExecutor (200ms) → ProxyOrderExecutor
                                         │
                             ┌────────────┼────────────┐
                             ▼            ▼            ▼
                    onNavigate()  onActionExecute()  onPause()
                   (返回 Future)  (返回 Future)      (同步)
                             │            │
                             ▼            ▼
                    FMS 自有线程完成 CompletableFuture
```

- `onNavigate()` 和 `onActionExecute()` 返回 `CompletableFuture`，FMS 在自己线程完成
- `onPause()` 等同步回调应快速返回
- Starter 不阻塞等待 Future，通过回调处理结果

---

## 6. FMS 实现示例

```java
@Component
public class MyFmsVehicleAdapter implements Vda5050ProxyVehicleAdapter {

    @Autowired
    private UdpCommandSender udpSender;

    @Override
    public CompletableFuture<NavigationResult> onNavigate(
            String vehicleId, Node targetNode,
            List<Node> waypoints, List<Edge> edges) {
        NodePosition pos = targetNode.getNodePosition();
        udpSender.send(vehicleId, buildMoveCommand(pos.getX(), pos.getY(), pos.getTheta()));
        return waitForArrival(vehicleId, targetNode.getNodeId());
    }

    @Override
    public CompletableFuture<ActionResult> onActionExecute(
            String vehicleId, Action action) {
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

---

## 7. 接口注册

声明为 Spring Bean 即可，`Vda5050ProxyAutoConfiguration` 自动发现：

```java
@Component
public class MyFmsVehicleAdapter implements Vda5050ProxyVehicleAdapter { ... }

@Component
public class MyFmsStateProvider implements Vda5050ProxyStateProvider { ... }
```
