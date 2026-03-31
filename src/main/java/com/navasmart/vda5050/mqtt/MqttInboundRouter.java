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
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * MQTT 入站消息路由器，实现 {@link MqttCallback} 接口，负责将收到的消息按 Topic 后缀分发到对应的处理器。
 *
 * <h3>路由规则</h3>
 * <p>收到消息后，提取 Topic 的最后一段后缀，并根据后缀分发：</p>
 * <ul>
 *   <li>{@code order} -> {@link #setOrderHandler(BiConsumer)}（Proxy 模式接收主控下发的订单）</li>
 *   <li>{@code instantActions} -> {@link #setInstantActionsHandler(BiConsumer)}（Proxy 模式接收即时动作）</li>
 *   <li>{@code state} -> {@link #setStateHandler(BiConsumer)}（Server 模式接收 AGV 状态）</li>
 *   <li>{@code connection} -> {@link #setConnectionHandler(BiConsumer)}（Server 模式接收连接状态）</li>
 *   <li>{@code factsheet} -> {@link #setFactsheetHandler(BiConsumer)}（Server 模式接收 Factsheet）</li>
 * </ul>
 *
 * <p>如果对应 handler 未设置（为 null），则静默忽略该类消息。</p>
 *
 * <p>当 MQTT 连接断开时，会通知所有通过 {@link #addConnectionLostListener(Runnable)} 注册的监听器。</p>
 */
@Component
public class MqttInboundRouter implements MqttCallback {

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

    public MqttInboundRouter(ObjectMapper objectMapper, MqttTopicResolver topicResolver,
                             VehicleRegistry vehicleRegistry) {
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
    }

    /**
     * 收到 MQTT 消息时的回调，按 Topic 后缀路由到对应的 {@link BiConsumer} 处理器。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从 Topic 中提取后缀和车辆标识</li>
     *   <li>在 {@link VehicleRegistry} 中查找对应的 {@link VehicleContext}</li>
     *   <li>将 JSON payload 反序列化为对应模型对象</li>
     *   <li>调用已注册的 handler 进行业务处理</li>
     * </ol>
     * </p>
     *
     * @param topic   消息的 MQTT Topic
     * @param message MQTT 消息体
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

            // 根据 Topic 后缀分发到对应的处理器
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
     *
     * @param cause 导致连接丢失的异常
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
        connectionLostListeners.forEach(Runnable::run);
    }

    /**
     * 消息发送完成回调（本实现中无需处理）。
     *
     * @param token 发送令牌
     */
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

    /**
     * 添加 MQTT 连接丢失监听器。
     *
     * @param listener 连接丢失时执行的回调
     */
    public void addConnectionLostListener(Runnable listener) { this.connectionLostListeners.add(listener); }
}
