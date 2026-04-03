package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for H4: resolveProxyClient should not silently fall back to shared client.
 */
class MqttGatewayProxyClientTest {

    private MqttGateway gateway;
    private VehicleRegistry vehicleRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MqttClient sharedClient = mock(MqttClient.class);
        when(sharedClient.isConnected()).thenReturn(true);

        ObjectMapper objectMapper = new ObjectMapper();
        MqttTopicResolver topicResolver = mock(MqttTopicResolver.class);
        when(topicResolver.stateTopic(anyString(), anyString())).thenReturn("test/state");
        when(topicResolver.connectionTopic(anyString(), anyString())).thenReturn("test/connection");
        when(topicResolver.factsheetTopic(anyString(), anyString())).thenReturn("test/factsheet");

        vehicleRegistry = mock(VehicleRegistry.class);
        Vda5050Properties properties = new Vda5050Properties();
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(ObjectProvider.class);

        gateway = new MqttGateway(sharedClient, objectMapper, topicResolver,
                vehicleRegistry, properties, meterProvider);
    }

    @Test
    void publishState_returnsFalse_whenProxyClientMissing() {
        // Vehicle exists but has no proxy MQTT client
        VehicleContext ctx = new VehicleContext("TestCo", "AGV001");
        when(vehicleRegistry.get("TestCo", "AGV001")).thenReturn(ctx);

        boolean result = gateway.publishState("TestCo", "AGV001", new AgvState());

        assertThat(result).isFalse();
    }

    @Test
    void publishConnection_returnsFalse_whenProxyClientMissing() {
        when(vehicleRegistry.get("TestCo", "AGV001")).thenReturn(null);

        boolean result = gateway.publishConnection("TestCo", "AGV001", new Connection());

        assertThat(result).isFalse();
    }

    @Test
    void publishFactsheet_returnsFalse_whenProxyClientMissing() {
        VehicleContext ctx = new VehicleContext("TestCo", "AGV001");
        when(vehicleRegistry.get("TestCo", "AGV001")).thenReturn(ctx);

        boolean result = gateway.publishFactsheet("TestCo", "AGV001", new Factsheet());

        assertThat(result).isFalse();
    }

    @Test
    void publishState_succeeds_whenProxyClientAvailable() throws Exception {
        VehicleContext ctx = new VehicleContext("TestCo", "AGV001");
        MqttClient proxyClient = mock(MqttClient.class);
        when(proxyClient.isConnected()).thenReturn(true);
        ctx.setProxyMqttClient(proxyClient);
        when(vehicleRegistry.get("TestCo", "AGV001")).thenReturn(ctx);

        boolean result = gateway.publishState("TestCo", "AGV001", new AgvState());

        assertThat(result).isTrue();
        verify(proxyClient).publish(anyString(), any());
    }
}
