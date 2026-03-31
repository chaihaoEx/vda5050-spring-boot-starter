package com.navasmart.vda5050.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.error.Vda5050ErrorFactory;
import com.navasmart.vda5050.mqtt.MqttConnectionManager;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.mqtt.MqttTopicResolver;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(Vda5050Properties.class)
public class Vda5050AutoConfiguration {

    @Bean("vda5050ObjectMapper")
    @ConditionalOnMissingBean(name = "vda5050ObjectMapper")
    public ObjectMapper vda5050ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttClient mqttClient(Vda5050Properties properties) throws MqttException {
        Vda5050Properties.MqttConfig mqtt = properties.getMqtt();
        String protocol = "websocket".equalsIgnoreCase(mqtt.getTransport()) ? "ws" : "tcp";
        String serverUri = protocol + "://" + mqtt.getHost() + ":" + mqtt.getPort();
        String clientId = mqtt.getClientIdPrefix() + "-" + System.currentTimeMillis();
        return new MqttClient(serverUri, clientId, new MemoryPersistence());
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttTopicResolver mqttTopicResolver(Vda5050Properties properties) {
        return new MqttTopicResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttGateway mqttGateway(MqttClient mqttClient, ObjectMapper vda5050ObjectMapper,
                                    MqttTopicResolver topicResolver) {
        return new MqttGateway(mqttClient, vda5050ObjectMapper, topicResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttInboundRouter mqttInboundRouter(ObjectMapper vda5050ObjectMapper,
                                                MqttTopicResolver topicResolver,
                                                VehicleRegistry vehicleRegistry) {
        return new MqttInboundRouter(vda5050ObjectMapper, topicResolver, vehicleRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttConnectionManager mqttConnectionManager(MqttClient mqttClient,
                                                        MqttInboundRouter inboundRouter,
                                                        MqttTopicResolver topicResolver,
                                                        VehicleRegistry vehicleRegistry,
                                                        Vda5050Properties properties,
                                                        ObjectMapper vda5050ObjectMapper) {
        return new MqttConnectionManager(mqttClient, inboundRouter, topicResolver,
                vehicleRegistry, properties, vda5050ObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public VehicleRegistry vehicleRegistry(Vda5050Properties properties) {
        return new VehicleRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public Vda5050ErrorFactory vda5050ErrorFactory() {
        return new Vda5050ErrorFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorAggregator errorAggregator(Vda5050ErrorFactory errorFactory) {
        return new ErrorAggregator(errorFactory);
    }
}
