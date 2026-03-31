package com.navasmart.vda5050.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.mqtt.MqttTopicResolver;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link Vda5050AutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} for lightweight auto-configuration testing
 * without starting a full Spring application context.</p>
 *
 * <p>MQTT beans (MqttClient, MqttGateway, MqttConnectionManager, etc.) are replaced with
 * mocks to avoid requiring a real MQTT broker connection.</p>
 */
class AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Vda5050AutoConfiguration.class))
            .withBean(MqttClient.class, () -> mock(MqttClient.class))
            .withBean(MqttTopicResolver.class, () -> mock(MqttTopicResolver.class))
            .withBean(MqttGateway.class, () -> mock(MqttGateway.class))
            .withBean(MqttInboundRouter.class, () -> mock(MqttInboundRouter.class))
            .withBean(MqttConnectionManager.class, () -> mock(MqttConnectionManager.class));

    @Test
    void vda5050ObjectMapperBeanExistsAndConfiguredCorrectly() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("vda5050ObjectMapper");
            ObjectMapper mapper = context.getBean("vda5050ObjectMapper", ObjectMapper.class);

            // Verify ignore unknown properties is disabled
            assertThat(mapper.getDeserializationConfig()
                    .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();

            // Verify NON_NULL inclusion
            assertThat(mapper.getSerializationConfig()
                    .getDefaultPropertyInclusion().getValueInclusion())
                    .isEqualTo(JsonInclude.Include.NON_NULL);
        });
    }

    @Test
    void vehicleRegistryBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(VehicleRegistry.class);
        });
    }

    @Test
    void errorAggregatorBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ErrorAggregator.class);
        });
    }

    @Test
    void vda5050ErrorFactoryBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Vda5050ErrorFactory.class);
        });
    }

    @Test
    void customObjectMapperOverridesDefault() {
        contextRunner
                .withBean("vda5050ObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasBean("vda5050ObjectMapper");
                    ObjectMapper mapper = context.getBean("vda5050ObjectMapper", ObjectMapper.class);
                    // Custom mapper should NOT have NON_NULL configured (it's a plain new ObjectMapper)
                    assertThat(mapper.getSerializationConfig()
                            .getDefaultPropertyInclusion().getValueInclusion())
                            .isNotEqualTo(JsonInclude.Include.NON_NULL);
                });
    }
}
