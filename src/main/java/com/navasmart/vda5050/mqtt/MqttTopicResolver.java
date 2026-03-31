package com.navasmart.vda5050.mqtt;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import org.springframework.stereotype.Component;

/**
 * MQTT Topic 路径构建器与解析器。
 *
 * <p>VDA5050 协议规定 MQTT Topic 格式为：
 * <pre>
 *   {interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}
 * </pre>
 * 例如：{@code uagv/v2/RobotCompany/AGV001/order}
 *
 * <p>其中 {@code interfaceName} 和 {@code majorVersion} 从全局配置
 * {@link Vda5050Properties} 中读取，{@code manufacturer} 和 {@code serialNumber}
 * 按车辆维度传入。</p>
 *
 * <p>本类为无状态 Spring 组件，线程安全。</p>
 */
@Component
public class MqttTopicResolver {

    private final Vda5050Properties properties;

    public MqttTopicResolver(Vda5050Properties properties) {
        this.properties = properties;
    }

    /**
     * 构建 Topic 前缀：{@code {interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}}。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return Topic 前缀字符串
     */
    public String buildPrefix(String manufacturer, String serialNumber) {
        return properties.getMqtt().getInterfaceName() + "/"
                + properties.getMqtt().getMajorVersion() + "/"
                + manufacturer + "/" + serialNumber;
    }

    /**
     * 构建 order Topic 完整路径。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 完整 Topic 路径，例如 {@code uagv/v2/RobotCo/AGV001/order}
     */
    public String orderTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/order";
    }

    /**
     * 构建 instantActions Topic 完整路径。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 完整 Topic 路径
     */
    public String instantActionsTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/instantActions";
    }

    /**
     * 构建 state Topic 完整路径。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 完整 Topic 路径
     */
    public String stateTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/state";
    }

    /**
     * 构建 connection Topic 完整路径。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 完整 Topic 路径
     */
    public String connectionTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/connection";
    }

    /**
     * 构建 factsheet Topic 完整路径。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 完整 Topic 路径
     */
    public String factsheetTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/factsheet";
    }

    /**
     * 从完整 Topic 中提取最后一级后缀（即 topicName 部分）。
     *
     * <p>例如输入 {@code "uagv/v2/RobotCo/AGV001/order"}，返回 {@code "order"}。</p>
     *
     * @param topic 完整的 MQTT Topic
     * @return Topic 后缀名称
     */
    public String extractTopicSuffix(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash >= 0 ? topic.substring(lastSlash + 1) : topic;
    }

    /**
     * 从完整 Topic 中提取车辆标识（manufacturer 和 serialNumber）。
     *
     * <p>Topic 格式为 {@code {interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}}，
     * 本方法返回索引 2 和 3 的元素。</p>
     *
     * @param topic 完整的 MQTT Topic
     * @return 包含 {@code [manufacturer, serialNumber]} 的数组；Topic 格式不合法时返回 null
     */
    public String[] extractVehicleId(String topic) {
        // Topic 格式: {interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}
        String[] parts = topic.split("/");
        if (parts.length >= 5) {
            return new String[]{parts[2], parts[3]};
        }
        return null;
    }
}
