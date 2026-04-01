package com.navasmart.vda5050.mqtt;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Vda5050Properties.MqttConfig#resolveScheme()} which determines
 * the MQTT URI scheme based on transport type and SSL configuration.
 */
class SslConfigTest {

    @Test
    void tcpWithSslDisabledReturnsTcp() {
        Vda5050Properties.MqttConfig mqtt = new Vda5050Properties.MqttConfig();
        mqtt.setTransport("tcp");
        mqtt.getSsl().setEnabled(false);
        assertThat(mqtt.resolveScheme()).isEqualTo("tcp");
    }

    @Test
    void tcpWithSslEnabledReturnsSsl() {
        Vda5050Properties.MqttConfig mqtt = new Vda5050Properties.MqttConfig();
        mqtt.setTransport("tcp");
        mqtt.getSsl().setEnabled(true);
        assertThat(mqtt.resolveScheme()).isEqualTo("ssl");
    }

    @Test
    void websocketWithSslDisabledReturnsWs() {
        Vda5050Properties.MqttConfig mqtt = new Vda5050Properties.MqttConfig();
        mqtt.setTransport("websocket");
        mqtt.getSsl().setEnabled(false);
        assertThat(mqtt.resolveScheme()).isEqualTo("ws");
    }

    @Test
    void websocketWithSslEnabledReturnsWss() {
        Vda5050Properties.MqttConfig mqtt = new Vda5050Properties.MqttConfig();
        mqtt.setTransport("websocket");
        mqtt.getSsl().setEnabled(true);
        assertThat(mqtt.resolveScheme()).isEqualTo("wss");
    }
}
