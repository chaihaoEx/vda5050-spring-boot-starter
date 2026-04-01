package com.navasmart.vda5050.proxy.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.enums.ConnectionState;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Proxy 模式下的连接状态发布器，负责在应用启动和关闭时发布 VDA5050 Connection 消息。
 *
 * <p>生命周期行为：
 * <ul>
 *   <li>应用启动后（{@link PostConstruct}）：为所有 Proxy 车辆发布 ONLINE 状态</li>
 *   <li>应用关闭前（{@link PreDestroy}）：先通知执行器停止、等待进行中的工作完成，再发布 OFFLINE 状态</li>
 * </ul>
 */
@Component
public class ProxyConnectionPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProxyConnectionPublisher.class);
    private static final long SHUTDOWN_TIMEOUT_MS = 10_000;

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    @Autowired(required = false)
    private ProxyOrderExecutor orderExecutor;

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
     * 应用关闭前优雅停机：先停止执行器、等待进行中工作完成，再发布 OFFLINE。
     */
    @PreDestroy
    public void publishOffline() {
        // 通知执行器停止接受新工作
        if (orderExecutor != null) {
            orderExecutor.shutdown();

            // 等待进行中的工作完成（最多等待 10 秒）
            long deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS;
            while (!orderExecutor.isIdle() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!orderExecutor.isIdle()) {
                log.warn("Shutdown timeout: some vehicles still have active work");
            }
        }

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
