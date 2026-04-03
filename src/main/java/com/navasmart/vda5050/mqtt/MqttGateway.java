package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 消息发布网关，封装 VDA5050 各类消息的序列化与发布逻辑。
 *
 * <h2>QoS 策略</h2>
 * <ul>
 *   <li><b>QoS 0</b>（至多一次）：用于 state、order、instantActions、factsheet 等高频或非关键消息</li>
 *   <li><b>QoS 1 + retained</b>（至少一次 + 保留消息）：仅用于 connection 消息</li>
 * </ul>
 *
 * <h2>可选特性</h2>
 * <ul>
 *   <li><b>速率限制</b>：通过 {@code vda5050.mqtt.minPublishIntervalMs} 配置最小发布间隔</li>
 *   <li><b>Micrometer 指标</b>：classpath 中存在 micrometer-core 时自动注册发布计数器</li>
 * </ul>
 */
@Component
public class MqttGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final MqttClient sharedMqttClient;
    private final ObjectMapper objectMapper;
    private final MqttTopicResolver topicResolver;
    private final VehicleRegistry vehicleRegistry;
    private final Vda5050Properties properties;
    private final MeterRegistry meterRegistry;

    /** 每种 Topic 类型的最近发布时间戳（用于速率限制） */
    private final ConcurrentHashMap<String, Long> lastPublishTimes = new ConcurrentHashMap<>();

    public MqttGateway(MqttClient mqttClient, ObjectMapper objectMapper,
                       MqttTopicResolver topicResolver, VehicleRegistry vehicleRegistry,
                       Vda5050Properties properties,
                       ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.sharedMqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.topicResolver = topicResolver;
        this.vehicleRegistry = vehicleRegistry;
        this.properties = properties;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /**
     * 将 payload 序列化为 JSON 并通过指定 client 发布到目标 Topic。
     */
    public boolean publish(MqttClient client, String topic, Object payload, int qos, boolean retained) {
        if (!client.isConnected()) {
            log.debug("MQTT client not connected, skipping publish to {}", topic);
            return false;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(qos);
            message.setRetained(retained);
            client.publish(topic, message);
            recordPublishMetric(topic, "success");
            return true;
        } catch (MqttException e) {
            log.error("Failed to publish to topic {}: {}", topic, e.getMessage(), e);
            recordPublishMetric(topic, "failure");
            return false;
        } catch (Exception e) {
            log.error("Failed to serialize payload for topic {}: {}", topic, e.getMessage(), e);
            recordPublishMetric(topic, "failure");
            return false;
        }
    }

    /**
     * 将 payload 序列化为 JSON 并通过共享 client 发布到目标 Topic。
     */
    public boolean publish(String topic, Object payload, int qos, boolean retained) {
        return publish(sharedMqttClient, topic, payload, qos, retained);
    }

    private MqttClient resolveProxyClient(String manufacturer, String serialNumber) {
        VehicleContext ctx = vehicleRegistry.get(manufacturer, serialNumber);
        if (ctx != null && ctx.getProxyMqttClient() != null) {
            return ctx.getProxyMqttClient();
        }
        log.warn("Proxy client not available for vehicle {}:{}, skipping publish", manufacturer, serialNumber);
        return null;
    }

    /**
     * 检查速率限制：如果距上次发布同 key 的间隔不足 minPublishIntervalMs，则跳过。
     * key 格式为 topicType:manufacturer:serialNumber，按车辆粒度限流。
     * 使用 ConcurrentHashMap.compute() 保证原子性。
     */
    private boolean isRateLimited(String topicType, String manufacturer, String serialNumber) {
        long minInterval = properties.getMqtt().getMinPublishIntervalMs();
        if (minInterval <= 0) {
            return false;
        }
        String key = topicType + ":" + manufacturer + ":" + serialNumber;
        long now = System.currentTimeMillis();
        boolean[] limited = {false};
        lastPublishTimes.compute(key, (k, lastTime) -> {
            if (lastTime != null && (now - lastTime) < minInterval) {
                limited[0] = true;
                return lastTime;
            }
            return now;
        });
        if (limited[0]) {
            log.debug("Rate limited: skipping publish for {}", key);
        }
        return limited[0];
    }

    private void recordPublishMetric(String topic, String status) {
        if (meterRegistry != null) {
            String topicType = topic.substring(topic.lastIndexOf('/') + 1);
            // Counter.builder().register() returns cached instance from MeterRegistry
            Counter.builder("vda5050.mqtt.publish")
                    .tag("topic_type", topicType)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        }
    }

    // ============ Proxy 模式便捷方法 ============

    public boolean publishState(String manufacturer, String serialNumber, AgvState state) {
        if (isRateLimited("state", manufacturer, serialNumber)) {
            return false;
        }
        MqttClient client = resolveProxyClient(manufacturer, serialNumber);
        if (client == null) {
            return false;
        }
        return publish(client, topicResolver.stateTopic(manufacturer, serialNumber), state, 0, false);
    }

    public boolean publishConnection(String manufacturer, String serialNumber, Connection connection) {
        MqttClient client = resolveProxyClient(manufacturer, serialNumber);
        if (client == null) {
            return false;
        }
        return publish(client, topicResolver.connectionTopic(manufacturer, serialNumber), connection, 1, true);
    }

    public boolean publishFactsheet(String manufacturer, String serialNumber, Factsheet factsheet) {
        MqttClient client = resolveProxyClient(manufacturer, serialNumber);
        if (client == null) {
            return false;
        }
        return publish(client, topicResolver.factsheetTopic(manufacturer, serialNumber), factsheet, 0, false);
    }

    // ============ Server 模式便捷方法 ============

    public boolean publishOrder(String manufacturer, String serialNumber, Order order) {
        return publish(topicResolver.orderTopic(manufacturer, serialNumber), order, 0, false);
    }

    public boolean publishInstantActions(String manufacturer, String serialNumber,
                                         InstantActions instantActions) {
        return publish(topicResolver.instantActionsTopic(manufacturer, serialNumber), instantActions, 0, false);
    }
}
