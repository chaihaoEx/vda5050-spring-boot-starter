# VDA5050 Spring Boot Starter 全面质量优化路线图

## Context

项目经历了两轮 Sonnet 4.6 生成的 PR（PR#1 Critical 修复 + PR#3 路线图改进），合并后代码量增长到 ~100 源文件、97 测试。三份并行审计（MQTT层、Proxy/Server/Vehicle层、Model/测试/架构）共发现 **8 CRITICAL + 6 HIGH + 12 MEDIUM** 问题。最危险的是线程安全（心跳调度器无锁读写）、资源泄露（MQTT连接失败不清理）、以及锁规则违反（adapter回调在锁内执行）。需要分 5 个 PR 按风险优先级逐步修复。

---

## Phase 1 — Critical Safety Fixes（线程安全 + 资源泄露）✅ 已完成

**分支**: `fix/phase1-critical-safety`

### 修复项

| # | 问题 | 文件 | 修复方式 |
|---|------|------|---------|
| C1 | ProxyHeartbeatScheduler 无锁读写 AgvState | `proxy/heartbeat/ProxyHeartbeatScheduler.java` | `publishHeartbeat()` 整体加锁（含 `updateStateFromProvider()` + header 字段设置），`copyAgvState()` snapshot 后解锁再 publish |
| C2 | MqttClient 连接失败资源泄露 | `mqtt/MqttConnectionManager.java` | try/catch 包裹 connect()，失败时 vehicleClient.close() 后 re-throw |
| C3 | VehicleContext 暴露原始 HashMap | `vehicle/VehicleContext.java` | getActionStartTimes() 返回 unmodifiableMap，新增 putActionStartTime/removeActionStartTime/clearActionStartTimes 方法，ProxyOrderExecutor 6 处调用点同步更新 |
| C4 | Model 无防御性拷贝 | 10 个含 List 字段的 model POJO | **仅 setter 做防御性拷贝**（`new ArrayList<>(list)`），getter 保持可变（避免破坏内部代码直接操作列表的模式） |
| C5 | Model 无 equals/hashCode | 11 个有业务 ID 的 model POJO | 基于业务 ID 实现 equals/hashCode（Order=orderId+orderUpdateId, Node=nodeId+sequenceId, Action=actionId 等），其余 17 个无天然 ID 的 POJO 推迟到 Phase 5 |
| C6 | SslUtil 密码 char[] 未清除 | `mqtt/SslUtil.java` | 密码存入局部 char[] 变量，finally 块中 Arrays.fill(password, '\0') |
| C7 | AutoConfig MqttClient @Bean 原始异常 | `autoconfigure/Vda5050AutoConfiguration.java` | catch MqttException → throw IllegalStateException，移除方法签名的 throws 声明 |
| C8 | OrderDispatcher + InstantActionSender publish 异常未捕获 | `MqttGateway.java` + `OrderDispatcher.java` + `InstantActionSender.java` | MqttGateway.publish() 及 5 个便捷方法返回 boolean，OrderDispatcher 和 InstantActionSender 据此返回 SendResult.failure() |

### 补充
- VehicleContext 新增 `tryLock(long timeout, TimeUnit unit)` 方法
- ProxyOrderExecutor CompletableFuture 回调（action callback + navigation callback）改用 tryLock(5s) 替代无超时 lock()，超时时记录 ERROR 日志并跳过

### 测试新增（71 个测试用例）
- `VehicleContextTest` — tryLock 超时、unmodifiable map、put/remove/clear 方法（6 tests）
- `ModelDefensiveCopyTest` — 10 个 POJO setter 防御性拷贝验证（10 tests）
- `ModelEqualsHashCodeTest` — 11 个 POJO equals/hashCode 契约验证（55 tests）

---

## Phase 2 — High-Severity Correctness Bugs ✅ 已完成

**分支**: `fix/phase2-high-correctness`

### 修复项

| # | 问题 | 文件 | 修复方式 |
|---|------|------|---------|
| H1 | handleFatalError + onActionCancel 在锁内调用 adapter | `ProxyOrderExecutor.java` | executeForVehicle 重构为 if/else if 链消除 early return，handleFatalError 改为纯状态变更，onNavigationCancel 和 onActionCancel 均在锁释放后执行 |
| H2 | Action 超时用脆弱字符串匹配 | `ProxyOrderExecutor.java`, `VehicleContext.java` | VehicleContext 新增 `Set<String> timedOutActionIds` 及 add/is/remove/clear 方法，超时时写入集合、回调中检查集合替代字符串比较 |
| H3 | AutoConfig @ConditionalOnBean 静默失败 | `Vda5050ProxyAutoConfiguration.java` | 添加 SmartInitializingSingleton proxyAdapterValidator，启动后校验必需 Bean 存在，缺失则抛 BeanCreationException |
| H4 | resolveProxyClient 静默回退到共享 client | `MqttGateway.java` | resolveProxyClient 返回 null + log.warn，publishState/publishConnection/publishFactsheet 检查 null 后 skip |
| H5 | AgvStateTracker orderId 来源不权威 | `AgvStateTracker.java` | collectOrderCompletion 中 orderId 改为从 sentOrder.getOrderId() 获取 |

### 测试新增（12 个测试用例）
- `Phase2CorrectnessTest` — H1 锁外回调验证 + H2 timedOutActionIds 追踪（4 tests）
- `VehicleContextTest` — timedOutActionIds add/remove/clear + clearActionStartTimes 联动清除（4 tests）
- `ProxyAutoConfigurationTest` — H3 缺少 adapter/provider 启动失败 + 正常启动（3 new tests）
- `MqttGatewayProxyClientTest` — H4 proxy client 缺失返回 false + 正常发布（4 tests）

---

## Phase 3 — Visibility, Configuration & Spec Conformance ✅ 已完成

**分支**: `fix/phase3-visibility-spec`  
**依赖**: Phase 2

### 修复项

| # | 问题 | 文件 | 修复方式 |
|---|------|------|---------|
| M1 | MqttInboundRouter handler 字段非 volatile | `MqttInboundRouter.java` | 5 个 handler 字段加 `volatile` |
| M2 | MqttHealthIndicator 不区分模式 | `MqttHealthIndicator.java` | 重写 health() 按 proxy/server 模式分别评估，Server 模式检查 sharedClient，Proxy 模式检查 per-vehicle client，任一 DOWN 则整体 DOWN |
| M3 | ObjectMapper 注入未用 @Qualifier | `Vda5050AutoConfiguration.java` | 3 个注入点（mqttGateway, mqttInboundRouter, mqttConnectionManager）加 `@Qualifier("vda5050ObjectMapper")` |
| M4 | cancelOrder blockingType 应为 HARD（VDA5050 spec） | `InstantActionSender.java` | `sendBuiltinAction` 对 cancelOrder 使用 `BlockingType.HARD`，其余保持 NONE |
| M5 | handleCancelOrder 不清理 actionStartTimes | `ProxyOrderStateMachine.java`, `VehicleContext.java` | handleCancelOrder 补充 `clearActionStartTimes()` + `setNavigationStartTime(0)`；VehicleContext 新增 `cancelledOrderIds` Set 及 add/is/remove/clear 方法；receiveOrder 时清理 cancelledOrderIds |
| M6 | factsheetRequest 在锁内 MQTT publish | `ProxyOrderStateMachine.java` | `processInstantAction` 改为返回 Factsheet，`receiveInstantActions` 在锁外执行 `publishFactsheet` |

### 测试新增（11 个测试用例）
- `Phase3VisibilitySpecTest` — M4 cancelOrder HARD blockingType + pauseVehicle NONE（2 tests）、M5 清理 actionStartTimes/navigationStartTime + cancelledOrderIds 跟踪 + 新订单清理（3 tests）、M6 锁外发布 factsheet + actionStatus FINISHED（2 tests）
- `MqttHealthIndicatorTest` — M2 server-only/proxy-only/mixed 模式健康评估（5 tests，含原有 2 个重写）
- `VehicleContextTest` — cancelledOrderIds add/remove/clear（3 tests）

---

## Phase 4 — ProxyOrderExecutor 拆分 + VehicleContext 锁分离 ✅ 已完成

**分支**: `refactor/phase4-decompose-executor`  
**依赖**: Phase 1-3 全部完成（锁规则、callback 模式已稳定）

### ProxyOrderExecutor 拆分（~565 行 → 3 个类）
- **ProxyOrderExecutor** (保留): execute() 主循环、shutdown/isIdle、handleFatalError、事件发布
- **ProxyNodeActionDispatcher** (新): processNode()、startAction()、allActionsTerminal()、findActionState()、NodeProcessResult
- **ProxyNavigationController** (新): advanceToNextNode()、导航 future 处理、waypoint 构建、edge action 启动

### VehicleContext 锁分离
- 现有 `stateLock` → proxy 状态专用
- 新增 `serverStateLock` → server 状态专用（lastReceivedState, lastSentOrder, connectionState, lastSeenTimestamp）
- 新增 `lockServer()` / `unlockServer()` / `tryLockServer()` 方法
- AgvStateTracker、ServerConnectionMonitor 改用 server 锁

### Circuit breaker 语义修正
- disconnectForcibly() 后调用 client.close(true) 防止 Paho 继续重连（shared client + per-vehicle client）

### 测试新增（14 个测试用例）
- `Phase4DecomposeTest` — 分解后 execute() 端到端验证（4 tests）、server 锁独立性（2 tests）、AgvStateTracker/ServerConnectionMonitor 使用 server 锁（2 tests）
- `VehicleContextTest` — tryLockServer 获取/超时/独立性（3 tests）

---

## Phase 5 — Model 加固 + 可观测性 + 最终打磨 ✅ 已完成

**分支**: `feat/phase5-hardening-polish`  
**依赖**: Phase 4

### 修复项

| # | 改进 | 说明 |
|---|------|------|
| 1 | AgvState 拷贝构造函数 | `AgvState(AgvState src)` 拷贝构造函数，ProxyHeartbeatScheduler 中 `copyAgvState()` 方法替换为 `new AgvState(agvState)` |
| 2 | 双重完成防护 | VehicleContext 新增 `completedOrderIds` Set（add/is/remove/clear），AgvStateTracker.collectOrderCompletion 中检查防重复，OrderDispatcher.sendOrder 时清除 |
| 3 | FATAL 预检 | OrderDispatcher 注入 ErrorAggregator，sendOrder() 先检查 `hasFatalError()`，拒绝向错误状态车辆下发订单 |
| 4 | Health indicator 重连计数 | VehicleContext 新增 `reconnectAttempts` AtomicInteger，MqttConnectionManager 新增 `getConsecutiveDisconnects()`，MqttHealthIndicator 暴露两个计数器到 health detail |
| 5 | 启动摘要日志 | Vda5050AutoConfiguration 添加 `@EventListener(ApplicationReadyEvent)` 打印模式、车辆数、SSL 状态、broker 地址 |
| 6 | 测试去 Thread.sleep | ProxyOrderFlowTest 中 3 处 `Thread.sleep(1000)` 改为 `awaitCondition()` 轮询 + 5s 超时 |

### 测试新增（11 个测试用例）
- `Phase5HardeningTest` — AgvState 拷贝构造函数（3 tests）、FATAL 预检拒绝/通过（2 tests）、双重完成防护（2 tests）、completedOrderIds 清除（1 test）、reconnectAttempts（1 test）
- `VehicleContextTest` — completedOrderIds add/remove/clear（3 tests）

---

## 总览

| Phase | 名称 | 修复数 | 工作量 | 关键文件 |
|-------|------|--------|--------|---------|
| 1 | Critical Safety ✅ | 8 CRITICAL | 已完成 | HeartbeatScheduler, VehicleContext, 10+11 models, SslUtil, MqttGateway, OrderDispatcher, InstantActionSender |
| 2 | High Correctness ✅ | 5 HIGH | 已完成 | ProxyOrderExecutor, VehicleContext, AgvStateTracker, MqttGateway, ProxyAutoConfig |
| 3 | Visibility + Spec ✅ | 6 MEDIUM | 已完成 | MqttInboundRouter, HealthIndicator, AutoConfig, InstantActionSender, ProxyOrderStateMachine, VehicleContext |
| 4 | Decompose + Lock Split ✅ | 3 MEDIUM (结构性) | 已完成 | ProxyOrderExecutor→3类, VehicleContext, MqttConnectionManager |
| 5 | Hardening + Polish ✅ | 6 项 | 已完成 | AgvState, VehicleContext, OrderDispatcher, HealthIndicator, AutoConfiguration, 测试 |
| **合计** | | **28 项** | **~12 天** | |

## 验证方式

每个 Phase 完成后：
1. `mvn verify` — 全部测试通过 + SpotBugs/Checkstyle clean
2. 新增测试覆盖本 Phase 修复点
3. PR review 后合并到 main
4. Phase 5 完成后打 v1.2.0 tag
