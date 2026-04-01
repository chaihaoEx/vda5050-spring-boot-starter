# VDA5050 Spring Boot Starter 全面质量优化路线图

## Context

项目经历了两轮 Sonnet 4.6 生成的 PR（PR#1 Critical 修复 + PR#3 路线图改进），合并后代码量增长到 ~100 源文件、97 测试。三份并行审计（MQTT层、Proxy/Server/Vehicle层、Model/测试/架构）共发现 **8 CRITICAL + 6 HIGH + 12 MEDIUM** 问题。最危险的是线程安全（心跳调度器无锁读写）、资源泄露（MQTT连接失败不清理）、以及锁规则违反（adapter回调在锁内执行）。需要分 5 个 PR 按风险优先级逐步修复。

---

## Phase 1 — Critical Safety Fixes（线程安全 + 资源泄露）

**分支**: `fix/phase1-critical-safety`

### 修复项

| # | 问题 | 文件 | 修复方式 |
|---|------|------|---------|
| C1 | ProxyHeartbeatScheduler 无锁读写 AgvState | `proxy/heartbeat/ProxyHeartbeatScheduler.java` | `updateStateFromProvider()` 加锁，snapshot AgvState 后解锁再 publish |
| C2 | MqttClient 连接失败资源泄露 | `mqtt/MqttConnectionManager.java:161-170` | try/finally 包裹 connect()，失败时 vehicleClient.close() |
| C3 | VehicleContext 暴露原始 HashMap | `vehicle/VehicleContext.java:207` | getActionStartTimes() 返回 unmodifiableMap，新增 putActionStartTime/removeActionStartTime 方法 |
| C4 | Model 无防御性拷贝 | 28 个 model POJO | 集合 getter 返回 unmodifiableList，setter 做 new ArrayList<>(list) |
| C5 | Model 无 equals/hashCode | 28 个 model POJO | 基于业务 ID 实现 equals/hashCode（Order=orderId, Node=nodeId+sequenceId 等） |
| C6 | SslUtil 密码 char[] 未清除 | `mqtt/SslUtil.java:35-46` | finally 块中 Arrays.fill(password, '\0') |
| C7 | AutoConfig MqttClient @Bean 原始异常 | `autoconfigure/Vda5050AutoConfiguration.java:73` | catch MqttException → throw IllegalStateException |
| C8 | OrderDispatcher publish 异常未捕获 | `server/dispatch/OrderDispatcher.java:70` + `mqtt/MqttGateway.java` | MqttGateway.publish() 返回 boolean，OrderDispatcher 据此返回 SendResult.failure() |

### 补充
- VehicleContext 新增 `tryLock(long timeout, TimeUnit unit)` 方法
- ProxyOrderExecutor CompletableFuture 回调改用 tryLock(5s) 替代无超时 lock()

**预估工作量**: 3-4 天  
**测试**: Model equals/hashCode 测试、SslUtil char[] 清除测试、心跳并发竞争测试

---

## Phase 2 — High-Severity Correctness Bugs

**分支**: `fix/phase2-high-correctness`  
**依赖**: Phase 1（VehicleContext.tryLock 和锁模式已建立）

| # | 问题 | 文件 | 修复方式 |
|---|------|------|---------|
| H1 | handleFatalError 在锁内调用 adapter | `ProxyOrderExecutor.java:482-489` | 状态变更在锁内，onNavigationCancel 收集到 completionInfo 后锁外执行 |
| H2 | Action 超时用脆弱字符串匹配 | `ProxyOrderExecutor.java:344` | VehicleContext 新增 `Set<String> timedOutActionIds`，替代 "Action timeout" 字符串比较 |
| H3 | AutoConfig @ConditionalOnBean 静默失败 | `Vda5050ProxyAutoConfiguration.java:67` | 添加 SmartInitializingSingleton 校验：proxy.enabled=true 时检查 adapter bean 存在，否则 ERROR 日志 + 抛异常 |
| H4 | resolveProxyClient 静默回退到共享 client | `MqttGateway.java:97-103` | proxy 模式下 client 为 null 时 log.warn + 跳过发布（而非发到错误 client） |
| H5 | AgvStateTracker 在 listener 读取前清除 lastSentOrder | `AgvStateTracker.java:153` | 先 snapshot orderId，callbacks 全部收集完后再 setLastSentOrder(null) |

**预估工作量**: 2 天  
**测试**: adapter 锁外调用测试、缺少 adapter bean 启动失败测试、cancel 后状态清理测试

---

## Phase 3 — Visibility, Configuration & Spec Conformance

**分支**: `fix/phase3-visibility-spec`  
**依赖**: Phase 2

| # | 问题 | 修复方式 |
|---|------|---------|
| M1 | MqttInboundRouter handler 字段非 volatile | 5 个 handler 字段加 `volatile` |
| M2 | MqttHealthIndicator 不区分模式 | 重写 health() 按 proxy/server 模式分别评估 |
| M3 | ObjectMapper 注入未用 @Qualifier | AutoConfig 中注入点加 `@Qualifier("vda5050ObjectMapper")` |
| M4 | cancelOrder blockingType 应为 HARD（VDA5050 spec） | InstantActionSender 修正 |
| M5 | handleCancelOrder 不清理 actionStartTimes | 补充清理 + 新增 cancelledOrderIds 防止回调覆盖已取消订单 |
| M6 | factsheetRequest 在锁内 MQTT publish | ProxyOrderStateMachine 收集 factsheet 后锁外 publish |

**预估工作量**: 1.5-2 天

---

## Phase 4 — ProxyOrderExecutor 拆分 + VehicleContext 锁分离

**分支**: `refactor/phase4-decompose-executor`  
**依赖**: Phase 1-3 全部完成（锁规则、callback 模式已稳定）

### ProxyOrderExecutor 拆分（~500 行 → 3 个类）
- **ProxyOrderExecutor** (保留): execute() 主循环、shutdown/isIdle、handleFatalError、事件发布
- **ProxyNodeActionDispatcher** (新): processNode()、startAction()、allActionsTerminal()、findActionState()
- **ProxyNavigationController** (新): advanceToNextNode()、导航 future 处理、waypoint 构建

### VehicleContext 锁分离
- 现有 `stateLock` → proxy 状态专用
- 新增 `serverStateLock` → server 状态专用（lastReceivedState, lastSentOrder, connectionState, lastSeenTimestamp）
- 新增 `lockServer()` / `unlockServer()` 方法
- AgvStateTracker、ServerConnectionMonitor 改用 server 锁

### Circuit breaker 语义修正
- disconnectForcibly() 后设 automaticReconnect=false 防止 Paho 继续重连

**预估工作量**: 3 天  
**测试**: 所有现有集成测试必须不变通过（回归门禁）

---

## Phase 5 — Model 加固 + 可观测性 + 最终打磨

**分支**: `feat/phase5-hardening-polish`  
**依赖**: Phase 4

| # | 改进 | 说明 |
|---|------|------|
| 1 | AgvState 拷贝构造函数 | 支持心跳调度器 snapshot 模式（锁内拷贝，锁外序列化发布） |
| 2 | 双重完成防护 | VehicleContext 新增 `completedOrderIds` Set，防止 order completion callback 重复触发 |
| 3 | FATAL 预检 | OrderDispatcher.sendOrder() 先检查 errorAggregator.hasFatalError()，拒绝向错误状态车辆下发订单 |
| 4 | Health indicator 重连计数 | 暴露 reconnectAttempts / consecutiveDisconnects 到 health detail |
| 5 | 启动摘要日志 | ApplicationReadyEvent listener 打印模式、车辆数、SSL 状态 |
| 6 | 测试去 Thread.sleep | ProxyOrderFlowTest 中 3 处 Thread.sleep(1000) 改为 polling + timeout |

**预估工作量**: 2 天

---

## 总览

| Phase | 名称 | 修复数 | 工作量 | 关键文件 |
|-------|------|--------|--------|---------|
| 1 | Critical Safety | 8 CRITICAL | 3-4天 | HeartbeatScheduler, VehicleContext, 28 models, SslUtil, MqttGateway, OrderDispatcher |
| 2 | High Correctness | 5 HIGH | 2天 | ProxyOrderExecutor, AgvStateTracker, MqttGateway, ProxyAutoConfig |
| 3 | Visibility + Spec | 6 MEDIUM | 1.5-2天 | MqttInboundRouter, HealthIndicator, AutoConfig, InstantActionSender |
| 4 | Decompose + Lock Split | 3 MEDIUM (结构性) | 3天 | ProxyOrderExecutor→3类, VehicleContext, MqttConnectionManager |
| 5 | Hardening + Polish | 6 项 | 2天 | AgvState, OrderDispatcher, HealthIndicator, 测试 |
| **合计** | | **28 项** | **~12 天** | |

## 验证方式

每个 Phase 完成后：
1. `mvn verify` — 全部测试通过 + SpotBugs/Checkstyle clean
2. 新增测试覆盖本 Phase 修复点
3. PR review 后合并到 main
4. Phase 5 完成后打 v1.2.0 tag
