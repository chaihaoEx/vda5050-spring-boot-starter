package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 MqttInboundRouter 对畸形消息的处理。
 * 验证无效 JSON、错误 schema、空 payload 不会导致崩溃。
 */
class MalformedMessageTest {

    private MqttInboundRouter router;
    private final AtomicInteger orderCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        Vda5050Properties props = new Vda5050Properties();
        MqttTopicResolver resolver = new MqttTopicResolver(props);
        VehicleRegistry registry = new VehicleRegistry(props);

        // Register a test vehicle
        VehicleContext ctx = registry.getOrCreate("TestMfg", "bot01");
        ctx.setProxyMode(true);

        router = new MqttInboundRouter(mapper, resolver, registry);
        router.setOrderHandler((vehicleCtx, order) -> orderCount.incrementAndGet());
    }

    @Test
    void invalidJsonDoesNotCrash() throws Exception {
        MqttMessage msg = new MqttMessage("{ this is not valid json }".getBytes(StandardCharsets.UTF_8));
        // Should log error but not throw
        router.messageArrived("uagv/v2/TestMfg/bot01/order", msg);
        assertThat(orderCount.get()).isZero();
    }

    @Test
    void emptyPayloadDoesNotCrash() throws Exception {
        MqttMessage msg = new MqttMessage(new byte[0]);
        router.messageArrived("uagv/v2/TestMfg/bot01/order", msg);
        assertThat(orderCount.get()).isZero();
    }

    @Test
    void unknownTopicSuffixIgnored() throws Exception {
        MqttMessage msg = new MqttMessage("{}".getBytes(StandardCharsets.UTF_8));
        // "unknownTopic" is not a known suffix
        router.messageArrived("uagv/v2/TestMfg/bot01/unknownTopic", msg);
        assertThat(orderCount.get()).isZero();
    }

    @Test
    void messageForUnknownVehicleIgnored() throws Exception {
        MqttMessage msg = new MqttMessage("{\"orderId\":\"o1\"}".getBytes(StandardCharsets.UTF_8));
        router.messageArrived("uagv/v2/UnknownMfg/unknown01/order", msg);
        assertThat(orderCount.get()).isZero();
    }

    @Test
    void validOrderMessageProcessed() throws Exception {
        String json = "{\"orderId\":\"order-1\",\"orderUpdateId\":0,\"nodes\":[],\"edges\":[]}";
        MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        router.messageArrived("uagv/v2/TestMfg/bot01/order", msg);
        assertThat(orderCount.get()).isEqualTo(1);
    }
}
