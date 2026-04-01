package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.actuator.MqttHealthIndicator;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 健康检查自动配置。
 * 仅在 classpath 中存在 {@code spring-boot-starter-actuator} 时激活。
 */
@Configuration
@ConditionalOnClass(HealthIndicator.class)
public class Vda5050ActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MqttHealthIndicator mqttHealthIndicator(MqttConnectionManager connectionManager,
                                                    VehicleRegistry vehicleRegistry) {
        return new MqttHealthIndicator(connectionManager, vehicleRegistry);
    }
}
