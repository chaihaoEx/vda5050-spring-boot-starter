package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Component
public class MqttInboundRouter implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundRouter.class);

    private final ObjectMapper objectMapper;
    private final MqttTopicResolver topicResolver;
    private final VehicleRegistry vehicleRegistry;

    // Proxy mode handlers (receive order/instantActions)
    private BiConsumer<VehicleContext, Order> orderHandler;
    private BiConsumer<VehicleContext, InstantActions> instantActionsHandler;

    // Server mode handlers (receive state/connection/factsheet)
    private BiConsumer<VehicleContext, AgvState> stateHandler;
    private BiConsumer<VehicleContext, Connection> connectionHandler;
    private BiConsumer<VehicleContext, Factsheet> factsheetHandler;

    private final List<Runnable> connectionLostListeners = new ArrayList<>();

    public MqttInboundRouter(ObjectMapper objectMapper, MqttTopicResolver topicResolver,
                             VehicleRegistry vehicleRegistry) {
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String suffix = topicResolver.extractTopicSuffix(topic);
            String[] vehicleId = topicResolver.extractVehicleId(topic);
            if (vehicleId == null) {
                log.warn("Cannot extract vehicle ID from topic: {}", topic);
                return;
            }

            VehicleContext ctx = vehicleRegistry.get(vehicleId[0], vehicleId[1]);
            if (ctx == null) {
                log.debug("Received message for unknown vehicle: {}:{}", vehicleId[0], vehicleId[1]);
                return;
            }

            byte[] payload = message.getPayload();

            switch (suffix) {
                case "order" -> {
                    if (orderHandler != null) {
                        Order order = objectMapper.readValue(payload, Order.class);
                        orderHandler.accept(ctx, order);
                    }
                }
                case "instantActions" -> {
                    if (instantActionsHandler != null) {
                        InstantActions actions = objectMapper.readValue(payload, InstantActions.class);
                        instantActionsHandler.accept(ctx, actions);
                    }
                }
                case "state" -> {
                    if (stateHandler != null) {
                        AgvState state = objectMapper.readValue(payload, AgvState.class);
                        stateHandler.accept(ctx, state);
                    }
                }
                case "connection" -> {
                    if (connectionHandler != null) {
                        Connection conn = objectMapper.readValue(payload, Connection.class);
                        connectionHandler.accept(ctx, conn);
                    }
                }
                case "factsheet" -> {
                    if (factsheetHandler != null) {
                        Factsheet fs = objectMapper.readValue(payload, Factsheet.class);
                        factsheetHandler.accept(ctx, fs);
                    }
                }
                default -> log.debug("Unknown topic suffix: {}", suffix);
            }
        } catch (Exception e) {
            log.error("Error processing message from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
        connectionLostListeners.forEach(Runnable::run);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No-op
    }

    // Handler setters
    public void setOrderHandler(BiConsumer<VehicleContext, Order> handler) { this.orderHandler = handler; }
    public void setInstantActionsHandler(BiConsumer<VehicleContext, InstantActions> handler) { this.instantActionsHandler = handler; }
    public void setStateHandler(BiConsumer<VehicleContext, AgvState> handler) { this.stateHandler = handler; }
    public void setConnectionHandler(BiConsumer<VehicleContext, Connection> handler) { this.connectionHandler = handler; }
    public void setFactsheetHandler(BiConsumer<VehicleContext, Factsheet> handler) { this.factsheetHandler = handler; }
    public void addConnectionLostListener(Runnable listener) { this.connectionLostListeners.add(listener); }
}
