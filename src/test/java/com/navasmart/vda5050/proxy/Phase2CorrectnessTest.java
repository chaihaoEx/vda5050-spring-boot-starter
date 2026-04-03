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

    /** Controllable future for action execution — tests can complete it after timeout */
    private volatile CompletableFuture<ActionResult> actionFuture;

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
                actionFuture = new CompletableFuture<>();
                return actionFuture;
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
        Order order = createOrderWithAction("order-1", 0, "act-1", "testAction", BlockingType.HARD.getValue());
        ActionState actionState = new ActionState();
        actionState.setActionId("act-1");
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());

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

        assertThat(actionCancelCalls).contains("act-1");
        assertThat(lockHeldDuringActionCancel.get())
                .as("Lock should NOT be held during onActionCancel callback")
                .isFalse();
    }

    // ============ H2: timedOutActionIds tracking ============

    @Test
    void actionTimeout_marksActionInTimedOutSet() {
        Order order = createOrderWithAction("order-1", 0, "act-1", "testAction", BlockingType.HARD.getValue());
        ActionState actionState = new ActionState();
        actionState.setActionId("act-1");
        actionState.setActionStatus(ActionStatus.RUNNING.getValue());

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

    @Test
    void asyncCallback_doesNotOverwriteTimedOutAction() {
        Order order = createOrderWithAction("order-1", 0, "act-1", "testAction", BlockingType.HARD.getValue());
        ActionState actionState = new ActionState();
        actionState.setActionId("act-1");
        actionState.setActionStatus(ActionStatus.WAITING.getValue());

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
            ctx.getAgvState().setActionStates(new ArrayList<>(List.of(actionState)));
        } finally {
            ctx.unlock();
        }

        // Step 1: first execute() starts the action (WAITING → RUNNING)
        executor.execute();
        assertThat(actionFuture).as("Action future should be created").isNotNull();

        // Step 2: simulate timeout by backdating the start time
        ctx.lock();
        try {
            ctx.putActionStartTime("act-1", 1); // long ago → will timeout
        } finally {
            ctx.unlock();
        }

        // Step 3: execute() detects timeout → marks FAILED + adds to timedOutActionIds
        executor.execute();

        ctx.lock();
        try {
            ActionState as = ctx.getAgvState().getActionStates().stream()
                    .filter(s -> "act-1".equals(s.getActionId()))
                    .findFirst().orElseThrow();
            assertThat(as.getActionStatus()).isEqualTo(ActionStatus.FAILED.getValue());
            assertThat(ctx.isTimedOutAction("act-1")).isTrue();
        } finally {
            ctx.unlock();
        }

        // Step 4: async callback arrives with success — should NOT overwrite FAILED
        // complete() executes whenComplete synchronously on the calling thread
        actionFuture.complete(ActionResult.success());

        ctx.lock();
        try {
            ActionState as = ctx.getAgvState().getActionStates().stream()
                    .filter(s -> "act-1".equals(s.getActionId()))
                    .findFirst().orElseThrow();
            assertThat(as.getActionStatus())
                    .as("Timed-out action status should remain FAILED after async callback")
                    .isEqualTo(ActionStatus.FAILED.getValue());
        } finally {
            ctx.unlock();
        }
    }

    // ============ MEDIUM-3: handleFatalError clears currentOrder ============

    @Test
    void handleFatalError_clearsCurrentOrder() {
        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(createSimpleOrder("order-1", 1));
        } finally {
            ctx.unlock();
        }

        errorAggregator.addFatalError(ctx, "Test error", "testError");
        executor.execute();

        ctx.lock();
        try {
            assertThat(ctx.getCurrentOrder())
                    .as("currentOrder should be null after fatal error")
                    .isNull();
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.IDLE);
        } finally {
            ctx.unlock();
        }
    }

    // ============ helpers ============

    private Order createSimpleOrder(String orderId, int orderUpdateId) {
        return createOrderWithAction(orderId, orderUpdateId, null, null, null);
    }

    private Order createOrderWithAction(String orderId, int orderUpdateId,
                                         String actionId, String actionType, String blockingType) {
        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);

        if (actionId != null) {
            Action action = new Action();
            action.setActionId(actionId);
            action.setActionType(actionType);
            action.setBlockingType(blockingType);
            node.setActions(List.of(action));
        } else {
            node.setActions(new ArrayList<>());
        }

        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(orderUpdateId);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());
        return order;
    }
}
