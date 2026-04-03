package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.VehicleStatus;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine;
import com.navasmart.vda5050.proxy.validation.OrderValidator;
import com.navasmart.vda5050.server.dispatch.InstantActionSender;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import com.navasmart.vda5050.server.callback.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 3 fixes:
 * M4 — cancelOrder blockingType should be HARD
 * M5 — handleCancelOrder clears actionStartTimes and tracks cancelledOrderIds
 * M6 — factsheetRequest publishes MQTT outside lock
 */
class Phase3VisibilitySpecTest {

    private VehicleContext ctx;
    private MqttGateway mqttGateway;
    private ErrorAggregator errorAggregator;
    private ApplicationEventPublisher eventPublisher;
    private Vda5050ProxyVehicleAdapter vehicleAdapter;
    private Vda5050ProxyStateProvider stateProvider;
    private ProxyOrderStateMachine stateMachine;
    private Vda5050Properties properties;

    @BeforeEach
    void setUp() {
        ctx = new VehicleContext("TestCo", "AGV001");
        ctx.setProxyMode(true);

        mqttGateway = mock(MqttGateway.class);
        when(mqttGateway.publishInstantActions(anyString(), anyString(), any())).thenReturn(true);
        when(mqttGateway.publishFactsheet(anyString(), anyString(), any())).thenReturn(true);

        errorAggregator = new ErrorAggregator(new Vda5050ErrorFactory());
        eventPublisher = mock(ApplicationEventPublisher.class);

        vehicleAdapter = new Vda5050ProxyVehicleAdapter() {
            @Override
            public CompletableFuture<NavigationResult> onNavigate(String vehicleId,
                    Node targetNode, List<Node> waypoints,
                    List<com.navasmart.vda5050.model.Edge> edges) {
                return CompletableFuture.completedFuture(NavigationResult.success());
            }
            @Override
            public CompletableFuture<ActionResult> onActionExecute(String vehicleId,
                    Action action) {
                return new CompletableFuture<>();
            }
            @Override public void onPause(String vehicleId) {}
            @Override public void onResume(String vehicleId) {}
            @Override public void onOrderCancel(String vehicleId) {}
            @Override public void onNavigationCancel(String vehicleId) {}
            @Override public void onActionCancel(String vehicleId, String actionId) {}
        };

        stateProvider = new Vda5050ProxyStateProvider() {
            @Override
            public VehicleStatus getVehicleStatus(String vehicleId) {
                return new VehicleStatus();
            }
            @Override
            public Factsheet getFactsheet(String vehicleId) {
                Factsheet fs = new Factsheet();
                return fs;
            }
        };

        properties = new Vda5050Properties();

        stateMachine = new ProxyOrderStateMachine(
                errorAggregator, vehicleAdapter, stateProvider,
                mqttGateway, eventPublisher, new OrderValidator());
    }

    // ============ M4: cancelOrder blockingType should be HARD ============

    @Test
    void cancelOrder_usesHardBlockingType() {
        VehicleRegistry registry = new VehicleRegistry(properties);
        VehicleContext serverCtx = registry.getOrCreate("TestCo", "AGV001");
        serverCtx.setServerMode(true);

        InstantActionSender sender = new InstantActionSender(registry, mqttGateway, properties);

        sender.cancelOrder("TestCo:AGV001");

        ArgumentCaptor<com.navasmart.vda5050.model.InstantActions> captor =
                ArgumentCaptor.forClass(com.navasmart.vda5050.model.InstantActions.class);
        verify(mqttGateway).publishInstantActions(eq("TestCo"), eq("AGV001"), captor.capture());

        com.navasmart.vda5050.model.InstantActions sent = captor.getValue();
        assertThat(sent.getInstantActions()).hasSize(1);
        assertThat(sent.getInstantActions().get(0).getBlockingType())
                .isEqualTo(BlockingType.HARD.getValue());
        assertThat(sent.getInstantActions().get(0).getActionType())
                .isEqualTo("cancelOrder");
    }

    @Test
    void pauseVehicle_usesNoneBlockingType() {
        VehicleRegistry registry = new VehicleRegistry(properties);
        VehicleContext serverCtx = registry.getOrCreate("TestCo", "AGV001");
        serverCtx.setServerMode(true);

        InstantActionSender sender = new InstantActionSender(registry, mqttGateway, properties);

        sender.pauseVehicle("TestCo:AGV001");

        ArgumentCaptor<com.navasmart.vda5050.model.InstantActions> captor =
                ArgumentCaptor.forClass(com.navasmart.vda5050.model.InstantActions.class);
        verify(mqttGateway).publishInstantActions(eq("TestCo"), eq("AGV001"), captor.capture());

        assertThat(captor.getValue().getInstantActions().get(0).getBlockingType())
                .isEqualTo(BlockingType.NONE.getValue());
    }

    // ============ M5: handleCancelOrder clears actionStartTimes + cancelledOrderIds ============

    @Test
    void cancelOrder_clearsActionStartTimesAndNavigationStartTime() {
        // Set up a running order with action start times
        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            Order order = createSimpleOrder("order-1", 1);
            ctx.setCurrentOrder(order);
            ctx.putActionStartTime("act-1", System.currentTimeMillis());
            ctx.putActionStartTime("act-2", System.currentTimeMillis());
            ctx.setNavigationStartTime(System.currentTimeMillis());
        } finally {
            ctx.unlock();
        }

        // Send cancel instant action
        InstantActions cancelMsg = new InstantActions();
        Action cancelAction = new Action();
        cancelAction.setActionId("cancel-1");
        cancelAction.setActionType("cancelOrder");
        cancelAction.setBlockingType(BlockingType.HARD.getValue());
        cancelMsg.setInstantActions(List.of(cancelAction));

        stateMachine.receiveInstantActions(ctx, cancelMsg);

        ctx.lock();
        try {
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.IDLE);
            assertThat(ctx.getCurrentOrder()).isNull();
            assertThat(ctx.getActionStartTimes()).isEmpty();
            assertThat(ctx.getNavigationStartTime()).isEqualTo(0);
        } finally {
            ctx.unlock();
        }
    }

    @Test
    void cancelOrder_addsCancelledOrderId() {
        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setCurrentOrder(createSimpleOrder("order-42", 1));
        } finally {
            ctx.unlock();
        }

        InstantActions cancelMsg = new InstantActions();
        Action cancelAction = new Action();
        cancelAction.setActionId("cancel-1");
        cancelAction.setActionType("cancelOrder");
        cancelAction.setBlockingType(BlockingType.HARD.getValue());
        cancelMsg.setInstantActions(List.of(cancelAction));

        stateMachine.receiveInstantActions(ctx, cancelMsg);

        ctx.lock();
        try {
            assertThat(ctx.isCancelledOrder("order-42")).isTrue();
        } finally {
            ctx.unlock();
        }
    }

    @Test
    void newOrder_clearsCancelledOrderIds() {
        ctx.lock();
        try {
            ctx.addCancelledOrderId("old-order");
        } finally {
            ctx.unlock();
        }

        Order newOrder = createSimpleOrder("new-order", 1);
        stateMachine.receiveOrder(ctx, newOrder);

        ctx.lock();
        try {
            assertThat(ctx.isCancelledOrder("old-order")).isFalse();
        } finally {
            ctx.unlock();
        }
    }

    // ============ M6: factsheetRequest publishes outside lock ============

    @Test
    void factsheetRequest_publishesOutsideLock() {
        // Track whether lock was held during publishFactsheet
        final boolean[] lockHeldDuringPublish = {false};
        MqttGateway spyGateway = mock(MqttGateway.class);
        when(spyGateway.publishFactsheet(anyString(), anyString(), any())).thenAnswer(invocation -> {
            boolean acquired = false;
            try {
                acquired = ctx.tryLock(0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!acquired) {
                lockHeldDuringPublish[0] = true;
            } else {
                ctx.unlock();
            }
            return true;
        });

        ProxyOrderStateMachine sm = new ProxyOrderStateMachine(
                errorAggregator, vehicleAdapter, stateProvider,
                spyGateway, eventPublisher, new OrderValidator());

        InstantActions msg = new InstantActions();
        Action fsAction = new Action();
        fsAction.setActionId("fs-1");
        fsAction.setActionType("factsheetRequest");
        fsAction.setBlockingType(BlockingType.NONE.getValue());
        msg.setInstantActions(List.of(fsAction));

        sm.receiveInstantActions(ctx, msg);

        verify(spyGateway).publishFactsheet(eq("TestCo"), eq("AGV001"), any(Factsheet.class));
        assertThat(lockHeldDuringPublish[0])
                .as("Lock should NOT be held during MQTT publishFactsheet")
                .isFalse();
    }

    @Test
    void factsheetRequest_setsActionStatusFinished() {
        InstantActions msg = new InstantActions();
        Action fsAction = new Action();
        fsAction.setActionId("fs-1");
        fsAction.setActionType("factsheetRequest");
        fsAction.setBlockingType(BlockingType.NONE.getValue());
        msg.setInstantActions(List.of(fsAction));

        stateMachine.receiveInstantActions(ctx, msg);

        ctx.lock();
        try {
            ActionState as = ctx.getAgvState().getActionStates().stream()
                    .filter(s -> "fs-1".equals(s.getActionId()))
                    .findFirst().orElse(null);
            assertThat(as).isNotNull();
            assertThat(as.getActionStatus()).isEqualTo(ActionStatus.FINISHED.getValue());
        } finally {
            ctx.unlock();
        }
    }

    // ============ MEDIUM-1: onOrderCancel called outside lock ============

    @Test
    void cancelOrder_callsAdapterOutsideLock() {
        final boolean[] lockHeldDuringCancel = {false};

        Vda5050ProxyVehicleAdapter trackingAdapter = new Vda5050ProxyVehicleAdapter() {
            @Override
            public CompletableFuture<NavigationResult> onNavigate(String vehicleId,
                    Node targetNode, List<Node> waypoints,
                    List<com.navasmart.vda5050.model.Edge> edges) {
                return CompletableFuture.completedFuture(NavigationResult.success());
            }
            @Override
            public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
                return new CompletableFuture<>();
            }
            @Override public void onPause(String vehicleId) {}
            @Override public void onResume(String vehicleId) {}
            @Override
            public void onOrderCancel(String vehicleId) {
                boolean acquired = false;
                try {
                    acquired = ctx.tryLock(0, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!acquired) {
                    lockHeldDuringCancel[0] = true;
                } else {
                    ctx.unlock();
                }
            }
            @Override public void onNavigationCancel(String vehicleId) {}
            @Override public void onActionCancel(String vehicleId, String actionId) {}
        };

        ProxyOrderStateMachine sm = new ProxyOrderStateMachine(
                errorAggregator, trackingAdapter, stateProvider,
                mqttGateway, eventPublisher, new OrderValidator());

        ctx.lock();
        try {
            ctx.setClientState(ProxyClientState.RUNNING);
            ctx.setCurrentOrder(createSimpleOrder("order-1", 1));
        } finally {
            ctx.unlock();
        }

        InstantActions cancelMsg = new InstantActions();
        Action cancelAction = new Action();
        cancelAction.setActionId("cancel-1");
        cancelAction.setActionType("cancelOrder");
        cancelAction.setBlockingType(BlockingType.HARD.getValue());
        cancelMsg.setInstantActions(List.of(cancelAction));

        sm.receiveInstantActions(ctx, cancelMsg);

        assertThat(lockHeldDuringCancel[0])
                .as("Lock should NOT be held during onOrderCancel adapter callback")
                .isFalse();

        ctx.lock();
        try {
            assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.IDLE);
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
