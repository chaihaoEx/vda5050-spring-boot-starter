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
import com.navasmart.vda5050.proxy.action.ActionHandler;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.ActionResult;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        Vda5050AutoConfiguration.class,
        Vda5050ProxyAutoConfiguration.class,
        ProxyActionHandlerTest.TestConfig.class
})
@TestPropertySource(properties = {
        "vda5050.proxy.enabled=true",
        "vda5050.proxy.vehicles[0].manufacturer=TestCo",
        "vda5050.proxy.vehicles[0].serialNumber=AGV001",
        "vda5050.proxy.heartbeatIntervalMs=5000",
        "vda5050.proxy.orderLoopIntervalMs=100",
        "vda5050.mqtt.host=127.0.0.1"
})
class ProxyActionHandlerTest {

    private static final String MANUFACTURER = "TestCo";
    private static final String SERIAL = "AGV001";
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

    @Autowired
    private TestCustomActionHandler customActionHandler;

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
        customActionHandler.executedActions.clear();
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
        public TestCustomActionHandler customPickHandler() {
            return new TestCustomActionHandler();
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

    static class TestCustomActionHandler implements ActionHandler {

        final CopyOnWriteArrayList<String> executedActions = new CopyOnWriteArrayList<>();

        @Override
        public Set<String> getSupportedActionTypes() {
            return Set.of("customPick");
        }

        @Override
        public CompletableFuture<ActionResult> execute(String vehicleId, Action action) {
            executedActions.add(action.getActionId());
            return CompletableFuture.completedFuture(ActionResult.success("picked"));
        }
    }

    @Test
    void testCustomActionHandlerPriority() throws Exception {
        // Build an order where the first node has a "customPick" action
        Order order = buildOrderWithAction("order-custom-1", "customPick", "act-custom-1");
        byte[] payload = objectMapper.writeValueAsBytes(order);
        testClient.publish(TOPIC_PREFIX + "/order", new MqttMessage(payload));

        // Wait for the executor loop to process the action
        Thread.sleep(3000);

        // The custom handler should have been called, not the vehicle adapter
        assertTrue(customActionHandler.executedActions.contains("act-custom-1"),
                "Custom ActionHandler should have been invoked for 'customPick'");
        assertFalse(mockAdapter.actionExecuteCalls.contains("act-custom-1"),
                "VehicleAdapter.onActionExecute should NOT have been invoked for 'customPick'");
    }

    @Test
    void testFallbackToVehicleAdapter() throws Exception {
        // Build an order where the first node has an "unknownType" action (no handler registered)
        Order order = buildOrderWithAction("order-fallback-1", "unknownType", "act-unknown-1");
        byte[] payload = objectMapper.writeValueAsBytes(order);
        testClient.publish(TOPIC_PREFIX + "/order", new MqttMessage(payload));

        // Wait for the executor loop to process the action
        Thread.sleep(3000);

        // The vehicle adapter fallback should have been called
        assertTrue(mockAdapter.actionExecuteCalls.contains("act-unknown-1"),
                "VehicleAdapter.onActionExecute should have been invoked for unregistered 'unknownType'");
        assertFalse(customActionHandler.executedActions.contains("act-unknown-1"),
                "Custom ActionHandler should NOT have been invoked for 'unknownType'");
    }

    private Order buildOrderWithAction(String orderId, String actionType, String actionId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(0);
        order.setHeaderId(1);
        order.setTimestamp("2025-01-01T00:00:00.000Z");
        order.setVersion("2.0.0");
        order.setManufacturer(MANUFACTURER);
        order.setSerialNumber(SERIAL);

        Action action = new Action();
        action.setActionId(actionId);
        action.setActionType(actionType);
        action.setBlockingType(BlockingType.HARD.getValue());

        Node node1 = new Node();
        node1.setNodeId("nodeA");
        node1.setSequenceId(0);
        node1.setReleased(true);
        node1.setActions(List.of(action));
        NodePosition pos1 = new NodePosition();
        pos1.setX(0);
        pos1.setY(0);
        pos1.setMapId("map1");
        node1.setNodePosition(pos1);

        order.setNodes(List.of(node1));
        order.setEdges(Collections.emptyList());
        return order;
    }
}
