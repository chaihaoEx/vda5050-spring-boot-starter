package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 VehicleRegistry 的运行时动态车辆注册/注销功能。
 */
class DynamicVehicleRegistrationTest {

    private VehicleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new VehicleRegistry(new Vda5050Properties());
    }

    @Test
    void registerVehicleCreatesContextWithCorrectFlags() {
        VehicleContext ctx = registry.registerVehicle("Mfg", "bot01", true, false);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getVehicleId()).isEqualTo("Mfg:bot01");
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isFalse();
    }

    @Test
    void registerExistingVehicleUpdatesFlagsWithoutDuplicate() {
        registry.registerVehicle("Mfg", "bot01", true, false);
        assertThat(registry.getAll()).hasSize(1);

        VehicleContext ctx = registry.registerVehicle("Mfg", "bot01", false, true);
        assertThat(registry.getAll()).hasSize(1);
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isTrue();
    }

    @Test
    void unregisterVehicleRemovesAndReturnsContext() {
        registry.registerVehicle("Mfg", "bot01", true, false);
        assertThat(registry.getAll()).hasSize(1);

        VehicleContext removed = registry.unregisterVehicle("Mfg", "bot01");
        assertThat(removed).isNotNull();
        assertThat(removed.getVehicleId()).isEqualTo("Mfg:bot01");
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void unregisterNonExistentVehicleReturnsNull() {
        VehicleContext removed = registry.unregisterVehicle("NoSuch", "vehicle");
        assertThat(removed).isNull();
    }

    @Test
    void getOrCreateReturnsSameInstanceForSameKey() {
        VehicleContext first = registry.getOrCreate("Mfg", "bot01");
        VehicleContext second = registry.getOrCreate("Mfg", "bot01");
        assertThat(first).isSameAs(second);
    }
}
