package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import com.navasmart.vda5050.proxy.statemachine.ProxyNavigationController;
import com.navasmart.vda5050.proxy.statemachine.ProxyNodeActionDispatcher;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.test.MockProxyAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 ProxyOrderExecutor 的优雅停机功能。
 */
class GracefulShutdownTest {

    private ProxyOrderExecutor executor;
    private VehicleRegistry registry;
    private MockProxyAdapter adapter;

    @BeforeEach
    void setUp() {
        Vda5050Properties props = new Vda5050Properties();
        props.getProxy().setEnabled(true);
        Vda5050Properties.VehicleConfig vc = new Vda5050Properties.VehicleConfig();
        vc.setManufacturer("TestMfg");
        vc.setSerialNumber("bot01");
        props.getProxy().setVehicles(List.of(vc));

        registry = new VehicleRegistry(props);
        registry.init();

        adapter = new MockProxyAdapter();

        ErrorAggregator errorAggregator = new ErrorAggregator(new Vda5050ErrorFactory());
        ProxyNodeActionDispatcher actionDispatcher = new ProxyNodeActionDispatcher(
                new ActionHandlerRegistry(), adapter, props);
        ProxyNavigationController navigationController = new ProxyNavigationController(
                adapter, errorAggregator, actionDispatcher);
        executor = new ProxyOrderExecutor(registry, errorAggregator,
                actionDispatcher, navigationController, adapter, props, event -> {});
    }

    @Test
    void isIdleReturnsTrueWhenNoProxyVehiclesExist() {
        Vda5050Properties emptyProps = new Vda5050Properties();
        VehicleRegistry emptyRegistry = new VehicleRegistry(emptyProps);
        MockProxyAdapter emptyAdapter = new MockProxyAdapter();
        ErrorAggregator emptyErrorAgg = new ErrorAggregator(new Vda5050ErrorFactory());
        ProxyNodeActionDispatcher emptyActionDispatcher = new ProxyNodeActionDispatcher(
                new ActionHandlerRegistry(), emptyAdapter, emptyProps);
        ProxyNavigationController emptyNavController = new ProxyNavigationController(
                emptyAdapter, emptyErrorAgg, emptyActionDispatcher);
        ProxyOrderExecutor emptyExecutor = new ProxyOrderExecutor(emptyRegistry, emptyErrorAgg,
                emptyActionDispatcher, emptyNavController, emptyAdapter, emptyProps, event -> {});

        assertThat(emptyExecutor.isIdle()).isTrue();
    }

    @Test
    void isIdleReturnsTrueWhenAllVehiclesIdle() {
        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void isIdleReturnsFalseWhenVehicleRunning() {
        VehicleContext ctx = registry.get("TestMfg", "bot01");
        ctx.setClientState(ProxyClientState.RUNNING);
        assertThat(executor.isIdle()).isFalse();

        ctx.setClientState(ProxyClientState.IDLE);
        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void shutdownPreventsExecutionLoop() {
        // Set up a vehicle with an order so execute() would normally process it
        VehicleContext ctx = registry.get("TestMfg", "bot01");
        ctx.setClientState(ProxyClientState.RUNNING);
        Order order = new Order();
        order.setOrderId("test-order");
        order.setNodes(new ArrayList<>());
        order.setEdges(new ArrayList<>());
        // Add a node so the executor has work to do
        Node node = new Node();
        node.setNodeId("n1");
        node.setSequenceId(0);
        node.setReleased(true);
        node.setActions(new ArrayList<>());
        order.getNodes().add(node);
        ctx.setCurrentOrder(order);
        ctx.setReachedWaypoint(true);

        // After shutdown, execute() should skip processing
        executor.shutdown();
        executor.execute();

        // The order should still be present (not processed) because shutdown skipped execution
        assertThat(ctx.getCurrentOrder()).isNotNull();
        // Verify no navigation was triggered
        assertThat(adapter.navigateCalls).isEmpty();
    }
}
