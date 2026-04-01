package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * MQTT 入站消息路由器，实现 {@link MqttCallbackExtended} 接口，负责将收到的消息按 Topic 后缀分发到对应的处理器。
 *
 * <h2>路由规则</h2>
 * <p>收到消息后，提取 Topic 的最后一段后缀，并根据后缀分发：</p>
 * <ul>
 *   <li>{@code order} -> {@link #setOrderHandler(BiConsumer)}（Proxy 模式接收主控下发的订单）</li>
 *   <li>{@code instantActions} -> {@link #setInstantActionsHandler(BiConsumer)}（Proxy 模式接收即时动作）</li>
 *   <li>{@code state} -> {@link #setStateHandler(BiConsumer)}（Server 模式接收 AGV 状态）</li>
 *   <li>{@code connection} -> {@link #setConnectionHandler(BiConsumer)}（Server 模式接收连接状态）</li>
 *   <li>{@code factsheet} -> {@link #setFactsheetHandler(BiConsumer)}（Server 模式接收 Factsheet）</li>
 * </ul>
 *
 * <h2>重连支持</h2>
 * <p>实现 {@link MqttCallbackExtended#connectComplete(boolean, String)}，
 * 重连成功后通知所有已注册的重连监听器（用于重新订阅 Topic）。</p>
 */
@Component
public class MqttInboundRouter implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundRouter.class);

    private final ObjectMapper objectMapper;
    private final MqttTopicResolver topicResolver;
    private final VehicleRegistry vehicleRegistry;

    /** Proxy 模式处理器：接收主控下发的 Order */
    private BiConsumer<VehicleContext, Order> orderHandler;

    /** Proxy 模式处理器：接收主控下发的 InstantActions */
    private BiConsumer<VehicleContext, InstantActions> instantActionsHandler;

    /** Server 模式处理器：接收 AGV 上报的 State */
    private BiConsumer<VehicleContext, AgvState> stateHandler;

    /** Server 模式处理器：接收 AGV 上报的 Connection 状态 */
    private BiConsumer<VehicleContext, Connection> connectionHandler;

    /** Server 模式处理器：接收 AGV 上报的 Factsheet */
    private BiConsumer<VehicleContext, Factsheet> factsheetHandler;

    /** 连接丢失监听器列表 */
    private final List<Runnable> connectionLostListeners = new ArrayList<>();

    /** 重连成功监听器列表 */
    private final List<Runnable> reconnectListeners = new ArrayList<>();

    public MqttInboundRouter(ObjectMapper objectMapper, MqttTopicResolver topicResolver,
                             VehicleRegistry vehicleRegistry) {
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
    }

    /**
     * MQTT 连接完成时的回调（首次连接或重连）。
     * 重连时通知所有已注册的重连监听器（用于重新订阅 Topic）。
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            log.info("MQTT reconnected to {}", serverURI);
            reconnectListeners.forEach(Runnable::run);
        }
    }

    /**
     * 收到 MQTT 消息时的回调，按 Topic 后缀路由到对应的 {@link BiConsumer} 处理器。
     */
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

    /**
     * MQTT 连接丢失时的回调。通知所有已注册的连接丢失监听器。
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
        connectionLostListeners.forEach(Runnable::run);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 无需处理
    }

    // ============ Handler 注册方法 ============

    public void setOrderHandler(BiConsumer<VehicleContext, Order> handler) { this.orderHandler = handler; }
    public void setInstantActionsHandler(BiConsumer<VehicleContext, InstantActions> handler) { this.instantActionsHandler = handler; }
    public void setStateHandler(BiConsumer<VehicleContext, AgvState> handler) { this.stateHandler = handler; }
    public void setConnectionHandler(BiConsumer<VehicleContext, Connection> handler) { this.connectionHandler = handler; }
    public void setFactsheetHandler(BiConsumer<VehicleContext, Factsheet> handler) { this.factsheetHandler = handler; }

    public void addConnectionLostListener(Runnable listener) { this.connectionLostListeners.add(listener); }
    public void addReconnectListener(Runnable listener) { this.reconnectListeners.add(listener); }
}
