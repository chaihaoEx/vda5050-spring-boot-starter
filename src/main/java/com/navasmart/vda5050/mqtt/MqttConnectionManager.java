package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.enums.ConnectionState;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(MqttConnectionManager.class);

    private final MqttClient mqttClient;
    private final MqttInboundRouter inboundRouter;
    private final MqttTopicResolver topicResolver;
    private final VehicleRegistry vehicleRegistry;
    private final Vda5050Properties properties;
    private final ObjectMapper objectMapper;

    public MqttConnectionManager(MqttClient mqttClient, MqttInboundRouter inboundRouter,
                                 MqttTopicResolver topicResolver, VehicleRegistry vehicleRegistry,
                                 Vda5050Properties properties, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.inboundRouter = inboundRouter;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(properties.getMqtt().isCleanSession());
        options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());

        String username = properties.getMqtt().getUsername();
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }

        // Set LWT for proxy vehicles
        if (properties.getProxy().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
                try {
                    Connection lwt = new Connection();
                    lwt.setConnectionState(ConnectionState.CONNECTIONBROKEN.getValue());
                    lwt.setTimestamp(TimestampUtil.now());
                    lwt.setVersion(properties.getMqtt().getProtocolVersion());
                    lwt.setManufacturer(ctx.getManufacturer());
                    lwt.setSerialNumber(ctx.getSerialNumber());

                    String topic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                    byte[] payload = objectMapper.writeValueAsBytes(lwt);
                    options.setWill(topic, payload, 1, true);
                    // Note: Paho only supports one LWT; for multi-vehicle, use last one
                    // In production, consider separate MqttClient per proxy vehicle
                    break;
                } catch (Exception e) {
                    log.warn("Failed to set LWT for vehicle {}: {}", ctx.getVehicleId(), e.getMessage());
                }
            }
        }

        mqttClient.setCallback(inboundRouter);
        mqttClient.connect(options);
        log.info("Connected to MQTT broker: {}:{}", properties.getMqtt().getHost(), properties.getMqtt().getPort());

        subscribeTopics();
    }

    private void subscribeTopics() throws MqttException {
        // Proxy mode: subscribe to order and instantActions
        if (properties.getProxy().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
                String orderTopic = topicResolver.orderTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String actionsTopic = topicResolver.instantActionsTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                mqttClient.subscribe(orderTopic, 0);
                mqttClient.subscribe(actionsTopic, 0);
                log.info("Proxy subscribed: {}, {}", orderTopic, actionsTopic);
            }
        }

        // Server mode: subscribe to state, connection, factsheet
        if (properties.getServer().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getServerVehicles()) {
                String stateTopic = topicResolver.stateTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String connTopic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String fsTopic = topicResolver.factsheetTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                mqttClient.subscribe(stateTopic, 0);
                mqttClient.subscribe(connTopic, 1);
                mqttClient.subscribe(fsTopic, 0);
                log.info("Server subscribed: {}, {}, {}", stateTopic, connTopic, fsTopic);
            }
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("Disconnected from MQTT broker");
            }
        } catch (MqttException e) {
            log.warn("Error disconnecting from MQTT: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return mqttClient.isConnected();
    }
}
