package com.navasmart.vda5050.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050AutoConfiguration;
import com.navasmart.vda5050.autoconfigure.Vda5050ServerAutoConfiguration;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.dispatch.OrderDispatcher;
import com.navasmart.vda5050.server.heartbeat.ServerConnectionMonitor;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
import com.navasmart.vda5050.test.EmbeddedMqttBroker;
import com.navasmart.vda5050.test.MockServerAdapter;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        Vda5050AutoConfiguration.class,
        Vda5050ServerAutoConfiguration.class,
        ServerStateTrackingTest.TestConfig.class
})
@TestPropertySource(properties = {
        "vda5050.server.enabled=true",
        "vda5050.server.vehicles[0].manufacturer=TestCo",
        "vda5050.server.vehicles[0].serialNumber=AGV001",
        "vda5050.mqtt.host=127.0.0.1"
})
class ServerStateTrackingTest {

    private static final String MANUFACTURER = "TestCo";
    private static final String SERIAL = "AGV001";
    private static final String VEHICLE_ID = MANUFACTURER + ":" + SERIAL;
    private static final String STATE_TOPIC = "uagv/v2/" + MANUFACTURER + "/" + SERIAL + "/state";

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

    private MockServerAdapter mockAdapter;

    @Autowired
    private OrderDispatcher orderDispatcher;

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
        static final MockServerAdapter SHARED_ADAPTER = new MockServerAdapter();

        @Bean
        public Vda5050ServerAdapter serverAdapter() {
            return SHARED_ADAPTER;
        }

        @Bean
        public AgvStateTracker agvStateTracker(VehicleRegistry vehicleRegistry) {
            return new AgvStateTracker(vehicleRegistry, SHARED_ADAPTER, event -> {});
        }

        @Bean
        public ServerConnectionMonitor serverConnectionMonitor(
                VehicleRegistry vehicleRegistry, Vda5050Properties properties) {
            return new ServerConnectionMonitor(vehicleRegistry, properties, SHARED_ADAPTER, event -> {});
        }

        @Bean
        public Object serverMqttHandlerWiring(MqttInboundRouter router,
                                               AgvStateTracker stateTracker,
                                               ServerConnectionMonitor connectionMonitor) {
            router.setStateHandler((ctx, state) ->
                    stateTracker.processState(ctx.getVehicleId(), state));
            router.setConnectionHandler((ctx, conn) ->
                    connectionMonitor.processConnection(ctx.getVehicleId(), conn));
            router.setFactsheetHandler((ctx, fs) ->
                    SHARED_ADAPTER.onFactsheetReceived(ctx.getVehicleId(), fs));
            return new Object();
        }
    }

    @Test
    void testStateUpdateCallback() throws Exception {
        AgvState state = buildState("order-1", "node0", false);
        publishState(state);

        Thread.sleep(2000);
        assertFalse(mockAdapter.stateUpdates.isEmpty(),
                "onStateUpdate should have been called after publishing state");
        AgvState received = mockAdapter.stateUpdates.get(mockAdapter.stateUpdates.size() - 1);
        assertEquals("order-1", received.getOrderId());
    }

    @Test
    void testNodeReachedDetection() throws Exception {
        // Publish first state with lastNodeId="node1"
        AgvState state1 = buildState("order-2", "node1", false);
        publishState(state1);
        Thread.sleep(1000);

        mockAdapter.nodesReached.clear();

        // Publish second state with lastNodeId="node2" -- should trigger onNodeReached
        AgvState state2 = buildState("order-2", "node2", false);
        publishState(state2);
        Thread.sleep(1000);

        assertTrue(mockAdapter.nodesReached.contains("node2"),
                "onNodeReached should have been called with 'node2'");
    }

    @Test
    void testOrderCompletionDetection() throws Exception {
        // First send an order so the tracker has a lastSentOrder
        Order order = new Order();
        order.setOrderId("completion-order-1");
        order.setOrderUpdateId(0);
        order.setNodes(Collections.emptyList());
        order.setEdges(Collections.emptyList());
        orderDispatcher.sendOrder(VEHICLE_ID, order);

        Thread.sleep(500);

        // Publish a state that indicates order completion:
        // same orderId, empty nodeStates, driving=false, all actions FINISHED
        AgvState completedState = new AgvState();
        completedState.setHeaderId(10);
        completedState.setTimestamp("2025-01-01T00:00:01.000Z");
        completedState.setVersion("2.0.0");
        completedState.setManufacturer(MANUFACTURER);
        completedState.setSerialNumber(SERIAL);
        completedState.setOrderId("completion-order-1");
        completedState.setOrderUpdateId(0);
        completedState.setNodeStates(Collections.emptyList());
        completedState.setEdgeStates(Collections.emptyList());
        completedState.setDriving(false);

        ActionState finishedAction = new ActionState();
        finishedAction.setActionId("act1");
        finishedAction.setActionType("pick");
        finishedAction.setActionStatus("FINISHED");
        completedState.setActionStates(List.of(finishedAction));

        completedState.setErrors(Collections.emptyList());

        publishState(completedState);

        boolean completed = mockAdapter.awaitOrderCompleted(5, TimeUnit.SECONDS);
        assertTrue(completed, "onOrderCompleted should have been called");
        assertTrue(mockAdapter.completedOrders.contains("completion-order-1"));
    }

    @Test
    void testErrorReportedAndCleared() throws Exception {
        // Publish state with an error
        AgvState stateWithError = buildState("order-err", "node0", false);
        Error error = new Error();
        error.setErrorType("hardwareError");
        error.setErrorDescription("Motor overheated");
        error.setErrorLevel("WARNING");
        stateWithError.setErrors(List.of(error));

        publishState(stateWithError);
        Thread.sleep(1000);

        assertFalse(mockAdapter.reportedErrors.isEmpty(),
                "onErrorReported should have been called");
        boolean foundError = mockAdapter.reportedErrors.stream()
                .anyMatch(e -> "hardwareError".equals(e.getErrorType())
                        && "Motor overheated".equals(e.getErrorDescription()));
        assertTrue(foundError, "The reported error should match the one we published");

        // Now publish state without the error -- should trigger onErrorCleared
        mockAdapter.clearedErrors.clear();
        AgvState stateNoError = buildState("order-err", "node0", false);
        stateNoError.setErrors(Collections.emptyList());
        publishState(stateNoError);
        Thread.sleep(1000);

        assertFalse(mockAdapter.clearedErrors.isEmpty(),
                "onErrorCleared should have been called");
        boolean clearedFound = mockAdapter.clearedErrors.stream()
                .anyMatch(e -> "hardwareError".equals(e.getErrorType())
                        && "Motor overheated".equals(e.getErrorDescription()));
        assertTrue(clearedFound, "The cleared error should match the previously reported one");
    }

    private AgvState buildState(String orderId, String lastNodeId, boolean driving) {
        AgvState state = new AgvState();
        state.setHeaderId(1);
        state.setTimestamp("2025-01-01T00:00:00.000Z");
        state.setVersion("2.0.0");
        state.setManufacturer(MANUFACTURER);
        state.setSerialNumber(SERIAL);
        state.setOrderId(orderId);
        state.setOrderUpdateId(0);
        state.setLastNodeId(lastNodeId);
        state.setLastNodeSequenceId(0);
        state.setDriving(driving);
        state.setNodeStates(new ArrayList<>());
        state.setEdgeStates(new ArrayList<>());
        state.setActionStates(new ArrayList<>());
        state.setErrors(new ArrayList<>());
        return state;
    }

    private void publishState(AgvState state) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(state);
        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(0);
        testClient.publish(STATE_TOPIC, msg);
    }
}
