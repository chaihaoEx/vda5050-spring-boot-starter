package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.event.vehicle.VehicleRegistryEvent;
import com.navasmart.vda5050.event.vehicle.VehicleUnRegistryEvent;
import com.navasmart.vda5050.listener.VehicleRegistryListener;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * 测试 {@link VehicleRegistry} 初始化与 {@link VehicleRegistryListener} 对外部
 * {@link VehicleRegistryEvent} 的 MQTT 编排。
 */
@ExtendWith(MockitoExtension.class)
class DynamicVehicleRegistrationTest {

    @Mock
    private MqttConnectionManager mqttConnectionManager;

    private Vda5050Properties properties;
    private VehicleRegistry registry;
    private VehicleRegistryListener listener;

    @BeforeEach
    void setUp() {
        properties = new Vda5050Properties();
        registry = new VehicleRegistry(properties);
        listener = new VehicleRegistryListener(registry, mqttConnectionManager, properties);
    }

    private void publishRegistered(String manufacturer, String serialNumber,
                                   boolean proxyMode, boolean serverMode) throws MqttException {
        listener.onVehicleRegistry(
                new VehicleRegistryEvent(this, manufacturer, serialNumber, proxyMode, serverMode));
    }

    private void publishUnregistered(String manufacturer, String serialNumber,
                                     boolean proxyMode, boolean serverMode) throws MqttException {
        listener.onVehicleUnRegistry(
                new VehicleUnRegistryEvent(this, manufacturer, serialNumber, proxyMode, serverMode));
    }

    @Test
    void init_registersVehiclesFromConfigWithoutMqtt() {
        properties.getProxy().setEnabled(true);
        Vda5050Properties.VehicleConfig proxyVehicle = vehicleConfig("ProxyMfg", "proxy01");
        properties.getProxy().setVehicles(List.of(proxyVehicle));

        properties.getServer().setEnabled(true);
        Vda5050Properties.VehicleConfig serverVehicle = vehicleConfig("ServerMfg", "server01");
        properties.getServer().setVehicles(List.of(serverVehicle));

        registry.init();

        assertThat(registry.getProxyVehicles()).extracting(VehicleContext::getVehicleId)
                .containsExactly("ProxyMfg:proxy01");
        assertThat(registry.getServerVehicles()).extracting(VehicleContext::getVehicleId)
                .containsExactly("ServerMfg:server01");
        verifyNoInteractions(mqttConnectionManager);
    }

    @Test
    void init_sameVehicleInProxyAndServerSharesSingleContext() {
        properties.getProxy().setEnabled(true);
        properties.getServer().setEnabled(true);
        Vda5050Properties.VehicleConfig shared = vehicleConfig("SharedMfg", "dual01");
        properties.getProxy().setVehicles(List.of(shared));
        properties.getServer().setVehicles(List.of(shared));

        registry.init();

        VehicleContext ctx = registry.get("SharedMfg", "dual01");
        assertThat(ctx).isNotNull();
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isTrue();
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void registered_proxyOnly_connectsProxyMqtt() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getProxy().setVehicles(List.of(vehicleConfig("Mfg", "bot01")));

        publishRegistered("Mfg", "bot01", true, false);

        VehicleContext ctx = registry.get("Mfg", "bot01");
        assertThat(ctx.getVehicleId()).isEqualTo("Mfg:bot01");
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isFalse();
        verify(mqttConnectionManager).connectProxyVehicle(same(ctx));
        verify(mqttConnectionManager, never()).subscribeServerVehicle(any());
    }

    @Test
    void registered_serverOnly_subscribesMqtt() throws MqttException {
        properties.getServer().setEnabled(true);
        properties.getServer().setVehicles(List.of(vehicleConfig("Mfg", "bot02")));

        publishRegistered("Mfg", "bot02", false, true);

        VehicleContext ctx = registry.get("Mfg", "bot02");
        assertThat(ctx.isServerMode()).isTrue();
        assertThat(ctx.isProxyMode()).isFalse();
        verify(mqttConnectionManager).subscribeServerVehicle(same(ctx));
        verify(mqttConnectionManager, never()).connectProxyVehicle(any());
    }

    @Test
    void registered_dualMode_invokesBothMqttOperations() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getProxy().setVehicles(List.of(vehicleConfig("Mfg", "bot03")));
        properties.getServer().setEnabled(true);
        properties.getServer().setVehicles(List.of(vehicleConfig("Mfg", "bot03")));

        publishRegistered("Mfg", "bot03", true, true);

        VehicleContext ctx = registry.get("Mfg", "bot03");
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isTrue();
        verify(mqttConnectionManager).connectProxyVehicle(same(ctx));
        verify(mqttConnectionManager).subscribeServerVehicle(same(ctx));
    }

    @Test
    void registered_twice_updatesModes_proxyConnectOnce_serverSubscribeWhenAdded() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getProxy().setVehicles(List.of(vehicleConfig("Mfg", "bot01")));

        publishRegistered("Mfg", "bot01", true, false);
        VehicleContext ctx = registry.get("Mfg", "bot01");
        assertThat(registry.getAll()).hasSize(1);

        properties.getServer().setEnabled(true);
        properties.getServer().setVehicles(List.of(vehicleConfig("Mfg", "bot01")));

        publishRegistered("Mfg", "bot01", false, true);

        assertThat(registry.getAll()).hasSize(1);
        assertThat(ctx.isProxyMode()).isTrue();
        assertThat(ctx.isServerMode()).isTrue();
        verify(mqttConnectionManager).connectProxyVehicle(same(ctx));
        verify(mqttConnectionManager).subscribeServerVehicle(same(ctx));
    }

    @Test
    void registered_noConfiguredRole_skipsMqtt() throws MqttException {
        publishRegistered("Mfg", "bot04", true, true);

        VehicleContext ctx = registry.get("Mfg", "bot04");
        assertThat(ctx.getVehicleId()).isEqualTo("Mfg:bot04");
        assertThat(ctx.isProxyMode()).isFalse();
        assertThat(ctx.isServerMode()).isFalse();
        verifyNoInteractions(mqttConnectionManager);
    }

    @Test
    void unregistered_proxy_disconnectsMqtt() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getProxy().setVehicles(List.of(vehicleConfig("Mfg", "bot01")));

        publishRegistered("Mfg", "bot01", true, false);
        VehicleContext ctx = registry.get("Mfg", "bot01");

        publishUnregistered("Mfg", "bot01", true, false);

        assertThat(ctx.getVehicleId()).isEqualTo("Mfg:bot01");
        assertThat(registry.getAll()).isEmpty();
        verify(mqttConnectionManager).disconnectProxyVehicle(same(ctx));
        verify(mqttConnectionManager, never()).unsubscribeServerVehicle(any());
    }

    @Test
    void unregistered_server_unsubscribesMqtt() throws MqttException {
        properties.getServer().setEnabled(true);
        properties.getServer().setVehicles(List.of(vehicleConfig("Mfg", "bot02")));

        publishRegistered("Mfg", "bot02", false, true);
        VehicleContext ctx = registry.get("Mfg", "bot02");

        publishUnregistered("Mfg", "bot02", false, true);

        verify(mqttConnectionManager).unsubscribeServerVehicle(same(ctx));
        verify(mqttConnectionManager, never()).disconnectProxyVehicle(any());
    }

    @Test
    void unregistered_nonExistent_skipsMqtt() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getServer().setEnabled(true);

        publishUnregistered("NoSuch", "vehicle", true, true);

        verifyNoInteractions(mqttConnectionManager);
    }

    @Test
    void getOrCreateReturnsSameInstanceForSameKey() {
        VehicleContext first = registry.getOrCreate("Mfg", "bot01");
        VehicleContext second = registry.getOrCreate("Mfg", "bot01");

        assertThat(first).isSameAs(second);
        verifyNoInteractions(mqttConnectionManager);
    }

    @Test
    void getProxyAndServerVehicles_filterByMode() throws MqttException {
        properties.getProxy().setEnabled(true);
        properties.getProxy().setVehicles(List.of(vehicleConfig("Mfg", "proxyOnly")));
        properties.getServer().setEnabled(true);
        properties.getServer().setVehicles(List.of(vehicleConfig("Mfg", "serverOnly")));

        publishRegistered("Mfg", "proxyOnly", true, false);
        publishRegistered("Mfg", "serverOnly", false, true);

        assertThat(registry.getProxyVehicles()).extracting(VehicleContext::getVehicleId)
                .containsExactly("Mfg:proxyOnly");
        assertThat(registry.getServerVehicles()).extracting(VehicleContext::getVehicleId)
                .containsExactly("Mfg:serverOnly");
    }

    private static Vda5050Properties.VehicleConfig vehicleConfig(String manufacturer, String serialNumber) {
        Vda5050Properties.VehicleConfig config = new Vda5050Properties.VehicleConfig();
        config.setManufacturer(manufacturer);
        config.setSerialNumber(serialNumber);
        return config;
    }
}
