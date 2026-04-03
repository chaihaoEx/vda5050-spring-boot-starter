package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 2 correctness fixes:
 * H1 — adapter callbacks must execute outside VehicleContext lock
 * H2 — action timeout detection uses timedOutActionIds instead of string matching
 */
class Phase2CorrectnessTest {

    private VehicleRegistry vehicleRegistry;
    private ErrorAggregator errorAggregator;
    private ActionHandlerRegistry actionHandlerRegistry;
    private Vda5050Properties properties;
    private ApplicationEventPublisher eventPublisher;
    private ProxyOrderExecutor executor;

    private VehicleContext ctx;

    // Track adapter calls and whether lock was held at call time
    private final CopyOnWriteArrayList<String> navigationCancelCalls = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> actionCancelCalls = new CopyOnWriteArrayList<>();
    private final AtomicBoolean lockHeldDuringNavigationCancel = new AtomicBoolean(false);
    private final AtomicBoolean lockHeldDuringActionCancel = new AtomicBoolean(false);

    private Vda5050ProxyVehicleAdapter adapter;

    @BeforeEach
    void setUp() {
        ctx = new VehicleContext("TestCo", "AGV001");
        ctx.setProxyMode(true);

        vehicleRegistry = mock(VehicleRegistry.class);
        when(vehicleRegistry.getProxyVehicles()).thenReturn(List.of(ctx));

        errorAggregator = new ErrorAggregator(new Vda5050ErrorFactory());
        actionHandlerRegistry = new ActionHandlerRegistry();
        eventPublisher = mock(ApplicationEventPublisher.class);

        properties = new Vda5050Properties();
        properties.getProxy().setActionTimeoutMs(100);
        properties.getProxy().setNavigationTimeoutMs(100);

        // Custom adapter that records whether lock is held during callback
        adapter = new Vda5050ProxyVehicleAdapter() {
            @Override
            public CompletableFuture<NavigationResult> onNavigate(String vehicleId,
                    com.navasmart.vda5050.model.Node targetNode,
                    List<com.navasmart.vda5050.model.Node> waypoints,
                    List<com.navasmart.vda5050.model.Edge> edges) {
                return CompletableFuture.completedFuture(NavigationResult.success());
            }

            @Override
            public void onNavigationCancel(String vehicleId) {
                navigationCancelCalls.add(vehicleId);
                // Check if we can acquire the lock — if we can't, it's held by the caller
                boolean acquired = false;
                try {
                    acquired = ctx.tryLock(0, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!acquired) {
                    lockHeldDuringNavigationCancel.set(true);
                } else {
                    ctx.unlock();
                }
            }

            @Override
            public CompletableFuture<ActionResult> onActionExecute(String vehicleId,
                    com.navasmart.vda5050.model.Action action) {
                // Return a future that never completes (simulates long-running action)
                return new CompletableFuture<>();
            }

            @Override
            public void onActionCancel(String vehicleId, String actionId) {
                actionCancelCalls.add(actionId);
                boolean acquired = false;
                try {
                    acquired = ctx.tryLock(0, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!acquired) {
                    lockHeldDuringActionCancel.set(true);
                } else {
                    ctx.unlock();
                }
            }

            @Override public void onPause(String vehicleId) {}
            @Override public void onResume(String vehicleId) {}
            @Override public void onOrderCancel(String vehicleId) {}
        };

        executor = new ProxyOrderExecutor(vehicleRegistry, errorAggregator,
                actionHandlerRegistry, adapter, properties, eventPublisher);
    }

    // ============ H1: handleFatalError calls adapter outside lock ============

    @Test
    void handleFatalError_callsNavigationCancelOutsideLock() {
        // Set up a vehicle with a fatal error
        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);

            Order order = createSimpleOrder("order-1", 1);
            ctx.setCurrentOrder(order);
        } finally {
            ctx.unlock();
        }

        // Add a fatal error
        errorAggregator.addFatalError(ctx, "Test error", "testError");

        // Execute — this should call handleFatalError and then onNavigationCancel outside lock
        executor.execute();

        assertThat(navigationCancelCalls).hasSize(1);
        assertThat(lockHeldDuringNavigationCancel.get())
                .as("Lock should NOT be held during onNavigationCancel callback")
                .isFalse();
    }

    @Test
    void navigationTimeout_callsNavigationCancelOutsideLock() {
        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(false);
            ctx.setNavigationStartTime(1); // Long ago → will timeout
            ctx.setCurrentNodeIndex(0);

            Order order = createSimpleOrder("order-1", 1);
            ctx.setCurrentOrder(order);
        } finally {
            ctx.unlock();
        }

        // Execute — navigation timeout should trigger handleFatalError
        executor.execute();

        assertThat(navigationCancelCalls).hasSize(1);
        assertThat(lockHeldDuringNavigationCancel.get())
                .as("Lock should NOT be held during onNavigationCancel callback")
                .isFalse();
    }

    // ============ H1: onActionCancel called outside lock ============

    @Test
    void actionTimeout_callsActionCancelOutsideLock() throws InterruptedException {
        // Set up a running action that will timeout
        Action action = new Action();
        action.setActionId("act-1");
        action.setActionType("testAction");
        action.setBlockingType(BlockingType.HARD.getValue());

        ActionState actionState = new ActionState();
        actionState.setActionId("act-1");
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());

        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);
        node.setActions(List.of(action));

        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(0);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
            ctx.getAgvState().setActionStates(List.of(actionState));
            // Action started long ago → will timeout
            ctx.putActionStartTime("act-1", 1);
        } finally {
            ctx.unlock();
        }

        executor.execute();

        assertThat(actionCancelCalls).contains("act-1");
        assertThat(lockHeldDuringActionCancel.get())
                .as("Lock should NOT be held during onActionCancel callback")
                .isFalse();
    }

    // ============ H2: timedOutActionIds tracking ============

    @Test
    void actionTimeout_marksActionInTimedOutSet() {
        Action action = new Action();
        action.setActionId("act-1");
        action.setActionType("testAction");
        action.setBlockingType(BlockingType.HARD.getValue());

        ActionState actionState = new ActionState();
        actionState.setActionId("act-1");
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());

        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);
        node.setActions(List.of(action));

        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(0);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
            ctx.getAgvState().setActionStates(List.of(actionState));
            ctx.putActionStartTime("act-1", 1);
        } finally {
            ctx.unlock();
        }

        executor.execute();

        ctx.lock();
        try {
            assertThat(ctx.isTimedOutAction("act-1"))
                    .as("Timed out action should be tracked in timedOutActionIds set")
                    .isTrue();
        } finally {
            ctx.unlock();
        }
    }

    // ============ helpers ============

    private Order createSimpleOrder(String orderId, int orderUpdateId) {
        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);
        node.setActions(new ArrayList<>());

        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(orderUpdateId);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());
        return order;
    }
}
