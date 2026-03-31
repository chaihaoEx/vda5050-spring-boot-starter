package com.navasmart.vda5050.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vda5050PropertiesTest {

    @Test
    void defaultMqttConfig() {
        Vda5050Properties props = new Vda5050Properties();
        Vda5050Properties.MqttConfig mqtt = props.getMqtt();

        assertEquals("localhost", mqtt.getHost());
        assertEquals(1883, mqtt.getPort());
        assertEquals("tcp", mqtt.getTransport());
        assertEquals(60, mqtt.getKeepAlive());
        assertEquals("", mqtt.getUsername());
        assertEquals("", mqtt.getPassword());
        assertTrue(mqtt.isCleanSession());
        assertEquals("vda5050", mqtt.getClientIdPrefix());
        assertEquals("uagv", mqtt.getInterfaceName());
        assertEquals("v2", mqtt.getMajorVersion());
        assertEquals("2.0.0", mqtt.getProtocolVersion());
    }

    @Test
    void defaultProxyConfig() {
        Vda5050Properties props = new Vda5050Properties();
        Vda5050Properties.ProxyConfig proxy = props.getProxy();

        assertFalse(proxy.isEnabled());
        assertEquals(1000, proxy.getHeartbeatIntervalMs());
        assertEquals(200, proxy.getOrderLoopIntervalMs());
        assertEquals(300000, proxy.getNavigationTimeoutMs());
        assertEquals(120000, proxy.getActionTimeoutMs());
        assertNotNull(proxy.getVehicles());
        assertTrue(proxy.getVehicles().isEmpty());
    }

    @Test
    void defaultServerConfig() {
        Vda5050Properties props = new Vda5050Properties();
        Vda5050Properties.ServerConfig server = props.getServer();

        assertFalse(server.isEnabled());
        assertEquals(60000, server.getStateTimeoutMs());
        assertEquals(30000, server.getConnectionCheckMs());
        assertNotNull(server.getVehicles());
        assertTrue(server.getVehicles().isEmpty());
    }

    @Test
    void vehicleConfigDefaults() {
        Vda5050Properties.VehicleConfig vc = new Vda5050Properties.VehicleConfig();
        assertNull(vc.getManufacturer());
        assertNull(vc.getSerialNumber());
        assertEquals("FORKLIFT", vc.getRobotType());
    }

    @Test
    void vehicleConfigSetters() {
        Vda5050Properties.VehicleConfig vc = new Vda5050Properties.VehicleConfig();
        vc.setManufacturer("TestCo");
        vc.setSerialNumber("AGV001");
        vc.setRobotType("CARRIER");

        assertEquals("TestCo", vc.getManufacturer());
        assertEquals("AGV001", vc.getSerialNumber());
        assertEquals("CARRIER", vc.getRobotType());
    }
}
