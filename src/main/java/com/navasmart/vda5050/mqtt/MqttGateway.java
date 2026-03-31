package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.model.*;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final MqttTopicResolver topicResolver;

    public MqttGateway(MqttClient mqttClient, ObjectMapper objectMapper,
                       MqttTopicResolver topicResolver) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
    }

    public void publish(String topic, Object payload, int qos, boolean retained) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(qos);
            message.setRetained(retained);
            mqttClient.publish(topic, message);
        } catch (MqttException e) {
            log.error("Failed to publish to topic {}: {}", topic, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to serialize payload for topic {}: {}", topic, e.getMessage(), e);
        }
    }

    // Proxy mode convenience methods
    public void publishState(String manufacturer, String serialNumber, AgvState state) {
        publish(topicResolver.stateTopic(manufacturer, serialNumber), state, 0, false);
    }

    public void publishConnection(String manufacturer, String serialNumber, Connection connection) {
        publish(topicResolver.connectionTopic(manufacturer, serialNumber), connection, 1, true);
    }

    public void publishFactsheet(String manufacturer, String serialNumber, Factsheet factsheet) {
        publish(topicResolver.factsheetTopic(manufacturer, serialNumber), factsheet, 0, false);
    }

    // Server mode convenience methods
    public void publishOrder(String manufacturer, String serialNumber, Order order) {
        publish(topicResolver.orderTopic(manufacturer, serialNumber), order, 0, false);
    }

    public void publishInstantActions(String manufacturer, String serialNumber,
                                      InstantActions instantActions) {
        publish(topicResolver.instantActionsTopic(manufacturer, serialNumber), instantActions, 0, false);
    }
}
