/**
 * MQTT 通信层，提供与 MQTT Broker 的连接管理、消息发布和入站路由。
 *
 * <p>基于 Eclipse Paho MQTT v3 客户端实现，支持 TCP 和 WebSocket 传输协议。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.mqtt.MqttTopicResolver} — VDA5050 Topic 路径构建</li>
 *   <li>{@link com.navasmart.vda5050.mqtt.MqttGateway} — 消息发布（含 QoS 和 retained 控制）</li>
 *   <li>{@link com.navasmart.vda5050.mqtt.MqttInboundRouter} — 入站消息按 Topic 后缀分发</li>
 *   <li>{@link com.navasmart.vda5050.mqtt.MqttConnectionManager} — 连接生命周期、LWT 配置</li>
 * </ul>
 *
 * <h2>Topic 格式</h2>
 * <pre>{interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}</pre>
 * <p>示例：{@code uagv/v2/MyCompany/forklift01/order}
 */
package com.navasmart.vda5050.mqtt;
