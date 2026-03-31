package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.mqtt.MqttTopicResolver;
import com.navasmart.vda5050.server.dispatch.OrderDispatcher;
import com.navasmart.vda5050.server.heartbeat.ServerConnectionMonitor;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
import com.navasmart.vda5050.server.tracking.OrderProgressTracker;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link Vda5050ServerAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} for lightweight auto-configuration testing.
 * MQTT beans are mocked to avoid requiring a running MQTT broker.</p>
 */
class ServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Vda5050AutoConfiguration.class,
                    Vda5050ServerAutoConfiguration.class))
            .withBean(MqttClient.class, () -> mock(MqttClient.class))
            .withBean(MqttTopicResolver.class, () -> mock(MqttTopicResolver.class))
            .withBean(MqttGateway.class, () -> mock(MqttGateway.class))
            .withBean(MqttInboundRouter.class, () -> mock(MqttInboundRouter.class))
            .withBean(MqttConnectionManager.class, () -> mock(MqttConnectionManager.class));

    @Test
    void serverBeansNotCreatedWhenPropertyNotSet() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OrderProgressTracker.class);
            assertThat(context).doesNotHaveBean(AgvStateTracker.class);
            assertThat(context).doesNotHaveBean(OrderDispatcher.class);
            assertThat(context).doesNotHaveBean(ServerConnectionMonitor.class);
        });
    }

    @Test
    void orderProgressTrackerCreatedWhenServerEnabled() {
        contextRunner
                .withPropertyValues("vda5050.server.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OrderProgressTracker.class);
                });
    }

    @Test
    void agvStateTrackerNotCreatedWithoutServerAdapter() {
        contextRunner
                .withPropertyValues("vda5050.server.enabled=true")
                .run(context -> {
                    // AgvStateTracker requires Vda5050ServerAdapter which is a user-provided bean
                    assertThat(context).doesNotHaveBean(AgvStateTracker.class);
                    assertThat(context).doesNotHaveBean(ServerConnectionMonitor.class);
                });
    }
}
