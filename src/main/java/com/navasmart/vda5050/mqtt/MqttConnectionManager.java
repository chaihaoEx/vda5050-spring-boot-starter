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

/**
 * MQTT 连接管理器，负责与 Broker 的连接建立、断开及 Topic 订阅。
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li>{@link PostConstruct} - 容器启动时自动连接 Broker 并订阅相关 Topic</li>
 *   <li>{@link PreDestroy} - 容器关闭时优雅断开连接</li>
 * </ul>
 *
 * <h2>LWT（Last Will and Testament）配置</h2>
 * <p>为 Proxy 模式车辆配置 LWT 消息：当客户端异常断开时，Broker 自动向 connection Topic
 * 发布 {@code CONNECTIONBROKEN} 状态（QoS 1 + retained），确保主控能感知连接中断。</p>
 * <p><b>注意：</b>Paho MQTT 客户端仅支持一条 LWT 消息。当存在多辆 Proxy 车辆时，
 * 仅为第一辆车设置 LWT。生产环境下建议为每辆 Proxy 车辆使用独立的 MqttClient。</p>
 *
 * <h2>自动重连</h2>
 * <p>通过 {@code MqttConnectOptions.setAutomaticReconnect(true)} 启用 Paho 内置的自动重连机制。</p>
 */
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

    /**
     * 容器启动时连接 MQTT Broker 并订阅相关 Topic。
     *
     * <p>执行步骤：
     * <ol>
     *   <li>配置连接选项（自动重连、cleanSession、keepAlive、认证信息）</li>
     *   <li>为 Proxy 车辆设置 LWT 消息</li>
     *   <li>设置入站消息回调（{@link MqttInboundRouter}）</li>
     *   <li>建立连接</li>
     *   <li>根据 Proxy/Server 模式订阅对应 Topic</li>
     * </ol>
     *
     * @throws MqttException 连接或订阅失败时抛出
     */
    @PostConstruct
    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(properties.getMqtt().isCleanSession());
        options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());

        // 配置认证信息（如果有）
        String username = properties.getMqtt().getUsername();
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }

        // 为 Proxy 车辆配置 LWT（遗嘱消息）
        // 当客户端异常断开时，Broker 会自动发布 CONNECTIONBROKEN 状态到 connection Topic
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
                    // LWT 使用 QoS 1 + retained，确保消息可靠且 Broker 保留最新状态
                    options.setWill(topic, payload, 1, true);
                    // Paho 仅支持单条 LWT，多车辆场景只能设置一辆车的 LWT
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

    /**
     * 根据 Proxy/Server 模式为已注册车辆订阅对应的 MQTT Topic。
     *
     * <p>Proxy 模式订阅：order（QoS 0）、instantActions（QoS 0）<br>
     * Server 模式订阅：state（QoS 0）、connection（QoS 1）、factsheet（QoS 0）</p>
     *
     * @throws MqttException 订阅失败时抛出
     */
    private void subscribeTopics() throws MqttException {
        // Proxy 模式：订阅主控下发的 order 和 instantActions
        if (properties.getProxy().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
                String orderTopic = topicResolver.orderTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String actionsTopic = topicResolver.instantActionsTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                mqttClient.subscribe(orderTopic, 0);
                mqttClient.subscribe(actionsTopic, 0);
                log.info("Proxy subscribed: {}, {}", orderTopic, actionsTopic);
            }
        }

        // Server 模式：订阅 AGV 上报的 state、connection 和 factsheet
        if (properties.getServer().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getServerVehicles()) {
                String stateTopic = topicResolver.stateTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String connTopic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                String fsTopic = topicResolver.factsheetTopic(ctx.getManufacturer(), ctx.getSerialNumber());
                mqttClient.subscribe(stateTopic, 0);
                // connection Topic 使用 QoS 1，确保可靠接收连接状态变更
                mqttClient.subscribe(connTopic, 1);
                mqttClient.subscribe(fsTopic, 0);
                log.info("Server subscribed: {}, {}, {}", stateTopic, connTopic, fsTopic);
            }
        }
    }

    /**
     * 容器关闭时优雅断开 MQTT 连接。
     */
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

    /**
     * 检查当前是否已连接到 MQTT Broker。
     *
     * @return 已连接返回 true，否则返回 false
     */
    public boolean isConnected() {
        return mqttClient.isConnected();
    }
}
