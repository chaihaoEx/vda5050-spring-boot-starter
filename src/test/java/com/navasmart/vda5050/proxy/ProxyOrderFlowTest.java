package com.navasmart.vda5050.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050AutoConfiguration;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.autoconfigure.Vda5050ProxyAutoConfiguration;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.heartbeat.ProxyHeartbeatScheduler;
import com.navasmart.vda5050.proxy.statemachine.ProxyNavigationController;
import com.navasmart.vda5050.proxy.statemachine.ProxyNodeActionDispatcher;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine;
import com.navasmart.vda5050.test.EmbeddedMqttBroker;
import com.navasmart.vda5050.test.MockProxyAdapter;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        Vda5050AutoConfiguration.class,
        Vda5050ProxyAutoConfiguration.class,
        ProxyOrderFlowTest.TestConfig.class
})
@TestPropertySource(properties = {
        "vda5050.proxy.enabled=true",
        "vda5050.proxy.vehicles[0].manufacturer=TestCo",
        "vda5050.proxy.vehicles[0].serialNumber=AGV001",
        "vda5050.proxy.heartbeatIntervalMs=500",
        "vda5050.proxy.orderLoopIntervalMs=100",
        "vda5050.mqtt.host=127.0.0.1"
})
class ProxyOrderFlowTest {

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

    private MockProxyAdapter mockAdapter;

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
        mockAdapter = TestConfig.SHARED_ADAPTER;
        mockAdapter.reset();
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
        static final MockProxyAdapter SHARED_ADAPTER = new MockProxyAdapter();

        @Bean
        @Primary
        public Vda5050ProxyVehicleAdapter vehicleAdapter() {
            return SHARED_ADAPTER;
        }

        @Bean
        @Primary
        public Vda5050ProxyStateProvider stateProvider() {
            return SHARED_ADAPTER;
        }

        @Bean
        public ProxyOrderStateMachine proxyOrderStateMachine(ErrorAggregator errorAggregator,
                                                              MqttGateway mqttGateway) {
            return new ProxyOrderStateMachine(errorAggregator, SHARED_ADAPTER, SHARED_ADAPTER, mqttGateway,
                    event -> {}, new com.navasmart.vda5050.proxy.validation.OrderValidator());
        }

        @Bean
        public ProxyNodeActionDispatcher proxyNodeActionDispatcher(ActionHandlerRegistry actionHandlerRegistry,
                                                                    Vda5050Properties properties) {
            return new ProxyNodeActionDispatcher(actionHandlerRegistry, SHARED_ADAPTER, properties);
        }

        @Bean
        public ProxyNavigationController proxyNavigationController(ErrorAggregator errorAggregator,
                                                                    ProxyNodeActionDispatcher actionDispatcher) {
            return new ProxyNavigationController(SHARED_ADAPTER, errorAggregator, actionDispatcher);
        }

        @Bean
        public ProxyOrderExecutor proxyOrderExecutor(VehicleRegistry vehicleRegistry,
                                                      ErrorAggregator errorAggregator,
                                                      ProxyNodeActionDispatcher actionDispatcher,
                                                      ProxyNavigationController navigationController,
                                                      Vda5050Properties properties,
                                                      org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new ProxyOrderExecutor(vehicleRegistry, errorAggregator, actionDispatcher,
                    navigationController, SHARED_ADAPTER, properties, eventPublisher);
        }

        @Bean
        public ProxyHeartbeatScheduler proxyHeartbeatScheduler(VehicleRegistry vehicleRegistry,
                                                                MqttGateway mqttGateway,
                                                                Vda5050Properties properties) {
            return new ProxyHeartbeatScheduler(vehicleRegistry, mqttGateway, SHARED_ADAPTER, properties);
        }

        @Bean
        public Object proxyMqttHandlerWiring(MqttInboundRouter router,
                                              ProxyOrderStateMachine stateMachine) {
            router.setOrderHandler((ctx, order) -> stateMachine.receiveOrder(ctx, order));
            router.setInstantActionsHandler((ctx, actions) -> stateMachine.receiveInstantActions(ctx, actions));
            return new Object();
        }
    }

    @Test
    void testOrderReceptionTriggersNavigation() throws Exception {
        Order order = buildSimpleOrder("order-1");
        byte[] payload = objectMapper.writeValueAsBytes(order);
        testClient.publish(TOPIC_PREFIX + "/order", new MqttMessage(payload));

        boolean navigated = mockAdapter.awaitNavigate(5, TimeUnit.SECONDS);
        assertTrue(navigated, "onNavigate should have been called after sending an order");
        assertFalse(mockAdapter.navigateCalls.isEmpty());
        assertEquals(VEHICLE_ID, mockAdapter.navigateCalls.get(0));
    }

    @Test
    void testHeartbeatPublishesState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final AgvState[] received = new AgvState[1];

        testClient.subscribe(TOPIC_PREFIX + "/state", 0, (topic, msg) -> {
            received[0] = objectMapper.readValue(msg.getPayload(), AgvState.class);
            latch.countDown();
        });

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "Should have received a state heartbeat message within 5 seconds");
        assertNotNull(received[0], "Received AgvState should not be null");
        assertEquals(MANUFACTURER, received[0].getManufacturer());
        assertEquals(SERIAL, received[0].getSerialNumber());
    }

    @Test
    void testCancelOrderTriggersCallback() throws Exception {
        // First send an order to put the proxy into RUNNING state
        Order order = buildSimpleOrder("order-cancel-1");
        byte[] orderPayload = objectMapper.writeValueAsBytes(order);
        testClient.publish(TOPIC_PREFIX + "/order", new MqttMessage(orderPayload));

        // Wait for order to be received
        mockAdapter.awaitNavigate(5, TimeUnit.SECONDS);

        // Now send cancelOrder instant action
        InstantActions instantActions = new InstantActions();
        instantActions.setHeaderId(1);
        instantActions.setTimestamp("2025-01-01T00:00:00.000Z");
        instantActions.setVersion("2.0.0");
        instantActions.setManufacturer(MANUFACTURER);
        instantActions.setSerialNumber(SERIAL);

        Action cancelAction = new Action();
        cancelAction.setActionId("cancel-" + UUID.randomUUID());
        cancelAction.setActionType("cancelOrder");
        cancelAction.setBlockingType(BlockingType.NONE.getValue());
        instantActions.setInstantActions(Collections.singletonList(cancelAction));

        byte[] iaPayload = objectMapper.writeValueAsBytes(instantActions);
        testClient.publish(TOPIC_PREFIX + "/instantActions", new MqttMessage(iaPayload));

        // Wait for processing
        Thread.sleep(1000);
        assertTrue(mockAdapter.orderCancelCalled.get(),
                "onOrderCancel should have been called after sending cancelOrder");
    }

    @Test
    void testPauseAndResume() throws Exception {
        // Send an order to put the proxy into RUNNING state
        Order order = buildSimpleOrder("order-pause-1");
        byte[] orderPayload = objectMapper.writeValueAsBytes(order);
        testClient.publish(TOPIC_PREFIX + "/order", new MqttMessage(orderPayload));
        mockAdapter.awaitNavigate(5, TimeUnit.SECONDS);

        // Send startPause
        InstantActions pauseActions = new InstantActions();
        pauseActions.setHeaderId(2);
        pauseActions.setTimestamp("2025-01-01T00:00:00.000Z");
        pauseActions.setVersion("2.0.0");
        pauseActions.setManufacturer(MANUFACTURER);
        pauseActions.setSerialNumber(SERIAL);

        Action pauseAction = new Action();
        pauseAction.setActionId("pause-" + UUID.randomUUID());
        pauseAction.setActionType("startPause");
        pauseAction.setBlockingType(BlockingType.NONE.getValue());
        pauseActions.setInstantActions(Collections.singletonList(pauseAction));

        byte[] pausePayload = objectMapper.writeValueAsBytes(pauseActions);
        testClient.publish(TOPIC_PREFIX + "/instantActions", new MqttMessage(pausePayload));

        Thread.sleep(1000);
        assertTrue(mockAdapter.pauseCalled.get(), "onPause should have been called");

        // Send stopPause
        InstantActions resumeActions = new InstantActions();
        resumeActions.setHeaderId(3);
        resumeActions.setTimestamp("2025-01-01T00:00:00.000Z");
        resumeActions.setVersion("2.0.0");
        resumeActions.setManufacturer(MANUFACTURER);
        resumeActions.setSerialNumber(SERIAL);

        Action resumeAction = new Action();
        resumeAction.setActionId("resume-" + UUID.randomUUID());
        resumeAction.setActionType("stopPause");
        resumeAction.setBlockingType(BlockingType.NONE.getValue());
        resumeActions.setInstantActions(Collections.singletonList(resumeAction));

        byte[] resumePayload = objectMapper.writeValueAsBytes(resumeActions);
        testClient.publish(TOPIC_PREFIX + "/instantActions", new MqttMessage(resumePayload));

        Thread.sleep(1000);
        assertTrue(mockAdapter.resumeCalled.get(), "onResume should have been called");
    }

    private Order buildSimpleOrder(String orderId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(0);
        order.setHeaderId(1);
        order.setTimestamp("2025-01-01T00:00:00.000Z");
        order.setVersion("2.0.0");
        order.setManufacturer(MANUFACTURER);
        order.setSerialNumber(SERIAL);

        Node node1 = new Node();
        node1.setNodeId("node1");
        node1.setSequenceId(0);
        node1.setReleased(true);
        node1.setActions(Collections.emptyList());
        NodePosition pos1 = new NodePosition();
        pos1.setX(0);
        pos1.setY(0);
        pos1.setMapId("map1");
        node1.setNodePosition(pos1);

        Node node2 = new Node();
        node2.setNodeId("node2");
        node2.setSequenceId(2);
        node2.setReleased(true);
        node2.setActions(Collections.emptyList());
        NodePosition pos2 = new NodePosition();
        pos2.setX(5);
        pos2.setY(0);
        pos2.setMapId("map1");
        node2.setNodePosition(pos2);

        Edge edge1 = new Edge();
        edge1.setEdgeId("edge1");
        edge1.setSequenceId(1);
        edge1.setReleased(true);
        edge1.setStartNodeId("node1");
        edge1.setEndNodeId("node2");
        edge1.setActions(Collections.emptyList());

        order.setNodes(List.of(node1, node2));
        order.setEdges(List.of(edge1));
        return order;
    }
}
