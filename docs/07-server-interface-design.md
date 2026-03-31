# Server 模式：调度接口设计

本文档定义 Server 模式下调度系统需要实现的接口和可调用的 API。

包路径：`com.navasmart.vda5050.server`

---

## 1. Server 模式概述

Server 模式下，你的系统作为 **VDA5050 Master Control**，负责：
- 向 VDA5050 AGV 下发订单和即时动作
- 接收并追踪 AGV 的状态变化
- 监控 AGV 的连接状态
- 管理订单的生命周期

```
你的调度逻辑
    ↕ Vda5050ServerAdapter (你实现回调)
    ↕ OrderDispatcher / InstantActionSender (你主动调用)
Server 模式内部
    ↕ MQTT
VDA5050 AGV
```

---

## 2. Vda5050ServerAdapter — 调度适配器接口

Server 模式的核心回调接口。当 AGV 状态发生变化时，Starter 通过此接口通知你的调度系统。

```java
/**
 * Server 模式调度适配器。调度系统实现此接口以接收 AGV 事件。
 * vehicleId 格式为 "{manufacturer}:{serialNumber}"
 */
public interface Vda5050ServerAdapter {

    // ===== 状态事件 =====

    /**
     * AGV 状态更新。每次收到 AgvState 消息时调用。
     * 可用于更新调度系统的车辆状态视图。
     *
     * @param vehicleId 车辆标识
     * @param state     完整的 AGV 状态
     */
    void onStateUpdate(String vehicleId, AgvState state);

    /**
     * AGV 到达节点。当 AgvState.lastNodeId 变化时调用。
     *
     * @param vehicleId 车辆标识
     * @param nodeId    到达的节点 ID
     * @param sequenceId 节点序列 ID
     */
    default void onNodeReached(String vehicleId, String nodeId, int sequenceId) {}

    /**
     * 动作状态变化。当某个 ActionState 的 actionStatus 变化时调用。
     *
     * @param vehicleId   车辆标识
     * @param actionState 变化后的动作状态
     */
    default void onActionStateChanged(String vehicleId, ActionState actionState) {}

    // ===== 订单事件 =====

    /**
     * 订单执行完成。当 AGV 完成当前订单的所有节点和动作时调用。
     *
     * @param vehicleId 车辆标识
     * @param orderId   完成的订单 ID
     */
    default void onOrderCompleted(String vehicleId, String orderId) {}

    /**
     * 订单执行失败。当 AGV 报告 FATAL 错误导致订单中止时调用。
     *
     * @param vehicleId 车辆标识
     * @param orderId   失败的订单 ID
     * @param errors    相关错误列表
     */
    default void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {}

    // ===== 连接事件 =====

    /**
     * AGV 连接状态变化。
     *
     * @param vehicleId       车辆标识
     * @param connectionState ONLINE / OFFLINE / CONNECTIONBROKEN
     */
    default void onConnectionStateChanged(String vehicleId, String connectionState) {}

    /**
     * AGV 状态超时。长时间未收到 AgvState 消息。
     *
     * @param vehicleId          车辆标识
     * @param lastSeenTimestamp   最后一次收到状态的时间
     */
    default void onVehicleTimeout(String vehicleId, String lastSeenTimestamp) {}

    // ===== 能力事件 =====

    /**
     * 收到 AGV 的 Factsheet。
     *
     * @param vehicleId 车辆标识
     * @param factsheet 车辆能力描述
     */
    default void onFactsheetReceived(String vehicleId, Factsheet factsheet) {}

    // ===== 错误事件 =====

    /**
     * AGV 报告新错误。当 AgvState.errors 中出现新的错误时调用。
     *
     * @param vehicleId 车辆标识
     * @param error     新增的错误
     */
    default void onErrorReported(String vehicleId, Error error) {}

    /**
     * AGV 错误已清除。之前报告的错误不再出现在 AgvState.errors 中。
     *
     * @param vehicleId 车辆标识
     * @param error     已清除的错误
     */
    default void onErrorCleared(String vehicleId, Error error) {}
}
```

---

## 3. OrderDispatcher — 订单下发 API

调度系统主动调用此组件向 AGV 发送订单。

```java
/**
 * VDA5050 订单下发器。通过 MQTT 向 AGV 发送 Order 消息。
 * 由 Starter 自动创建，调度系统注入使用。
 */
@Component
public class OrderDispatcher {

    /**
     * 向指定 AGV 发送订单。
     * 自动填充 headerId、timestamp、version、manufacturer、serialNumber。
     *
     * @param vehicleId 车辆标识（manufacturer:serialNumber）
     * @param order     订单内容（需设置 orderId、orderUpdateId、nodes、edges）
     * @return 发送结果
     */
    public SendResult sendOrder(String vehicleId, Order order) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) return SendResult.failure("车辆未注册: " + vehicleId);

        // 自动填充协议头
        order.setHeaderId(ctx.nextOrderHeaderId());
        order.setTimestamp(TimestampUtil.now());
        order.setVersion(properties.getMqtt().getProtocolVersion());
        order.setManufacturer(ctx.getManufacturer());
        order.setSerialNumber(ctx.getSerialNumber());

        // 记录已发送订单用于状态追踪
        ctx.setLastSentOrder(order);

        mqttGateway.publishOrder(ctx.getManufacturer(), ctx.getSerialNumber(), order);
        return SendResult.success();
    }

    /**
     * 发送订单更新（同一 orderId，递增的 orderUpdateId）。
     * 用于追加节点/边或更新 horizon。
     */
    public SendResult sendOrderUpdate(String vehicleId, Order orderUpdate) {
        // 验证 orderId 与当前活跃订单一致
        // ...
        return sendOrder(vehicleId, orderUpdate);
    }
}
```

---

## 4. InstantActionSender — 即时动作发送 API

```java
@Component
public class InstantActionSender {

    /**
     * 向 AGV 发送即时动作列表。
     */
    public SendResult send(String vehicleId, List<Action> actions) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) return SendResult.failure("车辆未注册");

        InstantActions msg = new InstantActions();
        msg.setHeaderId(ctx.nextInstantActionsHeaderId());
        msg.setTimestamp(TimestampUtil.now());
        msg.setVersion(properties.getMqtt().getProtocolVersion());
        msg.setManufacturer(ctx.getManufacturer());
        msg.setSerialNumber(ctx.getSerialNumber());
        msg.setInstantActions(actions);

        mqttGateway.publishInstantActions(ctx.getManufacturer(), ctx.getSerialNumber(), msg);
        return SendResult.success();
    }

    // ===== 便捷方法 =====

    /** 发送 cancelOrder */
    public SendResult cancelOrder(String vehicleId) {
        Action cancel = new Action();
        cancel.setActionType("cancelOrder");
        cancel.setActionId(UUID.randomUUID().toString());
        cancel.setBlockingType("NONE");
        cancel.setActionParameters(Collections.emptyList());
        cancel.setActionDescription("");
        return send(vehicleId, List.of(cancel));
    }

    /** 发送 startPause */
    public SendResult pauseVehicle(String vehicleId) {
        return sendSingleAction(vehicleId, "startPause");
    }

    /** 发送 stopPause */
    public SendResult resumeVehicle(String vehicleId) {
        return sendSingleAction(vehicleId, "stopPause");
    }

    /** 请求 Factsheet */
    public SendResult requestFactsheet(String vehicleId) {
        return sendSingleAction(vehicleId, "factsheetRequest");
    }

    private SendResult sendSingleAction(String vehicleId, String actionType) {
        Action action = new Action();
        action.setActionType(actionType);
        action.setActionId(UUID.randomUUID().toString());
        action.setBlockingType("NONE");
        action.setActionParameters(Collections.emptyList());
        action.setActionDescription("");
        return send(vehicleId, List.of(action));
    }
}
```

---

## 5. SendResult

```java
public class SendResult {
    private final boolean success;
    private final String failureReason;

    public static SendResult success() { return new SendResult(true, null); }
    public static SendResult failure(String reason) { return new SendResult(false, reason); }
}
```

---

## 6. 调度系统实现示例

```java
@Component
public class MyDispatchAdapter implements Vda5050ServerAdapter {

    @Autowired
    private OrderDispatcher orderDispatcher;
    @Autowired
    private InstantActionSender instantActionSender;
    @Autowired
    private TaskQueueService taskQueue; // 你的任务队列

    @Override
    public void onStateUpdate(String vehicleId, AgvState state) {
        // 更新内部车辆状态视图
        fleetView.updateVehicle(vehicleId, state);
    }

    @Override
    public void onNodeReached(String vehicleId, String nodeId, int sequenceId) {
        log.info("AGV {} 到达节点 {}", vehicleId, nodeId);
    }

    @Override
    public void onOrderCompleted(String vehicleId, String orderId) {
        log.info("AGV {} 完成订单 {}", vehicleId, orderId);
        // 分配下一个任务
        Task nextTask = taskQueue.pollForVehicle(vehicleId);
        if (nextTask != null) {
            Order order = buildOrderFromTask(nextTask);
            orderDispatcher.sendOrder(vehicleId, order);
        }
    }

    @Override
    public void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {
        log.error("AGV {} 订单 {} 失败: {}", vehicleId, orderId, errors);
        alertService.notifyOperator(vehicleId, errors);
    }

    @Override
    public void onConnectionStateChanged(String vehicleId, String connectionState) {
        if ("CONNECTIONBROKEN".equals(connectionState)) {
            log.warn("AGV {} 连接断开!", vehicleId);
            alertService.notifyDisconnection(vehicleId);
        }
    }

    @Override
    public void onErrorReported(String vehicleId, Error error) {
        if ("FATAL".equals(error.getErrorLevel())) {
            alertService.criticalAlert(vehicleId, error);
        }
    }
}
```
