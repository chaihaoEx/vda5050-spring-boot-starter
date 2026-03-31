package com.navasmart.vda5050.mqtt;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import org.springframework.stereotype.Component;

@Component
public class MqttTopicResolver {

    private final Vda5050Properties properties;

    public MqttTopicResolver(Vda5050Properties properties) {
        this.properties = properties;
    }

    public String buildPrefix(String manufacturer, String serialNumber) {
        return properties.getMqtt().getInterfaceName() + "/"
                + properties.getMqtt().getMajorVersion() + "/"
                + manufacturer + "/" + serialNumber;
    }

    public String orderTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/order";
    }

    public String instantActionsTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/instantActions";
    }

    public String stateTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/state";
    }

    public String connectionTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/connection";
    }

    public String factsheetTopic(String manufacturer, String serialNumber) {
        return buildPrefix(manufacturer, serialNumber) + "/factsheet";
    }

    public String extractTopicSuffix(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash >= 0 ? topic.substring(lastSlash + 1) : topic;
    }

    public String[] extractVehicleId(String topic) {
        // Topic format: {interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}
        String[] parts = topic.split("/");
        if (parts.length >= 5) {
            return new String[]{parts[2], parts[3]};
        }
        return null;
    }
}
