package com.navasmart.vda5050.proxy.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.enums.ConnectionState;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proxy 模式下的连接状态发布器，负责在应用启动和关闭时发布 VDA5050 Connection 消息。
 *
 * <p>生命周期行为：
 * <ul>
 *   <li>应用启动后（{@link jakarta.annotation.PostConstruct}）：为所有 Proxy 车辆发布 ONLINE 状态</li>
 *   <li>应用关闭前（{@link jakarta.annotation.PreDestroy}）：为所有 Proxy 车辆发布 OFFLINE 状态</li>
 * </ul>
 * </p>
 *
 * <p>VDA5050 规范要求使用 MQTT 的 retained 消息和 LWT（Last Will and Testament）机制，
 * 确保连接状态在异常断开时也能被正确通知。</p>
 *
 * <p>线程安全：此类的方法仅在容器生命周期回调中被调用，不存在并发问题。</p>
 */
@Component
public class ProxyConnectionPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProxyConnectionPublisher.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    public ProxyConnectionPublisher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                    Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    /**
     * 应用启动后为所有 Proxy 车辆发布 ONLINE 连接状态。
     */
    @PostConstruct
    public void publishOnline() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            publishConnectionState(ctx, ConnectionState.ONLINE);
            log.info("Published ONLINE for vehicle {}", ctx.getVehicleId());
        }
    }

    /**
     * 应用关闭前为所有 Proxy 车辆发布 OFFLINE 连接状态。
     */
    @PreDestroy
    public void publishOffline() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            publishConnectionState(ctx, ConnectionState.OFFLINE);
            log.info("Published OFFLINE for vehicle {}", ctx.getVehicleId());
        }
    }

    private void publishConnectionState(VehicleContext ctx, ConnectionState state) {
        Connection conn = new Connection();
        conn.setHeaderId(ctx.nextConnectionHeaderId());
        conn.setTimestamp(TimestampUtil.now());
        conn.setVersion(properties.getMqtt().getProtocolVersion());
        conn.setManufacturer(ctx.getManufacturer());
        conn.setSerialNumber(ctx.getSerialNumber());
        conn.setConnectionState(state.getValue());
        mqttGateway.publishConnection(ctx.getManufacturer(), ctx.getSerialNumber(), conn);
    }
}
