package com.navasmart.vda5050.mqtt;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttTopicResolverTest {

    private MqttTopicResolver resolver;

    @BeforeEach
    void setUp() {
        Vda5050Properties props = new Vda5050Properties();
        // 使用默认配置: interfaceName=uagv, majorVersion=v2
        resolver = new MqttTopicResolver(props);
    }

    @Test
    void buildPrefix() {
        assertEquals("uagv/v2/RobotCo/AGV001", resolver.buildPrefix("RobotCo", "AGV001"));
    }

    @Test
    void orderTopic() {
        assertEquals("uagv/v2/RobotCo/AGV001/order", resolver.orderTopic("RobotCo", "AGV001"));
    }

    @Test
    void instantActionsTopic() {
        assertEquals("uagv/v2/RobotCo/AGV001/instantActions",
                resolver.instantActionsTopic("RobotCo", "AGV001"));
    }

    @Test
    void stateTopic() {
        assertEquals("uagv/v2/RobotCo/AGV001/state", resolver.stateTopic("RobotCo", "AGV001"));
    }

    @Test
    void connectionTopic() {
        assertEquals("uagv/v2/RobotCo/AGV001/connection",
                resolver.connectionTopic("RobotCo", "AGV001"));
    }

    @Test
    void factsheetTopic() {
        assertEquals("uagv/v2/RobotCo/AGV001/factsheet",
                resolver.factsheetTopic("RobotCo", "AGV001"));
    }

    @Test
    void extractTopicSuffix() {
        assertEquals("order", resolver.extractTopicSuffix("uagv/v2/RobotCo/AGV001/order"));
        assertEquals("state", resolver.extractTopicSuffix("uagv/v2/RobotCo/AGV001/state"));
        assertEquals("noSlash", resolver.extractTopicSuffix("noSlash"));
    }

    @Test
    void extractVehicleId() {
        String[] id = resolver.extractVehicleId("uagv/v2/RobotCo/AGV001/order");
        assertNotNull(id);
        assertEquals("RobotCo", id[0]);
        assertEquals("AGV001", id[1]);
    }

    @Test
    void extractVehicleId_invalidTopic() {
        assertNull(resolver.extractVehicleId("too/short"));
        assertNull(resolver.extractVehicleId("only"));
    }
}
