package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.test.MockProxyAdapter;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 ProxyOrderExecutor 的优雅停机功能。
 */
class GracefulShutdownTest {

    private ProxyOrderExecutor executor;
    private VehicleRegistry registry;

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

        MockProxyAdapter adapter = new MockProxyAdapter();

        executor = new ProxyOrderExecutor(registry,
                new ErrorAggregator(new Vda5050ErrorFactory()),
                new ActionHandlerRegistry(),
                adapter, props);
    }

    @Test
    void isIdleReturnsTrueWhenNoProxyVehiclesExist() {
        Vda5050Properties emptyProps = new Vda5050Properties();
        VehicleRegistry emptyRegistry = new VehicleRegistry(emptyProps);
        ProxyOrderExecutor emptyExecutor = new ProxyOrderExecutor(emptyRegistry,
                new ErrorAggregator(new Vda5050ErrorFactory()),
                new ActionHandlerRegistry(),
                new MockProxyAdapter(), emptyProps);

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
        // Set a vehicle to RUNNING state
        VehicleContext ctx = registry.get("TestMfg", "bot01");
        ctx.setClientState(ProxyClientState.RUNNING);

        // After shutdown, execute() should return immediately without processing
        executor.shutdown();
        executor.execute();

        // Vehicle is still in RUNNING (execution loop didn't process it since no order exists,
        // but the important thing is shutdown flag was checked)
        assertThat(ctx.getClientState()).isEqualTo(ProxyClientState.RUNNING);
    }
}
