package com.navasmart.vda5050.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050AutoConfiguration;
import com.navasmart.vda5050.autoconfigure.Vda5050ServerAutoConfiguration;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.dispatch.InstantActionSender;
import com.navasmart.vda5050.server.dispatch.OrderDispatcher;
import com.navasmart.vda5050.test.EmbeddedMqttBroker;
import com.navasmart.vda5050.test.MockServerAdapter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        Vda5050AutoConfiguration.class,
        Vda5050ServerAutoConfiguration.class,
        ServerOrderDispatchTest.TestConfig.class
})
@TestPropertySource(properties = {
        "vda5050.server.enabled=true",
        "vda5050.server.vehicles[0].manufacturer=TestCo",
        "vda5050.server.vehicles[0].serialNumber=AGV001",
        "vda5050.mqtt.host=127.0.0.1"
})
class ServerOrderDispatchTest {

    private static final String MANUFACTURER = "TestCo";
    private static final String SERIAL = "AGV001";
    private static final String VEHICLE_ID = MANUFACTURER + ":" + SERIAL;
    private static final String TOPIC_PREFIX = "uagv/v2/" + MANUFACTURER + "/" + SERIAL;

    private static final EmbeddedMqttBroker broker = new EmbeddedMqttBroker();

    static {
        try {
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded MQTT broker", e);
        }
    }

    private MqttClient testClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrderDispatcher orderDispatcher;

    @Autowired
    private InstantActionSender instantActionSender;

    @AfterAll
    static void stopBroker() {
        broker.stop();
    }

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("vda5050.mqtt.port", () -> broker.getPort());
    }

    @BeforeEach
    void setUp() throws Exception {
        testClient = new MqttClient(
                "tcp://127.0.0.1:" + broker.getPort(),
                "test-client-" + UUID.randomUUID(),
                new MemoryPersistence());
        testClient.connect();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testClient != null && testClient.isConnected()) {
            testClient.disconnect();
            testClient.close();
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Vda5050ServerAdapter serverAdapter() {
            return new MockServerAdapter();
        }
    }

    @Test
    void testSendOrderPublishesMqtt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Order[] received = new Order[1];

        testClient.subscribe(TOPIC_PREFIX + "/order", 0, (topic, msg) -> {
            received[0] = objectMapper.readValue(msg.getPayload(), Order.class);
            latch.countDown();
        });

        // Small delay to ensure subscription is active
        Thread.sleep(500);

        Order order = buildSimpleOrder("dispatch-order-1");
        SendResult result = orderDispatcher.sendOrder(VEHICLE_ID, order);
        assertTrue(result.isSuccess(), "sendOrder should succeed for registered vehicle");

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "Order message should be received via MQTT");
        assertNotNull(received[0]);
        assertEquals("dispatch-order-1", received[0].getOrderId());
    }

    @Test
    void testSendOrderAutoPopulatesHeaders() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Order[] received = new Order[1];

        testClient.subscribe(TOPIC_PREFIX + "/order", 0, (topic, msg) -> {
            received[0] = objectMapper.readValue(msg.getPayload(), Order.class);
            latch.countDown();
        });

        Thread.sleep(500);

        Order order = new Order();
        order.setOrderId("header-test-1");
        order.setOrderUpdateId(0);
        order.setNodes(Collections.emptyList());
        order.setEdges(Collections.emptyList());

        orderDispatcher.sendOrder(VEHICLE_ID, order);

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "Order message should be received");
        assertNotNull(received[0]);
        assertTrue(received[0].getHeaderId() > 0, "headerId should be auto-populated and > 0");
        assertNotNull(received[0].getTimestamp(), "timestamp should be auto-populated");
        assertEquals("2.0.0", received[0].getVersion(), "version should be '2.0.0'");
        assertEquals(MANUFACTURER, received[0].getManufacturer());
        assertEquals(SERIAL, received[0].getSerialNumber());
    }

    @Test
    void testInstantActionSenderConvenienceMethods() throws Exception {
        // Collect all instant actions received
        List<InstantActions> receivedActions = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        testClient.subscribe(TOPIC_PREFIX + "/instantActions", 0, (topic, msg) -> {
            InstantActions ia = objectMapper.readValue(msg.getPayload(), InstantActions.class);
            receivedActions.add(ia);
            latch.countDown();
        });

        Thread.sleep(500);

        instantActionSender.cancelOrder(VEHICLE_ID);
        instantActionSender.pauseVehicle(VEHICLE_ID);
        instantActionSender.resumeVehicle(VEHICLE_ID);

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "Should receive 3 instant action messages");

        List<String> actionTypes = receivedActions.stream()
                .flatMap(ia -> ia.getInstantActions().stream())
                .map(Action::getActionType)
                .toList();

        assertTrue(actionTypes.contains("cancelOrder"), "Should contain cancelOrder action");
        assertTrue(actionTypes.contains("startPause"), "Should contain startPause action");
        assertTrue(actionTypes.contains("stopPause"), "Should contain stopPause action");
    }

    @Test
    void testSendToUnregisteredVehicle() {
        Order order = buildSimpleOrder("unknown-order-1");
        SendResult result = orderDispatcher.sendOrder("unknown:vehicle", order);
        assertFalse(result.isSuccess(), "sendOrder to unregistered vehicle should fail");
        assertNotNull(result.getFailureReason());
    }

    private Order buildSimpleOrder(String orderId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(0);

        Node node1 = new Node();
        node1.setNodeId("n1");
        node1.setSequenceId(0);
        node1.setReleased(true);
        node1.setActions(Collections.emptyList());
        NodePosition pos = new NodePosition();
        pos.setX(0);
        pos.setY(0);
        pos.setMapId("map1");
        node1.setNodePosition(pos);

        order.setNodes(List.of(node1));
        order.setEdges(Collections.emptyList());
        return order;
    }
}
