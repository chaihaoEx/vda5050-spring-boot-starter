package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.NodeState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.dispatch.OrderDispatcher;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 5 improvements:
 * - AgvState copy constructor
 * - completedOrderIds double-completion guard
 * - FATAL pre-check in OrderDispatcher
 * - reconnectAttempts tracking on VehicleContext
 */
class Phase5HardeningTest {

    private VehicleContext ctx;
    private ErrorAggregator errorAggregator;

    @BeforeEach
    void setUp() {
        ctx = new VehicleContext("TestCo", "AGV001");
        ctx.setProxyMode(true);
        ctx.setServerMode(true);
        errorAggregator = new ErrorAggregator(new Vda5050ErrorFactory());
    }

    // ============ AgvState copy constructor ============

    @Test
    void agvStateCopyConstructor_copiesAllFields() {
        AgvState src = new AgvState();
        src.setHeaderId(42);
        src.setTimestamp("2025-01-01T00:00:00Z");
        src.setVersion("2.0.0");
        src.setManufacturer("TestCo");
        src.setSerialNumber("AGV001");
        src.setOrderId("order-1");
        src.setOrderUpdateId(5);
        src.setZoneSetId("zone-1");
        src.setLastNodeId("node-3");
        src.setLastNodeSequenceId(4);
        src.setDriving(true);
        src.setPaused(false);
        src.setNewBaseRequested(true);
        src.setDistanceSinceLastNode(1.5);
        src.setOperatingMode("AUTOMATIC");

        NodeState ns = new NodeState();
        ns.setNodeId("n1");
        src.setNodeStates(new ArrayList<>(List.of(ns)));

        ActionState as = new ActionState();
        as.setActionId("a1");
        src.setActionStates(new ArrayList<>(List.of(as)));

        AgvState copy = new AgvState(src);

        assertThat(copy.getHeaderId()).isEqualTo(42);
        assertThat(copy.getTimestamp()).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(copy.getVersion()).isEqualTo("2.0.0");
        assertThat(copy.getManufacturer()).isEqualTo("TestCo");
        assertThat(copy.getSerialNumber()).isEqualTo("AGV001");
        assertThat(copy.getOrderId()).isEqualTo("order-1");
        assertThat(copy.getOrderUpdateId()).isEqualTo(5);
        assertThat(copy.getZoneSetId()).isEqualTo("zone-1");
        assertThat(copy.getLastNodeId()).isEqualTo("node-3");
        assertThat(copy.getLastNodeSequenceId()).isEqualTo(4);
        assertThat(copy.isDriving()).isTrue();
        assertThat(copy.isPaused()).isFalse();
        assertThat(copy.isNewBaseRequested()).isTrue();
        assertThat(copy.getDistanceSinceLastNode()).isEqualTo(1.5);
        assertThat(copy.getOperatingMode()).isEqualTo("AUTOMATIC");
        assertThat(copy.getNodeStates()).hasSize(1);
        assertThat(copy.getActionStates()).hasSize(1);
    }

    @Test
    void agvStateCopyConstructor_listsAreIndependent() {
        AgvState src = new AgvState();
        NodeState ns = new NodeState();
        ns.setNodeId("n1");
        src.getNodeStates().add(ns);

        AgvState copy = new AgvState(src);

        // Modifying copy should not affect source
        copy.getNodeStates().clear();
        assertThat(src.getNodeStates()).hasSize(1);
    }

    @Test
    void agvStateCopyConstructor_nullLoadsStayNull() {
        AgvState src = new AgvState();
        // loads is null by default (no setter called)

        AgvState copy = new AgvState(src);
        assertThat(copy.getLoads()).isNull();
    }

    // ============ FATAL pre-check in OrderDispatcher ============

    @Test
    void sendOrder_refusesWhenVehicleHasFatalError() {
        VehicleRegistry registry = mock(VehicleRegistry.class);
        when(registry.get("TestCo:AGV001")).thenReturn(ctx);

        MqttGateway gateway = mock(MqttGateway.class);
        Vda5050Properties props = new Vda5050Properties();

        OrderDispatcher dispatcher = new OrderDispatcher(registry, gateway, props, errorAggregator);

        // Add a fatal error
        ctx.lock();
        try {
            errorAggregator.addFatalError(ctx, "Navigation failed", "navigationError");
        } finally {
            ctx.unlock();
        }

        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(0);

        SendResult result = dispatcher.sendOrder("TestCo:AGV001", order);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("FATAL error");
        verify(gateway, never()).publishOrder(anyString(), anyString(), any());
    }

    @Test
    void sendOrder_succeedsWhenNoFatalError() {
        VehicleRegistry registry = mock(VehicleRegistry.class);
        when(registry.get("TestCo:AGV001")).thenReturn(ctx);

        MqttGateway gateway = mock(MqttGateway.class);
        when(gateway.publishOrder(anyString(), anyString(), any())).thenReturn(true);

        Vda5050Properties props = new Vda5050Properties();
        props.getMqtt().setProtocolVersion("2.0.0");

        OrderDispatcher dispatcher = new OrderDispatcher(registry, gateway, props, errorAggregator);

        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(0);

        SendResult result = dispatcher.sendOrder("TestCo:AGV001", order);

        assertThat(result.isSuccess()).isTrue();
        verify(gateway).publishOrder(eq("TestCo"), eq("AGV001"), any());
    }

    // ============ completedOrderIds double-completion guard ============

    @Test
    void agvStateTracker_preventsDoubleCompletion() {
        VehicleRegistry registry = mock(VehicleRegistry.class);
        when(registry.get("TestCo:AGV001")).thenReturn(ctx);

        AtomicInteger completionCount = new AtomicInteger(0);
        Vda5050ServerAdapter adapter = mock(Vda5050ServerAdapter.class);
        doAnswer(inv -> {
            completionCount.incrementAndGet();
            return null;
        }).when(adapter).onOrderCompleted(anyString(), anyString());

        AgvStateTracker tracker = new AgvStateTracker(registry, adapter, event -> {});

        // Set up a sent order
        Order sentOrder = new Order();
        sentOrder.setOrderId("order-1");
        sentOrder.setOrderUpdateId(0);
        ctx.lockServer();
        try {
            ctx.setLastSentOrder(sentOrder);
        } finally {
            ctx.unlockServer();
        }

        // Build a "completed" state
        AgvState completedState = new AgvState();
        completedState.setOrderId("order-1");
        completedState.setLastNodeId("final-node");
        completedState.setNodeStates(new ArrayList<>());
        completedState.setDriving(false);
        completedState.setActionStates(new ArrayList<>());
        completedState.setErrors(new ArrayList<>());

        // First processState triggers completion
        tracker.processState("TestCo:AGV001", completedState);
        assertThat(completionCount.get()).isEqualTo(1);

        // Re-set the sentOrder (simulating it being cleared, then re-set for same orderId)
        ctx.lockServer();
        try {
            ctx.setLastSentOrder(sentOrder);
        } finally {
            ctx.unlockServer();
        }

        // Second processState with same orderId should be guarded
        tracker.processState("TestCo:AGV001", completedState);
        assertThat(completionCount.get()).isEqualTo(1);
    }

    @Test
    void completedOrderIds_clearedWhenNewOrderSent() {
        VehicleRegistry registry = mock(VehicleRegistry.class);
        when(registry.get("TestCo:AGV001")).thenReturn(ctx);

        MqttGateway gateway = mock(MqttGateway.class);
        when(gateway.publishOrder(anyString(), anyString(), any())).thenReturn(true);

        Vda5050Properties props = new Vda5050Properties();
        props.getMqtt().setProtocolVersion("2.0.0");

        OrderDispatcher dispatcher = new OrderDispatcher(registry, gateway, props, errorAggregator);

        // Mark order-1 as completed
        ctx.addCompletedOrderId("order-1");
        assertThat(ctx.isCompletedOrder("order-1")).isTrue();

        // Sending a new order with the same ID clears the completed flag
        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(1);
        dispatcher.sendOrder("TestCo:AGV001", order);

        assertThat(ctx.isCompletedOrder("order-1")).isFalse();
    }

    // ============ reconnectAttempts on VehicleContext ============

    @Test
    void reconnectAttempts_incrementAndReset() {
        assertThat(ctx.getReconnectAttempts()).isEqualTo(0);

        assertThat(ctx.incrementReconnectAttempts()).isEqualTo(1);
        assertThat(ctx.incrementReconnectAttempts()).isEqualTo(2);
        assertThat(ctx.getReconnectAttempts()).isEqualTo(2);

        ctx.resetReconnectAttempts();
        assertThat(ctx.getReconnectAttempts()).isEqualTo(0);
    }
}
