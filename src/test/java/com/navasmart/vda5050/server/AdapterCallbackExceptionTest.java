package com.navasmart.vda5050.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.mqtt.MqttTopicResolver;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试适配器回调抛出异常时，消息路由器不会崩溃，后续消息仍能正常处理。
 */
class AdapterCallbackExceptionTest {

    private MqttInboundRouter router;
    private final AtomicInteger callCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        Vda5050Properties props = new Vda5050Properties();
        MqttTopicResolver resolver = new MqttTopicResolver(props);
        VehicleRegistry registry = new VehicleRegistry(props);

        VehicleContext ctx = registry.getOrCreate("TestMfg", "agv01");
        ctx.setServerMode(true);

        // Adapter that throws on first call, succeeds on second
        Vda5050ServerAdapter throwingAdapter = new Vda5050ServerAdapter() {
            @Override
            public void onStateUpdate(String vehicleId, AgvState state) {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    throw new RuntimeException("Simulated adapter failure");
                }
            }
        };

        AgvStateTracker tracker = new AgvStateTracker(registry, throwingAdapter, event -> {});

        router = new MqttInboundRouter(mapper, resolver, registry);
        router.setStateHandler((vehicleCtx, state) ->
                tracker.processState(vehicleCtx.getVehicleId(), state));
    }

    @Test
    void exceptionInCallbackDoesNotCrashRouter() throws Exception {
        String stateJson = "{\"headerId\":1,\"orderId\":\"\",\"nodeStates\":[],\"edgeStates\":[],"
                + "\"actionStates\":[],\"errors\":[],\"driving\":false}";
        MqttMessage msg = new MqttMessage(stateJson.getBytes(StandardCharsets.UTF_8));
        String topic = "uagv/v2/TestMfg/agv01/state";

        // First call: adapter throws, but router's catch block handles it
        router.messageArrived(topic, msg);
        assertThat(callCount.get()).isEqualTo(1);

        // Second call: should still be processed after the exception
        router.messageArrived(topic, msg);
        assertThat(callCount.get()).isEqualTo(2);
    }
}
