package com.navasmart.vda5050.actuator;

import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator Health Indicator，暴露 MQTT 连接状态。
 *
 * <p>根据当前激活的模式（proxy/server）分别评估健康状态：
 * <ul>
 *   <li>Server 模式：检查共享 MqttClient 连接状态</li>
 *   <li>Proxy 模式：检查每辆车的专属 MqttClient 连接状态</li>
 * </ul>
 *
 * <p>通过 {@code /actuator/health} 端点可查看 MQTT 连接健康状态。
 * 由 auto-configuration 注册，仅在 classpath 中存在 actuator 时生效。</p>
 */
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttConnectionManager connectionManager;
    private final VehicleRegistry vehicleRegistry;

    public MqttHealthIndicator(MqttConnectionManager connectionManager,
                               VehicleRegistry vehicleRegistry) {
        this.connectionManager = connectionManager;
        this.vehicleRegistry = vehicleRegistry;
    }

    @Override
    public Health health() {
        boolean anyDown = false;
        Map<String, String> details = new LinkedHashMap<>();

        // Server mode: check shared client only if server vehicles exist
        boolean hasServerVehicles = !vehicleRegistry.getServerVehicles().isEmpty();
        if (hasServerVehicles) {
            boolean sharedConnected = connectionManager.isConnected();
            details.put("sharedClient", sharedConnected ? "connected" : "disconnected");
            details.put("sharedClient.consecutiveDisconnects",
                    String.valueOf(connectionManager.getConsecutiveDisconnects()));
            if (!sharedConnected) {
                anyDown = true;
            }
        }

        // Proxy mode: check per-vehicle clients
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            MqttClient vehicleClient = ctx.getProxyMqttClient();
            boolean connected = vehicleClient != null && vehicleClient.isConnected();
            String prefix = "proxy:" + ctx.getVehicleId();
            details.put(prefix, connected ? "connected" : "disconnected");
            details.put(prefix + ".reconnectAttempts", String.valueOf(ctx.getReconnectAttempts()));
            if (!connected) {
                anyDown = true;
            }
        }

        Health.Builder builder = anyDown ? Health.down() : Health.up();
        details.forEach(builder::withDetail);
        return builder.build();
    }
}
