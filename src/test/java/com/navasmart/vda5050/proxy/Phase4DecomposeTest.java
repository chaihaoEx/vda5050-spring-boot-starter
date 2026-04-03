package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.EdgeState;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.NodeState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import com.navasmart.vda5050.proxy.statemachine.ProxyNavigationController;
import com.navasmart.vda5050.proxy.statemachine.ProxyNodeActionDispatcher;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.heartbeat.ServerConnectionMonitor;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 4 refactoring:
 * - ProxyOrderExecutor decomposition (ProxyNodeActionDispatcher + ProxyNavigationController)
 * - VehicleContext lock separation (stateLock for proxy, serverStateLock for server)
 * - Circuit breaker semantics (covered by integration/existing tests)
 */
class Phase4DecomposeTest {

    private VehicleRegistry vehicleRegistry;
    private ErrorAggregator errorAggregator;
    private ActionHandlerRegistry actionHandlerRegistry;
    private Vda5050Properties properties;
    private ApplicationEventPublisher eventPublisher;

    private ProxyOrderExecutor executor;

    private VehicleContext ctx;

    private final CopyOnWriteArrayList<String> navigateCalls = new CopyOnWriteArrayList<>();
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

        adapter = new Vda5050ProxyVehicleAdapter() {
            @Override
            public CompletableFuture<NavigationResult> onNavigate(String vehicleId,
                    Node targetNode, List<Node> waypoints, List<Edge> edges) {
                navigateCalls.add(vehicleId);
                return CompletableFuture.completedFuture(NavigationResult.success());
            }
            @Override
            public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
                actionFuture = new CompletableFuture<>();
                return actionFuture;
            }
            @Override public void onPause(String vehicleId) {}
            @Override public void onResume(String vehicleId) {}
            @Override public void onOrderCancel(String vehicleId) {}
            @Override public void onNavigationCancel(String vehicleId) {}
            @Override public void onActionCancel(String vehicleId, String actionId) {}
        };

        ProxyNodeActionDispatcher actionDispatcher = new ProxyNodeActionDispatcher(
                actionHandlerRegistry, adapter, properties);
        ProxyNavigationController navigationController = new ProxyNavigationController(
                adapter, errorAggregator, actionDispatcher);
        executor = new ProxyOrderExecutor(vehicleRegistry, errorAggregator,
                actionDispatcher, navigationController, adapter, properties, eventPublisher);
    }

    // ============ Decomposed executor: HARD action blocks via execute() ============

    @Test
    void execute_startsHardActionAndWaitsForCompletion() {
        Order order = createOrderWithAction("order-1", "act-1", "testAction", BlockingType.HARD.getValue());
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

        // First execute: starts the action (WAITING → RUNNING)
        executor.execute();

        ctx.lock();
        try {
            assertThat(actionState.getActionStatus()).isEqualTo(ActionStatus.RUNNING.getValue());
            // Still RUNNING state — action not yet completed
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.RUNNING);
        } finally {
            ctx.unlock();
        }
    }

    @Test
    void execute_completesOrderWhenNoActions() {
        Order order = createSingleNodeOrder("order-1");

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
            ctx.getAgvState().setNodeStates(new ArrayList<>());
        } finally {
            ctx.unlock();
        }

        executor.execute();

        ctx.lock();
        try {
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.IDLE);
        } finally {
            ctx.unlock();
        }

        verify(eventPublisher).publishEvent(any(com.navasmart.vda5050.event.OrderCompletedEvent.class));
    }

    // ============ Decomposed executor: navigation through execute() ============

    @Test
    void execute_advancesToNextNodeAndTriggersNavigation() {
        Order order = createTwoNodeOrder("order-1");

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
            // Need proper NodeState/EdgeState lists
            NodeState ns1 = new NodeState();
            ns1.setNodeId("node-1");
            ns1.setSequenceId(0);
            ns1.setReleased(true);
            NodeState ns2 = new NodeState();
            ns2.setNodeId("node-2");
            ns2.setSequenceId(2);
            ns2.setReleased(true);
            ctx.getAgvState().setNodeStates(new ArrayList<>(List.of(ns1, ns2)));

            EdgeState es1 = new EdgeState();
            es1.setEdgeId("edge-1");
            es1.setSequenceId(1);
            es1.setReleased(true);
            ctx.getAgvState().setEdgeStates(new ArrayList<>(List.of(es1)));
        } finally {
            ctx.unlock();
        }

        executor.execute();

        // Navigation should have been triggered (and completed since our future is sync)
        assertThat(navigateCalls).hasSize(1);
        assertThat(navigateCalls.get(0)).isEqualTo("TestCo:AGV001");

        ctx.lock();
        try {
            // After navigation completes synchronously, waypoint is reached
            assertThat(ctx.isReachedWaypoint()).isTrue();
            assertThat(ctx.getCurrentNodeIndex()).isEqualTo(1);
        } finally {
            ctx.unlock();
        }
    }

    @Test
    void execute_handlesFatalErrorAndPublishesEvent() {
        Order order = createSingleNodeOrder("order-1");

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setReachedWaypoint(true);
            ctx.setCurrentNodeIndex(0);
            ctx.setCurrentOrder(order);
        } finally {
            ctx.unlock();
        }

        errorAggregator.addFatalError(ctx, "Test fatal", "testError");
        executor.execute();

        ctx.lock();
        try {
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.IDLE);
            assertThat(ctx.getCurrentOrder()).isNull();
        } finally {
            ctx.unlock();
        }

        verify(eventPublisher).publishEvent(any(com.navasmart.vda5050.event.OrderFailedEvent.class));
    }

    // ============ VehicleContext: server lock independence ============

    @Test
    void serverLock_isIndependentFromProxyLock() throws InterruptedException {
        // Acquire proxy lock
        ctx.lock();
        try {
            // Server lock should be independently acquirable
            boolean serverAcquired = ctx.tryLockServer(0, TimeUnit.MILLISECONDS);
            assertThat(serverAcquired)
                    .as("Server lock should be acquirable while proxy lock is held")
                    .isTrue();
            ctx.unlockServer();
        } finally {
            ctx.unlock();
        }

        // Acquire server lock
        ctx.lockServer();
        try {
            // Proxy lock should be independently acquirable
            boolean proxyAcquired = ctx.tryLock(0, TimeUnit.MILLISECONDS);
            assertThat(proxyAcquired)
                    .as("Proxy lock should be acquirable while server lock is held")
                    .isTrue();
            ctx.unlock();
        } finally {
            ctx.unlockServer();
        }
    }

    @Test
    void serverLock_blocksWhenAlreadyHeldByAnotherThread() throws InterruptedException {
        AtomicBoolean acquired = new AtomicBoolean(false);

        ctx.lockServer();
        try {
            Thread t = new Thread(() -> {
                try {
                    acquired.set(ctx.tryLockServer(0, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();
            t.join(1000);

            assertThat(acquired.get())
                    .as("Another thread should NOT be able to acquire server lock")
                    .isFalse();
        } finally {
            ctx.unlockServer();
        }
    }

    @Test
    void agvStateTracker_usesServerLock_notProxyLock() {
        VehicleContext serverCtx = new VehicleContext("ServerCo", "AGV100");
        serverCtx.setServerMode(true);

        VehicleRegistry serverRegistry = mock(VehicleRegistry.class);
        when(serverRegistry.get("ServerCo:AGV100")).thenReturn(serverCtx);

        Vda5050ServerAdapter serverAdapter = mock(Vda5050ServerAdapter.class);
        AgvStateTracker tracker = new AgvStateTracker(serverRegistry, serverAdapter, event -> {});

        // Hold the proxy lock — should NOT block AgvStateTracker (which uses server lock)
        serverCtx.lock();
        try {
            AgvState newState = new AgvState();
            newState.setLastNodeId("n1");
            newState.setLastNodeSequenceId(0);
            newState.setActionStates(new ArrayList<>());
            newState.setErrors(new ArrayList<>());
            newState.setNodeStates(new ArrayList<>());

            tracker.processState("ServerCo:AGV100", newState);

            verify(serverAdapter).onStateUpdate("ServerCo:AGV100", newState);
        } finally {
            serverCtx.unlock();
        }
    }

    @Test
    void serverConnectionMonitor_usesServerLock_notProxyLock() {
        VehicleContext serverCtx = new VehicleContext("ServerCo", "AGV100");
        serverCtx.setServerMode(true);

        Vda5050Properties serverProps = new Vda5050Properties();
        serverProps.getServer().setStateTimeoutMs(1000);

        VehicleRegistry serverRegistry = mock(VehicleRegistry.class);
        when(serverRegistry.get("ServerCo:AGV100")).thenReturn(serverCtx);

        Vda5050ServerAdapter serverAdapter = mock(Vda5050ServerAdapter.class);
        ServerConnectionMonitor monitor = new ServerConnectionMonitor(
                serverRegistry, serverProps, serverAdapter, event -> {});

        Connection conn = new Connection();
        conn.setConnectionState("ONLINE");

        // Hold the proxy lock — should NOT block ServerConnectionMonitor (which uses server lock)
        serverCtx.lock();
        try {
            monitor.processConnection("ServerCo:AGV100", conn);
            verify(serverAdapter).onConnectionStateChanged("ServerCo:AGV100", "ONLINE");
        } finally {
            serverCtx.unlock();
        }
    }

    // ============ helpers ============

    private Order createOrderWithAction(String orderId, String actionId, String actionType, String blockingType) {
        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);

        Action action = new Action();
        action.setActionId(actionId);
        action.setActionType(actionType);
        action.setBlockingType(blockingType);
        node.setActions(List.of(action));

        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(1);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());
        return order;
    }

    private Order createSingleNodeOrder(String orderId) {
        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);
        node.setActions(new ArrayList<>());

        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(1);
        order.setNodes(List.of(node));
        order.setEdges(new ArrayList<>());
        return order;
    }

    private Order createTwoNodeOrder(String orderId) {
        Node node1 = new Node();
        node1.setNodeId("node-1");
        node1.setSequenceId(0);
        node1.setReleased(true);
        node1.setActions(new ArrayList<>());

        Node node2 = new Node();
        node2.setNodeId("node-2");
        node2.setSequenceId(2);
        node2.setReleased(true);
        node2.setActions(new ArrayList<>());

        Edge edge = new Edge();
        edge.setEdgeId("edge-1");
        edge.setSequenceId(1);
        edge.setReleased(true);
        edge.setActions(new ArrayList<>());

        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderUpdateId(1);
        order.setNodes(List.of(node1, node2));
        order.setEdges(List.of(edge));
        return order;
    }
}
