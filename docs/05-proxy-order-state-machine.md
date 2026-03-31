# Proxy 模式：订单状态机

包路径：`com.navasmart.vda5050.proxy.statemachine`

> 参考：`vda5050_client_node.cpp` 第 248-598 行

---

## 1. 状态定义

```java
public enum ProxyClientState { IDLE, RUNNING, PAUSED }
```

```
         收到有效 Order
    ┌──────────────────────┐
    │                      ▼
  IDLE ◄──────────── RUNNING ──────────► PAUSED
    ▲                 │    ▲                │
    │  cancelOrder    │    │  stopPause     │
    │  FATAL 错误     │    └────────────────┘
    │  订单完成       │
    └─────────────────┘
```

---

## 2. 状态转换表

| 当前 | 事件 | 目标 | 守卫条件 | 附带动作 |
|-----|------|-----|---------|---------|
| IDLE | 收到 Order | RUNNING | canAcceptOrder() | 初始化 AgvState |
| RUNNING | 所有节点完成 | IDLE | — | 发布最终状态 |
| RUNNING | cancelOrder | IDLE | — | 取消导航，WAITING 动作→FAILED |
| RUNNING | startPause | PAUSED | — | 取消导航，暂停运行中动作 |
| RUNNING | FATAL 错误 | IDLE | 运行中动作完成后 | 报告错误 |
| RUNNING | 不同 orderId | 不变 | — | 添加 WARNING |
| PAUSED | stopPause | RUNNING | — | 恢复动作，重置导航 |
| PAUSED | cancelOrder | IDLE | — | 取消所有操作 |

---

## 3. ProxyOrderStateMachine

```java
public class ProxyOrderStateMachine {

    public synchronized void receiveOrder(Order order) {
        if (!canAcceptOrder(order)) {
            errorAggregator.addWarning(ctx, "Cannot accept order", "orderUpdateError",
                Map.of("orderId", order.getOrderId()));
            return;
        }
        ctx.setCurrentOrder(order);
        ctx.setCurrentNodeIndex(0);
        ctx.setReachedWaypoint(true);
        initAgvState(order);
        ctx.setClientState(ProxyClientState.RUNNING);
    }

    private boolean canAcceptOrder(Order order) {
        if (ctx.getClientState() == ProxyClientState.IDLE) return true;
        if (ctx.getClientState() == ProxyClientState.PAUSED) return false;
        // RUNNING：同一 orderId 更新可接受，或当前订单已完成
        if (ctx.getCurrentOrder() != null
            && ctx.getCurrentOrder().getOrderId().equals(order.getOrderId())) return true;
        return isCurrentOrderCompleted();
    }

    public synchronized void receiveInstantActions(InstantActions instantActions) {
        for (Action action : instantActions.getInstantActions()) {
            processInstantAction(action);
        }
    }
}
```

---

## 4. ProxyOrderExecutor — 执行引擎（200ms 循环）

```java
@Component
public class ProxyOrderExecutor {

    private void executeForVehicle(VehicleContext ctx) {
        ctx.lock();
        try {
            if (ctx.getClientState() != ProxyClientState.RUNNING) return;

            // 1. 检查 FATAL 错误
            if (hasFatalError(ctx)) { handleFatalError(ctx); return; }

            // 2. 节点已到达：处理动作
            if (ctx.isReachedWaypoint()) {
                if (ctx.getCurrentNodeIndex() >= ctx.getCurrentOrder().getNodes().size()) {
                    ctx.setClientState(ProxyClientState.IDLE); return; // 订单完成
                }
                Node node = ctx.getCurrentOrder().getNodes().get(ctx.getCurrentNodeIndex());
                NodeProcessResult result = nodeProcessor.processNode(ctx, node);
                if (result == NodeProcessResult.ALL_ACTIONS_DONE) {
                    advanceToNextNode(ctx); // 前进到下一节点
                }
            }
        } finally {
            ctx.unlock();
        }
    }
}
```

---

## 5. 即时动作处理

| actionType | 处理方式 |
|-----------|---------|
| cancelOrder | 取消导航，WAITING→FAILED，状态→IDLE |
| startPause | 取消导航，暂停运行中动作，状态→PAUSED |
| stopPause | 恢复暂停动作，状态→RUNNING |
| startTeleop | 类似 pause，切换遥控模式 |
| stopTeleop | 退出遥控，恢复执行 |
| factsheetRequest | 发布 Factsheet |
| 其他 | 委托给 ActionHandler 或 VehicleAdapter |

---

## 6. AgvState 更新规则

| 字段 | 更新时机 | 说明 |
|------|---------|------|
| lastNodeId / lastNodeSequenceId | 到达节点时 | 导航回调返回后更新 |
| nodeStates / edgeStates | 经过后 | 移除已过元素 |
| driving | 导航开始/结束 | boolean |
| paused | pause/resume | boolean |
| actionStates | 动作状态变更 | 对应 actionId |
| agvPosition / velocity / batteryState | 定时心跳 | 从 Vda5050ProxyStateProvider 获取 |
| errors | 有错误产生时 | ErrorAggregator 添加 |
