# VDA5050 Spring Boot Starter — 架构总览

## 1. 项目定位

`vda5050-spring-boot-starter` 是一个 **VDA5050 协议工具集的 Spring Boot Starter**，在一个依赖中同时提供两种运行模式：

| 模式 | 角色 | 场景 |
|------|------|------|
| **Proxy（代理）** | AGV 侧代理 | 你的 FMS 需要接入外部 VDA5050 调度系统 |
| **Server（服务端）** | Master Control | 你的系统作为调度方，向 VDA5050 AGV 下发指令 |

两种模式可以**独立启用**，也可以**同时启用**（例如同时代理一部分车辆并调度另一部分）。

---

## 2. 系统上下文

### 2.1 Proxy 模式

```
┌──────────────────┐
│ 外部调度系统       │  (VDA5050 Master Control)
│ (第三方)          │
└────────┬─────────┘
         │  MQTT: order, instantActions → 
         │  MQTT: ← state, connection
┌────────▼─────────────────────────────────────┐
│  你的 FMS 应用 (Spring Boot)                  │
│  ┌─────────────────────────────────────────┐  │
│  │  vda5050-spring-boot-starter            │  │
│  │  [Proxy 模式]                           │  │
│  │  MQTT 接收 → 状态机 → 回调接口           │  │
│  └──────────────┬──────────────────────────┘  │
│                 ↕ Vda5050ProxyVehicleAdapter   │
│  ┌──────────────▼──────────────────────────┐  │
│  │  FMS 业务逻辑（你实现）                   │  │
│  └──────────────┬──────────────────────────┘  │
└─────────────────┼────────────────────────────┘
                  │ 自有协议 (UDP/TCP)
                  ▼
          ┌───────────────┐
          │  你的无人叉车   │
          └───────────────┘
```

### 2.2 Server 模式

```
┌──────────────────────────────────────────────┐
│  你的调度系统 (Spring Boot)                    │
│  ┌──────────────────────────────────────────┐ │
│  │  调度业务逻辑（你实现）                     │ │
│  └──────────────┬───────────────────────────┘ │
│                 ↕ Vda5050ServerAdapter          │
│  ┌──────────────▼───────────────────────────┐ │
│  │  vda5050-spring-boot-starter             │ │
│  │  [Server 模式]                           │ │
│  │  订单下发 → 状态追踪 → 事件回调           │ │
│  └──────────────┬───────────────────────────┘ │
└─────────────────┼────────────────────────────┘
                  │  MQTT: → order, instantActions
                  │  MQTT: state, connection ←
          ┌───────▼───────┐
          │ VDA5050 AGV    │  (第三方或自有)
          └───────────────┘
```

### 2.3 双模式并行

```
                    ┌──────────────┐
  外部调度系统 ──MQTT──▶│              │──MQTT──▶ VDA5050 AGV-A
  (控制你的车)        │  你的系统     │          (你调度的车)
                    │  [Proxy模式]  │
                    │  [Server模式] │──MQTT──▶ VDA5050 AGV-B
  你的叉车 ◀──UDP────│              │
                    └──────────────┘
```

---

## 3. 模块结构

```
vda5050-spring-boot-starter/
├── src/main/java/com/navasmart/vda5050/
│   │
│   ├── model/                                # [共享] VDA5050 数据模型
│   │   ├── Order.java, AgvState.java, ...    #   27 个 POJO
│   │   └── enums/                            #   枚举类型
│   │
│   ├── mqtt/                                 # [共享] MQTT 通信层
│   │   ├── MqttGateway.java                  #   发布消息
│   │   ├── MqttInboundRouter.java            #   入站消息路由
│   │   ├── MqttTopicResolver.java            #   Topic 路径构建
│   │   └── MqttConnectionManager.java        #   连接生命周期
│   │
│   ├── proxy/                                # [Proxy 模式] AGV 侧代理
│   │   ├── callback/                         #   FMS 回调接口
│   │   │   ├── Vda5050ProxyVehicleAdapter.java
│   │   │   ├── Vda5050ProxyStateProvider.java
│   │   │   ├── VehicleStatus.java
│   │   │   ├── NavigationResult.java
│   │   │   └── ActionResult.java
│   │   ├── statemachine/                     #   订单状态机
│   │   │   ├── ProxyClientState.java
│   │   │   ├── ProxyOrderStateMachine.java
│   │   │   ├── ProxyOrderExecutor.java
│   │   │   └── ProxyNodeProcessor.java
│   │   ├── action/                           #   动作处理器框架
│   │   │   ├── ActionHandler.java
│   │   │   ├── ActionHandlerRegistry.java
│   │   │   └── builtin/
│   │   └── heartbeat/
│   │       ├── ProxyHeartbeatScheduler.java
│   │       └── ProxyConnectionPublisher.java
│   │
│   ├── server/                               # [Server 模式] Master Control
│   │   ├── callback/                         #   调度回调接口
│   │   │   ├── Vda5050ServerAdapter.java
│   │   │   └── Vda5050ServerEventListener.java
│   │   ├── dispatch/                         #   订单下发
│   │   │   ├── OrderDispatcher.java
│   │   │   └── InstantActionSender.java
│   │   ├── tracking/                         #   状态追踪
│   │   │   ├── AgvStateTracker.java
│   │   │   └── OrderProgressTracker.java
│   │   └── heartbeat/
│   │       └── ServerConnectionMonitor.java
│   │
│   ├── vehicle/                              # [共享] 多车辆管理
│   │   ├── VehicleContext.java
│   │   └── VehicleRegistry.java
│   │
│   ├── error/                                # [共享] 错误处理
│   │   ├── ErrorAggregator.java
│   │   └── Vda5050ErrorFactory.java
│   │
│   ├── util/                                 # [共享] 工具类
│   │   ├── TimestampUtil.java
│   │   └── HeaderIdGenerator.java
│   │
│   └── autoconfigure/                        # Spring Boot 自动配置
│       ├── Vda5050Properties.java
│       ├── Vda5050ProxyAutoConfiguration.java
│       └── Vda5050ServerAutoConfiguration.java
```

---

## 4. 核心设计原则

### 4.1 共享协议层，分离业务逻辑

数据模型（`model/`）和 MQTT 通信（`mqtt/`）两种模式共用。业务逻辑在 `proxy/` 和 `server/` 中各自独立。

### 4.2 回调驱动

两种模式都通过回调接口与用户代码交互：

| 模式 | 用户实现的接口 | 用途 |
|------|--------------|------|
| Proxy | `Vda5050ProxyVehicleAdapter` | 收到指令 → 控制车辆 |
| Proxy | `Vda5050ProxyStateProvider` | 提供车辆状态 |
| Server | `Vda5050ServerAdapter` | 订单生成、调度决策 |
| Server | `Vda5050ServerEventListener` | 收到车辆状态变化通知 |

### 4.3 独立启用

通过配置激活对应模式，不配置则不启用：

```yaml
vda5050:
  proxy:
    enabled: true        # 启用 Proxy 模式
    vehicles: [...]
  server:
    enabled: true        # 启用 Server 模式
    vehicles: [...]
```

### 4.4 多车独立、线程安全

每辆车拥有独立的 `VehicleContext`，所有状态变更在锁保护下进行。

---

## 5. 两种模式的 MQTT 方向对比

| Topic | Proxy 模式 | Server 模式 |
|-------|-----------|------------|
| `{prefix}/order` | **订阅**（接收指令） | **发布**（下发指令） |
| `{prefix}/instantActions` | **订阅**（接收即时动作） | **发布**（下发即时动作） |
| `{prefix}/state` | **发布**（上报状态） | **订阅**（接收状态） |
| `{prefix}/connection` | **发布**（上报连接） | **订阅**（监控连接） |
| `{prefix}/factsheet` | **发布**（上报能力） | **订阅**（接收能力） |

---

## 6. 技术栈

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | Spring Boot 4.x (兼容 3.x) | 主体框架 |
| MQTT | Eclipse Paho MQTT v3 (1.2.5) | MQTT 通信，直接使用 Paho 客户端 |
| 序列化 | Jackson | VDA5050 JSON ↔ POJO |
| 日志 | SLF4J + Logback | 标准日志 |
| 构建 | Maven | 发布为 Starter |
| 测试 | JUnit 5 + HiveMQ Embedded | 单元/集成测试 |
| 语言 | Java 17+ | LTS 版本 |

---

## 7. 文档导航

| 文档 | 类别 | 说明 |
|------|------|------|
| 01 架构总览 | 共享 | 本文档 |
| 02 数据模型参考 | 共享 | 27 个 VDA5050 消息类型 |
| 03 MQTT 通信层 | 共享 | Topic、收发、连接管理 |
| 04 Proxy 回调接口 | Proxy | FMS 需实现的接口 |
| 05 Proxy 订单状态机 | Proxy | IDLE/RUNNING/PAUSED |
| 06 Proxy 动作处理器 | Proxy | Blocking Type 调度 |
| 07 Server 接口设计 | Server | 调度系统需实现的接口 |
| 08 Server 订单下发 | Server | 订单生命周期管理 |
| 09 多车辆管理 | 共享 | VehicleContext、线程安全 |
| 10 配置指南 | 共享 | 完整 YAML 参考 |
| 11 错误处理 | 共享 | 错误分类与聚合 |
| 12 集成指南 | 共享 | 两种模式的实操指南 |
