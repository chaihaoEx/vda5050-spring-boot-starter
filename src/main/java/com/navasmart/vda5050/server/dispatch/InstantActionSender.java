package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server 模式下的即时动作发送器，负责向 AGV 发送 VDA5050 InstantActions 消息。
 *
 * <p>提供通用的 {@link #send} 方法和内置动作的便捷方法：
 * <ul>
 *   <li>{@link #cancelOrder} - 取消当前订单</li>
 *   <li>{@link #pauseVehicle} - 暂停车辆（startPause）</li>
 *   <li>{@link #resumeVehicle} - 恢复车辆（stopPause）</li>
 *   <li>{@link #requestFactsheet} - 请求 Factsheet</li>
 * </ul>
 *
 * <p>线程安全：此类通过 VehicleContext 的锁机制保证线程安全。</p>
 *
 * @see SendResult
 */
@Component
public class InstantActionSender {

    private static final Logger log = LoggerFactory.getLogger(InstantActionSender.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    public InstantActionSender(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                               Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    /**
     * 向指定车辆发送自定义即时动作列表。
     *
     * <p>自动填充消息头字段并通过 MQTT 发布到对应的 instantActions 主题。</p>
     *
     * @param vehicleId 目标车辆标识符
     * @param actions   待发送的动作列表
     * @return 发送结果；车辆未注册时返回失败
     */
    public SendResult send(String vehicleId, List<Action> actions) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        InstantActions msg = new InstantActions();
        msg.setHeaderId(ctx.nextInstantActionsHeaderId());
        msg.setTimestamp(TimestampUtil.now());
        msg.setVersion(properties.getMqtt().getProtocolVersion());
        msg.setManufacturer(ctx.getManufacturer());
        msg.setSerialNumber(ctx.getSerialNumber());
        msg.setInstantActions(actions);

        mqttGateway.publishInstantActions(ctx.getManufacturer(), ctx.getSerialNumber(), msg);
        log.info("Sent {} instant action(s) to vehicle {}", actions.size(), vehicleId);
        return SendResult.success();
    }

    /**
     * 向指定车辆发送取消订单的即时动作。
     *
     * @param vehicleId 目标车辆标识符
     * @return 发送结果
     */
    public SendResult cancelOrder(String vehicleId) {
        return sendBuiltinAction(vehicleId, "cancelOrder");
    }

    /**
     * 向指定车辆发送暂停的即时动作（startPause）。
     *
     * @param vehicleId 目标车辆标识符
     * @return 发送结果
     */
    public SendResult pauseVehicle(String vehicleId) {
        return sendBuiltinAction(vehicleId, "startPause");
    }

    /**
     * 向指定车辆发送恢复运行的即时动作（stopPause）。
     *
     * @param vehicleId 目标车辆标识符
     * @return 发送结果
     */
    public SendResult resumeVehicle(String vehicleId) {
        return sendBuiltinAction(vehicleId, "stopPause");
    }

    /**
     * 向指定车辆发送 Factsheet 请求的即时动作。
     *
     * @param vehicleId 目标车辆标识符
     * @return 发送结果
     */
    public SendResult requestFactsheet(String vehicleId) {
        return sendBuiltinAction(vehicleId, "factsheetRequest");
    }

    private SendResult sendBuiltinAction(String vehicleId, String actionType) {
        Action action = new Action();
        action.setActionId(UUID.randomUUID().toString());
        action.setActionType(actionType);
        action.setBlockingType(BlockingType.NONE.getValue());
        return send(vehicleId, Collections.singletonList(action));
    }
}
