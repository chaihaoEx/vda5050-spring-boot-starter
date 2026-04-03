package com.navasmart.vda5050.actuator;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 MQTT Health Indicator（包含 M2: 按模式区分健康评估）。
 */
class MqttHealthIndicatorTest {

    @Test
    void healthUpWhenServerConnected() {
        Vda5050Properties props = new Vda5050Properties();
        props.getServer().setEnabled(true);
        props.getServer().getVehicles().add(createVehicleConfig("MfgA", "SN1"));
        VehicleRegistry registry = new VehicleRegistry(props);
        registry.init();

        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        when(manager.isConnected()).thenReturn(true);

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("sharedClient", "connected");
    }

    @Test
    void healthDownWhenServerDisconnected() {
        Vda5050Properties props = new Vda5050Properties();
        props.getServer().setEnabled(true);
        props.getServer().getVehicles().add(createVehicleConfig("MfgA", "SN1"));
        VehicleRegistry registry = new VehicleRegistry(props);
        registry.init();

        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        when(manager.isConnected()).thenReturn(false);

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("sharedClient", "disconnected");
    }

    @Test
    void proxyOnlyMode_noSharedClientDetail() {
        Vda5050Properties props = new Vda5050Properties();
        props.getProxy().setEnabled(true);
        props.getProxy().getVehicles().add(createVehicleConfig("MfgA", "SN1"));
        VehicleRegistry registry = new VehicleRegistry(props);
        registry.init();

        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        // Shared client connected, but no server vehicles → should not appear in details
        when(manager.isConnected()).thenReturn(true);

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        // No server vehicles → no sharedClient detail
        assertThat(health.getDetails()).doesNotContainKey("sharedClient");
        // Proxy vehicle has no MQTT client set → disconnected → DOWN
        assertThat(health.getDetails()).containsEntry("proxy:MfgA:SN1", "disconnected");
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void proxyVehicle_connectedClient_healthUp() {
        Vda5050Properties props = new Vda5050Properties();
        props.getProxy().setEnabled(true);
        props.getProxy().getVehicles().add(createVehicleConfig("MfgA", "SN1"));
        VehicleRegistry registry = new VehicleRegistry(props);
        registry.init();

        // Set a mock connected MQTT client on the proxy vehicle
        VehicleContext ctx = registry.get("MfgA", "SN1");
        MqttClient vehicleClient = mock(MqttClient.class);
        when(vehicleClient.isConnected()).thenReturn(true);
        ctx.setProxyMqttClient(vehicleClient);

        MqttConnectionManager manager = mock(MqttConnectionManager.class);

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("proxy:MfgA:SN1", "connected");
    }

    @Test
    void mixedMode_serverDown_proxyUp_healthDown() {
        Vda5050Properties props = new Vda5050Properties();
        props.getServer().setEnabled(true);
        props.getServer().getVehicles().add(createVehicleConfig("MfgA", "SN1"));
        props.getProxy().setEnabled(true);
        props.getProxy().getVehicles().add(createVehicleConfig("MfgB", "SN2"));
        VehicleRegistry registry = new VehicleRegistry(props);
        registry.init();

        // Proxy vehicle connected
        VehicleContext proxyCtx = registry.get("MfgB", "SN2");
        MqttClient vehicleClient = mock(MqttClient.class);
        when(vehicleClient.isConnected()).thenReturn(true);
        proxyCtx.setProxyMqttClient(vehicleClient);

        // Shared client disconnected
        MqttConnectionManager manager = mock(MqttConnectionManager.class);
        when(manager.isConnected()).thenReturn(false);

        MqttHealthIndicator indicator = new MqttHealthIndicator(manager, registry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("sharedClient", "disconnected");
        assertThat(health.getDetails()).containsEntry("proxy:MfgB:SN2", "connected");
    }

    private Vda5050Properties.VehicleConfig createVehicleConfig(String manufacturer, String serialNumber) {
        Vda5050Properties.VehicleConfig vc = new Vda5050Properties.VehicleConfig();
        vc.setManufacturer(manufacturer);
        vc.setSerialNumber(serialNumber);
        return vc;
    }
}
