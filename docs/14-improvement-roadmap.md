# VDA5050 Spring Boot Starter — 改进路线图

## 背景

这是一个新搭建的 VDA5050 通信框架，打包为 Spring Boot Starter 库。代码结构清晰，85 个源文件、60 个测试、文档完善。以下是按优先级排列的改进方向。

---

## 一、Critical — 必须修复

### 1.1 MQTT LWT 多车限制
- **问题**：Paho MQTT 每个 client 只支持一个 LWT。多车 proxy 模式下，应用崩溃时只有第一辆车能发出 CONNECTIONBROKEN，其他车辆的 broker 端无法感知断连。
- **文件**：`mqtt/MqttConnectionManager.java`
- **方案**：每辆车使用独立 MqttClient，或明确文档标注此限制。

### 1.2 超时机制未实现
- **问题**：`navigationTimeoutMs`（300s）和 `actionTimeoutMs`（120s）在 Properties 中已配置，但执行代码中**从未使用**。导航卡死或 action 无响应时不会触发任何超时处理。
- **文件**：`proxy/statemachine/ProxyOrderExecutor.java`、`Vda5050Properties.java`
- **方案**：在 200ms 执行循环中增加超时检测逻辑。

### 1.3 ProxyOrderExecutor 数组越界风险
- **问题**：`order.getNodes().get(ctx.getCurrentNodeIndex())` 没有边界检查，index 越界时抛 IndexOutOfBoundsException。
- **文件**：`proxy/statemachine/ProxyOrderExecutor.java`（约 288、311、317、324 行）
- **方案**：在列表访问前增加边界校验。

### 1.4 Edge Action 从未被执行
- **问题**：VDA5050 协议允许在 edge 上挂载 action，代码初始化时也把 edge action 加入了 actionStates，但执行循环只处理 node action。edge action 会永远停在 WAITING 状态。
- **文件**：`proxy/statemachine/ProxyOrderExecutor.java`
- **方案**：在 edge 遍历阶段增加 edge action 的处理逻辑。

---

## 二、High — 建议修复

### 2.1 不支持 SSL/TLS 加密连接
- MQTT 连接仅支持 tcp/websocket，无加密传输选项。
- 增加 `vda5050.mqtt.ssl.enabled`、keystore/truststore 配置。

### 2.2 配置参数无校验
- 端口范围（1-65535）、超时值（>0）、manufacturer/serialNumber 非空等均无验证。
- 在 `Vda5050Properties` 上增加 `@Validated` + Jakarta Bean Validation 注解。

### 2.3 ServerConnectionMonitor 竞态条件
- `ctx.getLastSeenTimestamp()` 读取时未获取 VehicleContext 的锁（约 58 行），与写入线程存在竞态。
- 读取前应先加锁。

### 2.4 MQTT 重连健壮性不足
- Broker 永久宕机时无断路器机制（无限重连循环）。
- 重连后无法保证 topic 被重新订阅。
- 无重连事件通知机制供用户代码感知。

### 2.5 测试覆盖率缺口
- **缺失场景**：MQTT 重连、多车并发操作、超时处理、畸形消息、adapter 回调异常。
- **测试比例**：~0.7 测试/文件，重度依赖集成测试，单元测试偏少。
- **JaCoCo**：已配置但未设覆盖率阈值。

---

## 三、Medium — 值得优化

### 3.1 可观测性
- 缺少 Micrometer 指标（消息收发量、订单延迟、错误计数、连接状态）。
- 缺少带 orderId/vehicleId 关联 ID 的结构化日志。
- 考虑增加 Spring Boot Actuator Health Indicator 暴露 MQTT 连接状态。

### 3.2 MQTT v5 支持
- 当前使用 Paho MQTT v3（1.2.5）。MQTT v5 新增：共享订阅、消息过期、用户属性、原因码。
- 可考虑 HiveMQ client 或 Paho v5 以面向未来。

### 3.3 入站订单校验
- 收到的 Order 消息无 schema 校验（缺失必填字段、无效序列等）。
- 缺少 node/edge 序列一致性检查。
- 缺少 orderId/orderUpdateId 单调递增校验。

### 3.4 优雅停机
- `@PreDestroy` 发布 OFFLINE，但无法保证进行中的导航/action 完成。
- 应先排空执行队列、完成当前导航，再关闭。

### 3.5 动态车辆注册
- VehicleRegistry 仅在 `@PostConstruct` 从配置初始化。
- 运行时无法添加/移除车辆，需要重启。

### 3.6 事件系统
- 缺少 Spring ApplicationEvent 发布（订单接收、节点到达、错误发生等关键事件）。
- 用户代码可通过事件监听而无需实现所有 adapter 方法。

---

## 四、Low — 未来考虑

### 4.1 VDA5050 v2.1 兼容
- 当前实现对标 v2.0，需关注协议演进。

### 4.2 背压与限流
- 无 MQTT 消息洪水防护。
- 高负载下心跳/状态发布无速率限制。

### 4.3 静态分析强制执行
- SpotBugs/Checkstyle 当前为非阻塞模式（仅报告）。考虑构建失败。

### 4.4 密码安全
- MQTT 密码在配置文件中明文存储。
- 支持 Spring Cloud Config、Vault 或环境变量引用。

---

## 优先级矩阵

| 优先级 | 改进项 | 工作量 | 影响面 |
|--------|--------|--------|--------|
| **Critical** | LWT 多车修复 | 高 | 安全性 |
| **Critical** | 超时机制实现 | 中 | 正确性 |
| **Critical** | 执行器边界检查 | 低 | 稳定性 |
| **Critical** | Edge Action 执行 | 中 | 协议合规 |
| **High** | SSL/TLS 支持 | 中 | 安全性 |
| **High** | 配置校验 | 低 | 健壮性 |
| **High** | ConnectionMonitor 加锁 | 低 | 线程安全 |
| **High** | 重连健壮性 | 中 | 可靠性 |
| **High** | 测试覆盖扩展 | 高 | 质量 |
| **Medium** | 可观测性 | 中 | 运维 |
| **Medium** | 订单校验 | 中 | 正确性 |
| **Medium** | 优雅停机 | 低 | 可靠性 |
| **Medium** | 动态车辆注册 | 中 | 灵活性 |
| **Medium** | 事件系统 | 低 | 扩展性 |
