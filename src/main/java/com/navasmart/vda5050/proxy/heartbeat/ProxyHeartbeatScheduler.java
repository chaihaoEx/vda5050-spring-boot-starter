package com.navasmart.vda5050.proxy.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.AgvPosition;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.BatteryState;
import com.navasmart.vda5050.model.SafetyState;
import com.navasmart.vda5050.model.Velocity;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.VehicleStatus;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Proxy 模式下的心跳发布调度器，按固定间隔周期性发布 VDA5050 State 消息。
 *
 * <p>每个心跳周期（默认 1000ms，可通过 {@code vda5050.proxy.heartbeatIntervalMs} 配置），
 * 对每辆 Proxy 车辆执行：
 * <ol>
 *   <li>调用 {@link Vda5050ProxyStateProvider#getVehicleStatus} 获取最新车辆状态</li>
 *   <li>将状态映射到 AgvState 的位置、速度、电池、安全等字段</li>
 *   <li>合并外部错误列表（保留协议层错误，替换外部错误）</li>
 *   <li>通过 MQTT 发布 State 消息</li>
 * </ol>
 *
 * <p>线程安全：心跳发布在调度线程中执行，与订单执行循环使用相同的 VehicleContext 锁机制。</p>
 *
 * @see Vda5050ProxyStateProvider
 * @see VehicleStatus
 */
@Component
public class ProxyHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHeartbeatScheduler.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050ProxyStateProvider stateProvider;
    private final Vda5050Properties properties;

    public ProxyHeartbeatScheduler(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                   Vda5050ProxyStateProvider stateProvider,
                                   Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.stateProvider = stateProvider;
        this.properties = properties;
    }

    /**
     * 心跳发布主方法，按固定间隔（默认 1000ms）被 Spring Scheduling 调用。
     *
     * <p>遍历所有 Proxy 模式的车辆，从 StateProvider 获取最新状态并发布 State 消息。
     * 单辆车的发布失败不会影响其他车辆。</p>
     */
    @Scheduled(fixedDelayString = "${vda5050.proxy.heartbeatIntervalMs:1000}")
    public void publishHeartbeat() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            try {
                AgvState snapshot;
                ctx.lock();
                try {
                    updateStateFromProvider(ctx);
                    AgvState agvState = ctx.getAgvState();
                    agvState.setHeaderId(ctx.nextStateHeaderId());
                    agvState.setTimestamp(TimestampUtil.now());
                    agvState.setVersion(properties.getMqtt().getProtocolVersion());
                    agvState.setManufacturer(ctx.getManufacturer());
                    agvState.setSerialNumber(ctx.getSerialNumber());
                    snapshot = copyAgvState(agvState);
                } finally {
                    ctx.unlock();
                }

                mqttGateway.publishState(ctx.getManufacturer(), ctx.getSerialNumber(), snapshot);
            } catch (Exception e) {
                log.error("Failed to publish heartbeat for vehicle {}: {}",
                        ctx.getVehicleId(), e.getMessage(), e);
            }
        }
    }

    private void updateStateFromProvider(VehicleContext ctx) {
        VehicleStatus status = stateProvider.getVehicleStatus(ctx.getVehicleId());
        if (status == null) {
            return;
        }

        AgvState agvState = ctx.getAgvState();

        // 映射位置信息到 AgvPosition
        AgvPosition pos = new AgvPosition();
        pos.setPositionInitialized(status.isPositionInitialized());
        pos.setX(status.getX());
        pos.setY(status.getY());
        pos.setTheta(status.getTheta());
        pos.setMapId(status.getMapId());
        pos.setLocalizationScore(status.getLocalizationScore());
        agvState.setAgvPosition(pos);

        // 映射速度信息到 Velocity
        Velocity vel = new Velocity();
        vel.setVx(status.getVx());
        vel.setVy(status.getVy());
        vel.setOmega(status.getOmega());
        agvState.setVelocity(vel);

        // 映射电池信息到 BatteryState
        BatteryState battery = new BatteryState();
        battery.setBatteryCharge(status.getBatteryCharge());
        battery.setBatteryVoltage(status.getBatteryVoltage());
        battery.setBatteryHealth(status.getBatteryHealth());
        battery.setCharging(status.isCharging());
        battery.setReach(status.getReach());
        agvState.setBatteryState(battery);

        // 映射安全信息到 SafetyState
        SafetyState safety = new SafetyState();
        safety.setEStop(status.getEStop());
        safety.setFieldViolation(status.isFieldViolation());
        agvState.setSafetyState(safety);

        // 映射载荷信息
        if (status.getLoads() != null) {
            agvState.setLoads(status.getLoads());
        }

        // 合并外部错误：移除之前从 FMS 传入的非协议错误，替换为最新的外部错误
        if (status.getErrors() != null) {
            // 保留协议层产生的错误（validation、orderUpdate、navigation 等），替换其余外部错误
            agvState.getErrors().removeIf(e -> !isProtocolError(e));
            agvState.getErrors().addAll(status.getErrors());
        }
    }

    private AgvState copyAgvState(AgvState src) {
        AgvState copy = new AgvState();
        copy.setHeaderId(src.getHeaderId());
        copy.setTimestamp(src.getTimestamp());
        copy.setVersion(src.getVersion());
        copy.setManufacturer(src.getManufacturer());
        copy.setSerialNumber(src.getSerialNumber());
        copy.setOrderId(src.getOrderId());
        copy.setOrderUpdateId(src.getOrderUpdateId());
        copy.setZoneSetId(src.getZoneSetId());
        copy.setLastNodeId(src.getLastNodeId());
        copy.setLastNodeSequenceId(src.getLastNodeSequenceId());
        copy.setNodeStates(new ArrayList<>(src.getNodeStates()));
        copy.setEdgeStates(new ArrayList<>(src.getEdgeStates()));
        copy.setAgvPosition(src.getAgvPosition());
        copy.setVelocity(src.getVelocity());
        if (src.getLoads() != null) {
            copy.setLoads(new ArrayList<>(src.getLoads()));
        }
        copy.setDriving(src.isDriving());
        copy.setPaused(src.isPaused());
        copy.setNewBaseRequested(src.isNewBaseRequested());
        copy.setDistanceSinceLastNode(src.getDistanceSinceLastNode());
        copy.setActionStates(new ArrayList<>(src.getActionStates()));
        copy.setBatteryState(src.getBatteryState());
        copy.setOperatingMode(src.getOperatingMode());
        copy.setErrors(new ArrayList<>(src.getErrors()));
        copy.setInformations(new ArrayList<>(src.getInformations()));
        copy.setSafetyState(src.getSafetyState());
        return copy;
    }

    private boolean isProtocolError(com.navasmart.vda5050.model.Error error) {
        String type = error.getErrorType();
        return type != null && (type.startsWith("validation") || type.startsWith("orderUpdate")
                || type.startsWith("noOrderTo") || type.startsWith("navigation")
                || type.startsWith("action"));
    }
}
