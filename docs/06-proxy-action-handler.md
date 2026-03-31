# Proxy 模式：动作处理器框架

包路径：`com.navasmart.vda5050.proxy.action`

> 参考：`vda5050_action_handler.hpp`、`vda5050_client_node.cpp` 第 306-374 行

---

## 1. ActionHandler 接口

```java
public interface ActionHandler {
    /** 支持的 actionType 集合 */
    Set<String> getSupportedActionTypes();

    /** 执行动作 */
    CompletableFuture<ActionResult> execute(String vehicleId, Action action);

    /** 取消动作 */
    default void cancel(String vehicleId, String actionId) {}

    /** 暂停动作 */
    default void pause(String vehicleId, String actionId) {}

    /** 恢复动作 */
    default void resume(String vehicleId, String actionId) {}

    /** 是否支持暂停 */
    default boolean canBePaused() { return false; }
}
```

FMS 实现此接口并注册为 Spring Bean，Starter 自动发现。

---

## 2. Blocking Type 调度

| 类型 | 行为 |
|------|------|
| **HARD** | 独占执行，不允许其他动作和驾驶 |
| **SOFT** | 允许其他动作，但不允许驾驶 |
| **NONE** | 允许并行执行和驾驶 |

空 blockingType 默认为 **HARD**。

### 调度算法

```
遍历节点上每个 Action：
  FINISHED/FAILED → 跳过
  RUNNING + HARD → 等待完成，返回
  RUNNING + SOFT → 标记 stopDriving
  WAITING + HARD (无运行中动作) → 执行，返回等待
  WAITING + SOFT/NONE → 执行
全部完成 → ALL_ACTIONS_DONE → 前进到下一节点
```

---

## 3. 处理优先级

1. **内置即时动作**（cancelOrder、startPause 等） → 状态机直接处理
2. **注册了 ActionHandler 的 actionType** → 调用 handler
3. **未注册的 actionType** → 回退到 `Vda5050ProxyVehicleAdapter.onActionExecute()`
4. **无任何处理器** → FAILED

---

## 4. 自定义处理器示例

```java
@Component
public class ForkLiftActionHandler implements ActionHandler {

    @Override
    public Set<String> getSupportedActionTypes() {
        return Set.of("pick", "place", "liftFork", "lowerFork");
    }

    @Override
    public CompletableFuture<ActionResult> execute(String vehicleId, Action action) {
        switch (action.getActionType()) {
            case "pick":
                String loadId = getParam(action, "loadId");
                return udpSender.sendPick(vehicleId, loadId);
            case "place":
                return udpSender.sendPlace(vehicleId);
            default:
                return CompletableFuture.completedFuture(
                    ActionResult.failure("未知动作"));
        }
    }

    @Override
    public boolean canBePaused() { return true; }

    @Override
    public void cancel(String vehicleId, String actionId) {
        udpSender.sendCancelAction(vehicleId, actionId);
    }
}
```
