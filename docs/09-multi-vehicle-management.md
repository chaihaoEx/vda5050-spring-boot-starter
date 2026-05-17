# 多车辆管理

VehicleContext 和 VehicleRegistry 是两种模式共享的基础设施。每辆车拥有独立的状态容器。

包路径：`com.navasmart.vda5050.vehicle`

---

## 1. VehicleContext

```java
public class VehicleContext {

    // ===== 身份（不可变）=====
    private final String manufacturer;
    private final String serialNumber;
    private final String vehicleId;  // "{manufacturer}:{serialNumber}"

    // ===== 线程安全 =====
    private final ReentrantLock stateLock = new ReentrantLock();

    // ===== 模式标记 =====
    private boolean proxyMode;   // 是否用作 Proxy 模式
    private boolean serverMode;  // 是否用作 Server 模式

    // ===== Proxy 模式状态 =====
    private AgvState agvState;                      // 本地构建的 AGV 状态
    private Order currentOrder;                     // 当前执行的订单
    private ProxyClientState clientState = ProxyClientState.IDLE;
    private int currentNodeIndex;
    private int nextStopIndex;
    private boolean reachedWaypoint;

    // ===== Server 模式状态 =====
    private AgvState lastReceivedState;             // 最近收到的 AGV 状态
    private Order lastSentOrder;                    // 最近下发的订单
    private String connectionState = "OFFLINE";     // AGV 连接状态
    private long lastSeenTimestamp;                  // 最后收到消息的时间

    // ===== 共享 =====
    private final AtomicInteger stateHeaderId = new AtomicInteger(0);
    private final AtomicInteger connectionHeaderId = new AtomicInteger(0);
    private final AtomicInteger orderHeaderId = new AtomicInteger(0);
    private final AtomicInteger instantActionsHeaderId = new AtomicInteger(0);

    public void lock() { stateLock.lock(); }
    public void unlock() { stateLock.unlock(); }
}
```

---

## 2. VehicleRegistry

由 `Vda5050AutoConfiguration` 注册为 Bean（非 `@Component` 扫描）。启动时 `@PostConstruct init()` 根据 `vda5050.proxy` / `vda5050.server` 的车辆列表与 `enabled` 标志填充注册表；每条记录的模式解析使用 **`Vda5050Properties#isProxyVehicle`** / **`isServerVehicle`**（与运行时监听逻辑一致）。

运行时若要同步 MQTT（连接 Proxy 客户端、订阅 Server Topic），应用层需 **`ApplicationEventPublisher.publishEvent(new VehicleRegistryEvent(...))`**；由 **`VehicleRegistryListener`**（自动配置注册的 Bean）监听事件并编排 **`VehicleRegistry`** 与 **`MqttConnectionManager`**。事件载荷仅包含变更类型、`manufacturer`、`serialNumber`，**不包含** proxy/server 布尔值——后者始终来自当前配置。

```java
public class VehicleRegistry {

    private final ConcurrentHashMap<String, VehicleContext> vehicles = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (properties.getProxy().isEnabled()) {
            for (VehicleConfig vc : properties.getProxy().getVehicles()) {
                applyModesFromProperties(vc.getManufacturer(), vc.getSerialNumber());
            }
        }
        if (properties.getServer().isEnabled()) {
            for (VehicleConfig vc : properties.getServer().getVehicles()) {
                applyModesFromProperties(vc.getManufacturer(), vc.getSerialNumber());
            }
        }
    }

    /** 按当前配置刷新该车的 Proxy/Server 标志 */
    public VehicleContext applyModesFromProperties(String manufacturer, String serialNumber) { ... }

    /** 移除条目（注销事件路径） */
    public VehicleContext remove(String manufacturer, String serialNumber) { ... }

    public VehicleContext getOrCreate(String manufacturer, String serialNumber) { ... }
    public VehicleContext get(String vehicleId) { ... }
    public VehicleContext get(String mfr, String sn) { ... }

    public Collection<VehicleContext> getProxyVehicles() { ... }
    public Collection<VehicleContext> getServerVehicles() { ... }
    public Collection<VehicleContext> getAll() { ... }
}
```

### 2.1 VehicleRegistryEvent（运行时）

- **`VehicleRegistryChangeType`**：`REGISTERED`、`UNREGISTERED`。
- **载荷**：`manufacturer`、`serialNumber`（及 Spring `ApplicationEvent` 的 `source`）。
- **约定**：默认同步监听；调用 `publishEvent` 的线程上会执行 MQTT I/O（失败会向外传播，具体异常类型以实现为准）。

---

## 3. 线程模型

```
Proxy 模式线程：
├── MQTT 入站线程 → ProxyOrderStateMachine（获取车辆锁）
├── ProxyOrderExecutor 定时线程（200ms，遍历 Proxy 车辆）
├── ProxyHeartbeatScheduler 定时线程（1s，发布 AgvState）
└── FMS 异步回调线程 → 完成 Future（获取车辆锁）

Server 模式线程：
├── MQTT 入站线程 → AgvStateTracker（获取车辆锁）
├── ServerConnectionMonitor 定时线程（30s，检查超时）
└── 调度系统线程 → OrderDispatcher.sendOrder()（获取车辆锁）
```

**锁规则**：
- 所有对 VehicleContext 可变状态的访问必须持锁
- 锁粒度为单辆车，不同车辆无竞争
- 持锁时不做 I/O（MQTT 发布、网络请求）

---

## 4. 车辆配置

```yaml
vda5050:
  proxy:
    enabled: true
    vehicles:
      - manufacturer: MyCompany
        serialNumber: forklift01
      - manufacturer: MyCompany
        serialNumber: forklift02
  server:
    enabled: true
    vehicles:
      - manufacturer: ThirdParty
        serialNumber: agv01
      - manufacturer: ThirdParty
        serialNumber: agv02
```

同一辆车可以同时出现在 proxy 和 server 配置中（双模式），但通常不会这样做。

---

## 5. 性能参考

| 指标 | 预估值 |
|------|--------|
| 每辆车内存 | ~50-80KB |
| Proxy 执行循环 | ~1ms/车/轮 |
| Server 状态处理 | ~0.5ms/车/消息 |
| 推荐车辆上限 | 100-200 辆/实例 |
