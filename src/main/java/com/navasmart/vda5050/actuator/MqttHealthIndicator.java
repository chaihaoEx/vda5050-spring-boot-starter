package com.navasmart.vda5050.actuator;

import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Spring Boot Actuator Health Indicator，暴露 MQTT 连接状态。
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
        Health.Builder builder;
        if (connectionManager.isConnected()) {
            builder = Health.up().withDetail("sharedClient", "connected");
        } else {
            builder = Health.down().withDetail("sharedClient", "disconnected");
        }

        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            MqttClient vehicleClient = ctx.getProxyMqttClient();
            boolean connected = vehicleClient != null && vehicleClient.isConnected();
            builder.withDetail("proxy:" + ctx.getVehicleId(), connected ? "connected" : "disconnected");
        }

        return builder.build();
    }
}
