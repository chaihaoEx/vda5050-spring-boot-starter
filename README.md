# VDA5050 Spring Boot Starter

[![CI](https://github.com/chaihaoEx/vda5050-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/chaihaoEx/vda5050-spring-boot-starter/actions/workflows/ci.yml)

一个为 Java/Spring Boot 生态设计的 VDA5050 协议工具库，提供 **Proxy（代理）** 和 **Server（服务端）** 两种运行模式，帮助开发者快速对接 VDA5050 协议的自动导引车辆（AGV）调度系统。

## 背景

[VDA5050](https://github.com/VDA5050/VDA5050) 是由德国汽车工业协会（VDA）和德国机械设备制造业联合会（VDMA）联合制定的 AGV 通信接口标准，旨在统一不同厂商 AGV 与调度系统之间的通信协议。协议基于 MQTT，定义了订单下发、状态上报、即时动作等完整的交互流程。

本项目的目标是让 Spring Boot 开发者**无需深入了解 VDA5050 协议细节**，通过引入一个 Starter 依赖 + 实现业务回调接口，即可完成 VDA5050 协议的对接。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.navasmart</groupId>
    <artifactId>vda5050-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 MQTT 和车辆

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
  server:
    enabled: true
    vehicles:
      - manufacturer: ThirdParty
        serialNumber: agv01
```

### 3. 实现回调接口

**Proxy 模式** — 实现两个接口，接收 VDA5050 指令并控制你的车辆：

```java
@Component
public class MyVehicleAdapter implements Vda5050ProxyVehicleAdapter {
    @Override
    public CompletableFuture<NavigationResult> onNavigate(
            String vehicleId, Node targetNode, List<Node> waypoints, List<Edge> edges) {
        // 控制你的车辆导航到目标节点
        return CompletableFuture.completedFuture(NavigationResult.success());
    }

    @Override
    public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
        // 执行动作（如 pick、drop、charge 等）
        return CompletableFuture.completedFuture(ActionResult.success());
    }

    // ... 其他回调方法（onPause, onResume, onOrderCancel 等）
}

@Component
public class MyStateProvider implements Vda5050ProxyStateProvider {
    @Override
    public VehicleStatus getVehicleStatus(String vehicleId) {
        // 返回车辆当前状态（位置、电池、速度等）
    }

    @Override
    public Factsheet getFactsheet(String vehicleId) {
        // 返回车辆能力描述
    }
}
```

**Server 模式** — 实现一个接口接收 AGV 状态，注入 API 下发指令：

```java
@Component
public class MyServerAdapter implements Vda5050ServerAdapter {
    @Override
    public void onStateUpdate(String vehicleId, AgvState state) {
        // 处理 AGV 状态更新
    }

    @Override
    public void onOrderCompleted(String vehicleId, String orderId) {
        // 订单执行完成
    }
}

@Service
public class MyDispatchService {
    @Autowired private OrderDispatcher orderDispatcher;
    @Autowired private InstantActionSender actionSender;

    public void dispatchOrder() {
        Order order = OrderBuilder.create("order-001")
            .addNode("node-1", 0.0, 0.0, 0.0, "map-1", true)
            .withAction("action-1", "pick", "HARD", Map.of("station", "S1"))
            .addEdge("edge-1", "node-1", "node-2", 1.5, true)
            .addNode("node-2", 10.0, 0.0, 0.0, "map-1", true)
            .build();
        orderDispatcher.sendOrder("ThirdParty:agv01", order);
    }
}
```

## 两种模式

### Proxy 模式（AGV 侧代理）

你的系统已有自己的车队和控制协议，但需要接入外部的 VDA5050 调度系统。Proxy 模式作为中间层：

```
外部 VDA5050 调度系统 ──MQTT──▶ [Proxy 模式] ──回调──▶ 你的 FMS ──自有协议──▶ 你的车辆
```

- 接收 VDA5050 Order / InstantActions
- 通过回调接口通知你的业务系统
- 自动管理订单状态机（IDLE → RUNNING ↔ PAUSED）、动作调度、心跳上报

### Server 模式（Master Control）

你的系统需要作为调度方，控制符合 VDA5050 标准的 AGV：

```
你的调度系统 ──调用 API──▶ [Server 模式] ──MQTT──▶ VDA5050 AGV
```

- 提供 `OrderDispatcher`、`InstantActionSender` 等 API 下发指令
- 自动追踪 AGV 状态、检测订单完成/失败
- 通过事件回调通知你的调度逻辑

两种模式可独立启用，也可同时运行。

## 技术栈

- Java 17+
- Spring Boot 4.0.5
- Eclipse Paho MQTT v3 (1.2.5)
- Jackson

## 构建与测试

```bash
./mvnw clean compile       # 编译
./mvnw verify              # 编译 + 测试 + SpotBugs + Checkstyle + JaCoCo
./mvnw test -Dtest=ProxyOrderFlowTest  # 运行单个测试类
```

项目包含 60 个测试（单元测试 + 集成测试），集成测试使用 Moquette 嵌入式 MQTT Broker，无需外部依赖。

## 配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `vda5050.mqtt.host` | `localhost` | MQTT Broker 地址 |
| `vda5050.mqtt.port` | `1883` | MQTT Broker 端口 |
| `vda5050.mqtt.transport` | `tcp` | 传输协议（`tcp` / `websocket`） |
| `vda5050.mqtt.interfaceName` | `uagv` | VDA5050 Topic 接口名 |
| `vda5050.mqtt.majorVersion` | `v2` | VDA5050 Topic 版本号 |
| `vda5050.proxy.enabled` | `false` | 启用 Proxy 模式 |
| `vda5050.proxy.heartbeatIntervalMs` | `1000` | 状态心跳间隔（毫秒） |
| `vda5050.proxy.orderLoopIntervalMs` | `200` | 订单执行循环间隔（毫秒） |
| `vda5050.server.enabled` | `false` | 启用 Server 模式 |
| `vda5050.server.stateTimeoutMs` | `60000` | AGV 状态超时阈值（毫秒） |

完整配置请参考 [10 配置指南](docs/10-configuration-guide.md)。

## 文档

| 文档 | 说明 |
|------|------|
| [01 架构总览](docs/01-architecture-overview.md) | 双模式架构、模块结构、数据流 |
| [02 数据模型参考](docs/02-data-model-reference.md) | 27 个 VDA5050 消息类型定义 |
| [03 MQTT 通信层](docs/03-mqtt-communication.md) | Topic 结构、收发、连接管理 |
| [04 Proxy 回调接口](docs/04-proxy-callback-interface.md) | FMS 需实现的接口 |
| [05 Proxy 订单状态机](docs/05-proxy-order-state-machine.md) | 订单执行引擎 |
| [06 Proxy 动作处理器](docs/06-proxy-action-handler.md) | Blocking Type 调度 |
| [07 Server 接口设计](docs/07-server-interface-design.md) | 调度系统接口与 API |
| [08 Server 订单下发](docs/08-server-order-dispatch.md) | 状态追踪、进度查询 |
| [09 多车辆管理](docs/09-multi-vehicle-management.md) | VehicleContext、线程安全 |
| [10 配置指南](docs/10-configuration-guide.md) | 完整 YAML 参考 |
| [11 错误处理](docs/11-error-handling.md) | 错误分类与聚合 |
| [12 集成指南](docs/12-integration-guide.md) | 两种模式的实操示例 |
| [13 Proxy 快速集成](docs/13-proxy-quick-start.md) | 10 分钟完成 Proxy 模式集成 |

## 参考项目

- **[NVIDIA-ISAAC-ROS/isaac_ros_cloud_control](https://github.com/NVIDIA-ISAAC-ROS/isaac_ros_cloud_control)** — NVIDIA Isaac ROS 的 VDA5050 客户端实现（C++ / Python / ROS 2）。本项目的数据模型、订单状态机、动作调度逻辑和 MQTT 通信层设计均参考了该项目的实现。

- **[VDA5050/VDA5050](https://github.com/VDA5050/VDA5050)** — VDA5050 协议官方规范文档，定义了所有消息类型、Topic 结构和交互流程。

## License

TBD
