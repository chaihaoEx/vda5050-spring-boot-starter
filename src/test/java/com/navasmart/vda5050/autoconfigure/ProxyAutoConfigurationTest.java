package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.mqtt.MqttTopicResolver;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.heartbeat.ProxyHeartbeatScheduler;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link Vda5050ProxyAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} for lightweight auto-configuration testing.
 * MQTT beans are mocked to avoid requiring a running MQTT broker.</p>
 */
class ProxyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Vda5050AutoConfiguration.class,
                    Vda5050ProxyAutoConfiguration.class))
            .withBean(MqttClient.class, () -> mock(MqttClient.class))
            .withBean(MqttTopicResolver.class, () -> mock(MqttTopicResolver.class))
            .withBean(MqttGateway.class, () -> mock(MqttGateway.class))
            .withBean(MqttInboundRouter.class, () -> mock(MqttInboundRouter.class))
            .withBean(MqttConnectionManager.class, () -> mock(MqttConnectionManager.class));

    @Test
    void proxyBeansNotCreatedWhenPropertyNotSet() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ActionHandlerRegistry.class);
            assertThat(context).doesNotHaveBean(ProxyOrderStateMachine.class);
            assertThat(context).doesNotHaveBean(ProxyHeartbeatScheduler.class);
        });
    }

    @Test
    void actionHandlerRegistryCreatedWhenProxyEnabled() {
        contextRunner
                .withPropertyValues("vda5050.proxy.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionHandlerRegistry.class);
                });
    }

    @Test
    void proxyOrderStateMachineNotCreatedWithoutAdapterBeans() {
        contextRunner
                .withPropertyValues("vda5050.proxy.enabled=true")
                .run(context -> {
                    // ProxyOrderStateMachine requires Vda5050ProxyVehicleAdapter and
                    // Vda5050ProxyStateProvider which are user-provided beans
                    assertThat(context).doesNotHaveBean(ProxyOrderStateMachine.class);
                });
    }
}
