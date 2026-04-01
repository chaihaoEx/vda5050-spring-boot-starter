package com.navasmart.vda5050.actuator;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 MQTT Health Indicator。
 */
class MqttHealthIndicatorTest {

    @Test
    void healthUpWhenConnected() {
        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        when(manager.isConnected()).thenReturn(true);
        VehicleRegistry registry = new VehicleRegistry(new Vda5050Properties());

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("sharedClient", "connected");
    }

    @Test
    void healthDownWhenDisconnected() {
        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        when(manager.isConnected()).thenReturn(false);
        VehicleRegistry registry = new VehicleRegistry(new Vda5050Properties());

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("sharedClient", "disconnected");
    }
}
