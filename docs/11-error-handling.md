# 错误处理

包路径：`com.navasmart.vda5050.error`（共享）

---

## 1. 错误级别

| 级别 | 含义 | AGV 行为 |
|------|------|---------|
| **WARNING** | 非致命 | AGV 可继续运行 |
| **FATAL** | 致命 | AGV 不可操作，需干预 |

---

## 2. Proxy 模式错误

### 2.1 协议层错误（Starter 产生）

| 错误类型 | 级别 | 场景 |
|---------|------|------|
| validationError | WARNING | 无效 Order 消息 |
| orderUpdateError | WARNING | 忙时收到不同 orderId |
| noOrderToCancel | WARNING | 无活跃订单时 cancelOrder |

### 2.2 执行层错误（导航/动作失败）

| 错误类型 | 级别 | 场景 |
|---------|------|------|
| navigationError | FATAL | onNavigate() 返回失败 |
| navigationTimeout | FATAL | 导航超时 |
| actionError | WARNING/FATAL | onActionExecute() 返回失败 |

### 2.3 FATAL 错误处理流程

```
FATAL 错误 → 停止新导航 → 等待运行中动作完成 → 状态→IDLE → 上报 AgvState
```

### 2.4 错误清除

- 接受新订单时清除所有错误
- FMS 在 VehicleStatus 中不再包含该错误

---

## 3. Server 模式错误

### 3.1 AGV 上报错误

Server 通过 `AgvStateTracker` 检测 `AgvState.errors[]` 中的变化：

```java
// 新增错误 → 回调
serverAdapter.onErrorReported(vehicleId, error);

// 已清除错误 → 回调
serverAdapter.onErrorCleared(vehicleId, error);
```

### 3.2 通信错误

| 场景 | 检测方式 | 回调 |
|------|---------|------|
| AGV 断连 | Connection 消息 = CONNECTIONBROKEN | onConnectionStateChanged() |
| AGV 状态超时 | 超过 stateTimeoutMs 未收到 state | onVehicleTimeout() |
| 订单执行失败 | AgvState 中有 FATAL 错误 | onOrderFailed() |

---

## 4. Vda5050ErrorFactory

```java
@Component
public class Vda5050ErrorFactory {

    public Error createError(ErrorLevel level, String description,
                             String errorType, Map<String, String> references) {
        Error error = new Error();
        error.setErrorType(errorType);
        error.setErrorDescription(description);
        error.setErrorLevel(level.name());
        List<ErrorReference> refs = new ArrayList<>();
        if (references != null) {
            references.forEach((k, v) -> {
                ErrorReference ref = new ErrorReference();
                ref.setReferenceKey(k);
                ref.setReferenceValue(v);
                refs.add(ref);
            });
        }
        error.setErrorReferences(refs);
        return error;
    }

    public Error createWarning(String description, String errorType) {
        return createError(ErrorLevel.WARNING, description, errorType, null);
    }

    public Error createFatal(String description, String errorType,
                             Map<String, String> references) {
        return createError(ErrorLevel.FATAL, description, errorType, references);
    }
}
```

---

## 5. ErrorAggregator（Proxy 模式使用）

```java
@Component
public class ErrorAggregator {

    /** 添加错误（自动去重） */
    public void addError(VehicleContext ctx, Error error) { ... }

    /** 添加 WARNING */
    public void addWarning(VehicleContext ctx, String desc, String type,
                           Map<String, String> refs) { ... }

    /** 添加 FATAL */
    public void addFatalError(VehicleContext ctx, String desc, String type) { ... }

    /** 清除所有错误（新订单时调用） */
    public void clearAllErrors(VehicleContext ctx) { ... }

    /** 是否有 FATAL 错误 */
    public boolean hasFatalError(VehicleContext ctx) { ... }

    /** 合并 FMS 上报的错误 */
    public void mergeExternalErrors(VehicleContext ctx, List<Error> fmsErrors) { ... }
}
```

---

## 6. FMS 侧错误上报（Proxy 模式）

通过 VehicleStatus：

```java
public VehicleStatus getVehicleStatus(String vehicleId) {
    VehicleStatus status = new VehicleStatus();
    if (hasHardwareError(vehicleId)) {
        Error err = new Error();
        err.setErrorType("hardwareError");
        err.setErrorDescription("激光雷达异常");
        err.setErrorLevel("WARNING");
        err.setErrorReferences(List.of());
        status.setErrors(List.of(err));
    }
    return status;
}
```
