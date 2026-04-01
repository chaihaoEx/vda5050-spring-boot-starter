package com.navasmart.vda5050.actuator;

import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator Health Indicator，暴露 MQTT 连接状态。
 *
 * <p>仅在 classpath 中存在 {@code spring-boot-starter-actuator} 时激活。
 * 通过 {@code /actuator/health} 端点可查看 MQTT 连接健康状态。</p>
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
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
