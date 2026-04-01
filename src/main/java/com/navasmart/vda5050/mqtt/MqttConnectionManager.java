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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>Proxy 模式下，每辆车拥有独立的 {@link MqttClient}，各自配置 LWT 消息：
 * 当客户端异常断开时，Broker 自动向对应车辆的 connection Topic 发布
 * {@code CONNECTIONBROKEN} 状态（QoS 1 + retained）。</p>
 *
 * <h2>多客户端架构</h2>
 * <ul>
 *   <li><b>Proxy 车辆</b>：每辆车一个专属 MqttClient（存储在 {@link VehicleContext#getProxyMqttClient()}），
 *       各自独立订阅 order/instantActions，各自拥有 LWT</li>
 *   <li><b>Server 模式 / 共享</b>：使用 Spring Bean 注入的共享 MqttClient，
 *       订阅所有受控 AGV 的 state/connection/factsheet</li>
 * </ul>
 *
 * <h2>自动重连</h2>
 * <p>所有 client 均通过 {@code MqttConnectOptions.setAutomaticReconnect(true)} 启用 Paho 内置的自动重连机制。
 * 重连成功后自动重新订阅 Topic。可通过 {@code vda5050.mqtt.maxReconnectAttempts} 配置最大重连次数。</p>
 */
@Component
public class MqttConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(MqttConnectionManager.class);

    private final MqttClient sharedMqttClient;
    private final MqttInboundRouter inboundRouter;
    private final MqttTopicResolver topicResolver;
    private final VehicleRegistry vehicleRegistry;
    private final Vda5050Properties properties;
    private final ObjectMapper objectMapper;

    /** 共享 client 的重连尝试计数器 */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public MqttConnectionManager(MqttClient mqttClient, MqttInboundRouter inboundRouter,
                                 MqttTopicResolver topicResolver, VehicleRegistry vehicleRegistry,
                                 Vda5050Properties properties, ObjectMapper objectMapper) {
        this.sharedMqttClient = mqttClient;
        this.inboundRouter = inboundRouter;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 容器启动时连接 MQTT Broker 并订阅相关 Topic。
     *
     * @throws MqttException 连接或订阅失败时抛出
     */
    @PostConstruct
    public void connect() throws MqttException {
        // 为每辆 Proxy 车辆创建独立 client，每辆车各自拥有 LWT
        if (properties.getProxy().isEnabled()) {
            connectProxyVehicles();
        }

        // 连接共享 client（Server 模式需要，或 Proxy 未启用时作为默认 client）
        if (properties.getServer().isEnabled() || !properties.getProxy().isEnabled()) {
            MqttConnectOptions sharedOptions = buildBaseOptions();
            sharedMqttClient.setCallback(inboundRouter);
            sharedMqttClient.connect(sharedOptions);
            log.info("Shared MQTT client connected to {}:{}",
                    properties.getMqtt().getHost(), properties.getMqtt().getPort());

            // 注册重连监听器：重连后自动重新订阅 Server Topic
            inboundRouter.addReconnectListener(() -> {
                reconnectAttempts.set(0);
                try {
                    if (properties.getServer().isEnabled()) {
                        subscribeServerTopics(sharedMqttClient);
                        log.info("Re-subscribed server topics after reconnect");
                    }
                } catch (MqttException e) {
                    log.error("Failed to re-subscribe server topics after reconnect: {}", e.getMessage());
                }
            });

            // 注册连接丢失监听器：断路器逻辑
            int maxAttempts = properties.getMqtt().getMaxReconnectAttempts();
            if (maxAttempts > 0) {
                inboundRouter.addConnectionLostListener(() -> {
                    int attempts = reconnectAttempts.incrementAndGet();
                    if (attempts >= maxAttempts) {
                        log.error("Max reconnect attempts ({}) reached for shared client. Giving up.", maxAttempts);
                        try {
                            sharedMqttClient.disconnect();
                        } catch (MqttException e) {
                            log.warn("Error disconnecting after max retries: {}", e.getMessage());
                        }
                    }
                });
            }

            if (properties.getServer().isEnabled()) {
                subscribeServerTopics(sharedMqttClient);
            }
        }
    }

    /**
     * 为每辆 Proxy 车辆创建独立的 MqttClient，各自配置 LWT 后连接 Broker。
     */
    private void connectProxyVehicles() throws MqttException {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            connectSingleProxyVehicle(ctx);
        }
    }

    /**
     * 为单辆 Proxy 车辆创建独立 MqttClient，配置 LWT，连接 Broker 并订阅 Topic。
     * 公开方法供动态车辆注册使用。
     *
     * @param ctx 车辆上下文
     * @throws MqttException 连接失败时抛出
     */
    public void connectProxyVehicle(VehicleContext ctx) throws MqttException {
        connectSingleProxyVehicle(ctx);
    }

    private void connectSingleProxyVehicle(VehicleContext ctx) throws MqttException {
        Vda5050Properties.MqttConfig mqtt = properties.getMqtt();
        String serverUri = mqtt.resolveScheme() + "://" + mqtt.getHost() + ":" + mqtt.getPort();

        String clientId = mqtt.getClientIdPrefix()
                + "-" + ctx.getManufacturer()
                + "-" + ctx.getSerialNumber()
                + "-" + System.currentTimeMillis();

        MqttClient vehicleClient = new MqttClient(serverUri, clientId, new MemoryPersistence());

        MqttConnectOptions vehicleOptions = buildBaseOptions();
        setLwt(vehicleOptions, ctx);

        vehicleClient.setCallback(new VehicleClientCallback(ctx.getVehicleId(), inboundRouter, this, ctx));
        vehicleClient.connect(vehicleOptions);
        ctx.setProxyMqttClient(vehicleClient);

        subscribeProxyTopics(vehicleClient, ctx);
        log.info("Proxy vehicle {} connected with dedicated MQTT client (id={})",
                ctx.getVehicleId(), clientId);
    }

    /**
     * 断开指定 Proxy 车辆的 MQTT 连接。
     *
     * @param ctx 车辆上下文
     */
    public void disconnectProxyVehicle(VehicleContext ctx) {
        MqttClient vehicleClient = ctx.getProxyMqttClient();
        if (vehicleClient != null) {
            try {
                if (vehicleClient.isConnected()) {
                    vehicleClient.disconnect();
                    log.info("Proxy vehicle {} MQTT client disconnected", ctx.getVehicleId());
                }
            } catch (MqttException e) {
                log.warn("Error disconnecting proxy client for {}: {}", ctx.getVehicleId(), e.getMessage());
            }
            ctx.setProxyMqttClient(null);
        }
    }

    /**
     * 在共享 client 上为单辆 Server 模式车辆订阅 state/connection/factsheet Topic。
     *
     * @param ctx 车辆上下文
     * @throws MqttException 订阅失败时抛出
     */
    public void subscribeServerVehicle(VehicleContext ctx) throws MqttException {
        String stateTopic = topicResolver.stateTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        String connTopic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        String fsTopic = topicResolver.factsheetTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        sharedMqttClient.subscribe(stateTopic, 0);
        sharedMqttClient.subscribe(connTopic, 1);
        sharedMqttClient.subscribe(fsTopic, 0);
        log.info("Server subscribed for vehicle {}: {}, {}, {}", ctx.getVehicleId(), stateTopic, connTopic, fsTopic);
    }

    /**
     * 在共享 client 上取消订阅指定 Server 模式车辆的 Topic。
     *
     * @param ctx 车辆上下文
     * @throws MqttException 取消订阅失败时抛出
     */
    public void unsubscribeServerVehicle(VehicleContext ctx) throws MqttException {
        String stateTopic = topicResolver.stateTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        String connTopic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        String fsTopic = topicResolver.factsheetTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        sharedMqttClient.unsubscribe(new String[]{stateTopic, connTopic, fsTopic});
        log.info("Server unsubscribed for vehicle {}", ctx.getVehicleId());
    }

    /**
     * 构建基础连接选项（自动重连、cleanSession、keepAlive、认证、SSL）。
     */
    private MqttConnectOptions buildBaseOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(properties.getMqtt().isCleanSession());
        options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());

        String username = properties.getMqtt().getUsername();
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }

        // SSL/TLS 配置
        Vda5050Properties.SslConfig sslConfig = properties.getMqtt().getSsl();
        if (sslConfig.isEnabled()) {
            try {
                options.setSocketFactory(SslUtil.createSocketFactory(sslConfig));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure SSL for MQTT", e);
            }
        }

        return options;
    }

    /**
     * 为指定 Proxy 车辆配置 LWT（遗嘱消息）。
     */
    private void setLwt(MqttConnectOptions options, VehicleContext ctx) {
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
        } catch (Exception e) {
            log.warn("Failed to set LWT for vehicle {}: {}", ctx.getVehicleId(), e.getMessage());
        }
    }

    /**
     * 在指定 client 上为 Proxy 车辆订阅 order 和 instantActions Topic。
     */
    void subscribeProxyTopics(MqttClient client, VehicleContext ctx) throws MqttException {
        String orderTopic = topicResolver.orderTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        String actionsTopic = topicResolver.instantActionsTopic(ctx.getManufacturer(), ctx.getSerialNumber());
        client.subscribe(orderTopic, 0);
        client.subscribe(actionsTopic, 0);
        log.info("Proxy subscribed: {}, {}", orderTopic, actionsTopic);
    }

    /**
     * 在共享 client 上为 Server 模式车辆订阅 state、connection 和 factsheet Topic。
     */
    private void subscribeServerTopics(MqttClient client) throws MqttException {
        for (VehicleContext ctx : vehicleRegistry.getServerVehicles()) {
            String stateTopic = topicResolver.stateTopic(ctx.getManufacturer(), ctx.getSerialNumber());
            String connTopic = topicResolver.connectionTopic(ctx.getManufacturer(), ctx.getSerialNumber());
            String fsTopic = topicResolver.factsheetTopic(ctx.getManufacturer(), ctx.getSerialNumber());
            client.subscribe(stateTopic, 0);
            client.subscribe(connTopic, 1);
            client.subscribe(fsTopic, 0);
            log.info("Server subscribed: {}, {}, {}", stateTopic, connTopic, fsTopic);
        }
    }

    /**
     * 容器关闭时优雅断开所有 MQTT 连接（Proxy 专属 client + 共享 client）。
     */
    @PreDestroy
    public void disconnect() {
        if (properties.getProxy().isEnabled()) {
            for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
                disconnectProxyVehicle(ctx);
            }
        }

        try {
            if (sharedMqttClient.isConnected()) {
                sharedMqttClient.disconnect();
                log.info("Shared MQTT client disconnected");
            }
        } catch (MqttException e) {
            log.warn("Error disconnecting shared MQTT client: {}", e.getMessage());
        }
    }

    /**
     * 检查共享 MQTT client 是否已连接到 Broker。
     */
    public boolean isConnected() {
        return sharedMqttClient.isConnected();
    }

    /**
     * Per-vehicle client 的回调：消息转发到共享 InboundRouter，
     * 重连后自动重新订阅 Proxy Topic，断连时执行断路器逻辑。
     */
    private static class VehicleClientCallback implements MqttCallbackExtended {

        private final String vehicleId;
        private final MqttInboundRouter inboundRouter;
        private final MqttConnectionManager connectionManager;
        private final VehicleContext vehicleContext;
        private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

        VehicleClientCallback(String vehicleId, MqttInboundRouter inboundRouter,
                              MqttConnectionManager connectionManager, VehicleContext vehicleContext) {
            this.vehicleId = vehicleId;
            this.inboundRouter = inboundRouter;
            this.connectionManager = connectionManager;
            this.vehicleContext = vehicleContext;
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                reconnectAttempts.set(0);
                log.info("Proxy vehicle {} reconnected to {}", vehicleId, serverURI);
                MqttClient client = vehicleContext.getProxyMqttClient();
                if (client != null) {
                    try {
                        connectionManager.subscribeProxyTopics(client, vehicleContext);
                        log.info("Re-subscribed proxy topics for vehicle {} after reconnect", vehicleId);
                    } catch (MqttException e) {
                        log.error("Failed to re-subscribe proxy topics for {}: {}", vehicleId, e.getMessage());
                    }
                }
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("Proxy vehicle {} MQTT connection lost: {}", vehicleId, cause.getMessage());
            int maxAttempts = connectionManager.properties.getMqtt().getMaxReconnectAttempts();
            if (maxAttempts > 0) {
                int attempts = reconnectAttempts.incrementAndGet();
                if (attempts >= maxAttempts) {
                    log.error("Max reconnect attempts ({}) reached for vehicle {}. Giving up.",
                            maxAttempts, vehicleId);
                    MqttClient client = vehicleContext.getProxyMqttClient();
                    if (client != null) {
                        try {
                            client.disconnect();
                        } catch (MqttException e) {
                            log.warn("Error disconnecting vehicle {} after max retries: {}",
                                    vehicleId, e.getMessage());
                        }
                    }
                }
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            inboundRouter.messageArrived(topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // 无需处理
        }
    }
}
