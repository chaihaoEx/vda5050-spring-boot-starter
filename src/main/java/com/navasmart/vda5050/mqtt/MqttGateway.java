package com.navasmart.vda5050.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.Order;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MQTT 消息发布网关，封装 VDA5050 各类消息的序列化与发布逻辑。
 *
 * <h2>QoS 策略</h2>
 * <ul>
 *   <li><b>QoS 0</b>（至多一次）：用于 state、order、instantActions、factsheet 等高频或非关键消息</li>
 *   <li><b>QoS 1 + retained</b>（至少一次 + 保留消息）：仅用于 connection 消息，
 *       确保 Broker 持久保存最新连接状态，新订阅者可立即获取</li>
 * </ul>
 *
 * <p>所有 payload 通过 Jackson {@link ObjectMapper} 序列化为 JSON 字节数组后发布。</p>
 */
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

    /**
     * 将 payload 序列化为 JSON 并发布到指定 MQTT Topic。
     *
     * @param topic    目标 MQTT Topic
     * @param payload  消息体对象，将通过 Jackson 序列化为 JSON
     * @param qos      MQTT QoS 级别（0 或 1）
     * @param retained 是否设置为保留消息
     */
    public void publish(String topic, Object payload, int qos, boolean retained) {
        if (!mqttClient.isConnected()) {
            log.debug("MQTT client not connected, skipping publish to {}", topic);
            return;
        }
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

    // ============ Proxy 模式便捷方法 ============

    /**
     * 发布 AGV 状态消息（QoS 0，非保留）。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @param state        AGV 状态对象
     */
    public void publishState(String manufacturer, String serialNumber, AgvState state) {
        publish(topicResolver.stateTopic(manufacturer, serialNumber), state, 0, false);
    }

    /**
     * 发布连接状态消息（QoS 1，保留消息）。
     *
     * <p>使用 QoS 1 确保消息可靠送达，retained=true 使 Broker 保留最新连接状态。</p>
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @param connection   连接状态对象
     */
    public void publishConnection(String manufacturer, String serialNumber, Connection connection) {
        publish(topicResolver.connectionTopic(manufacturer, serialNumber), connection, 1, true);
    }

    /**
     * 发布 Factsheet 消息（QoS 0，非保留）。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @param factsheet    Factsheet 对象
     */
    public void publishFactsheet(String manufacturer, String serialNumber, Factsheet factsheet) {
        publish(topicResolver.factsheetTopic(manufacturer, serialNumber), factsheet, 0, false);
    }

    // ============ Server 模式便捷方法 ============

    /**
     * 向 AGV 下发订单消息（QoS 0，非保留）。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @param order        订单对象
     */
    public void publishOrder(String manufacturer, String serialNumber, Order order) {
        publish(topicResolver.orderTopic(manufacturer, serialNumber), order, 0, false);
    }

    /**
     * 向 AGV 下发即时动作消息（QoS 0，非保留）。
     *
     * @param manufacturer   制造商名称
     * @param serialNumber   车辆序列号
     * @param instantActions 即时动作对象
     */
    public void publishInstantActions(String manufacturer, String serialNumber,
                                      InstantActions instantActions) {
        publish(topicResolver.instantActionsTopic(manufacturer, serialNumber), instantActions, 0, false);
    }
}
