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
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VDA5050 Spring Boot Starter 的核心自动配置类，负责创建所有公共基础组件。
 *
 * <p>此配置类无条件加载（不依赖 proxy/server 模式配置），提供的基础 Bean 包括：
 * <ul>
 *   <li>MQTT 通信层：MqttClient、MqttGateway、MqttInboundRouter、MqttConnectionManager</li>
 *   <li>消息序列化：vda5050ObjectMapper（独立的 ObjectMapper 实例）</li>
 *   <li>车辆管理：VehicleRegistry</li>
 *   <li>错误处理：Vda5050ErrorFactory、ErrorAggregator</li>
 * </ul>
 *
 * <p>所有 Bean 均标注 {@link ConditionalOnMissingBean}，用户可通过自定义 Bean 覆盖默认实现。</p>
 *
 * @see Vda5050ProxyAutoConfiguration
 * @see Vda5050ServerAutoConfiguration
 * @see Vda5050Properties
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(Vda5050Properties.class)
public class Vda5050AutoConfiguration {

    /**
     * 创建 VDA5050 专用的 ObjectMapper，配置为忽略未知属性、排除 null 值。
     *
     * <p>使用独立的 Bean 名称 {@code vda5050ObjectMapper}，避免与应用中其他 ObjectMapper 冲突。</p>
     *
     * @return 配置好的 ObjectMapper 实例
     */
    @Bean("vda5050ObjectMapper")
    @ConditionalOnMissingBean(name = "vda5050ObjectMapper")
    public ObjectMapper vda5050ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * 创建 MQTT 客户端，支持 TCP 和 WebSocket 两种传输协议。
     *
     * <p>客户端 ID 格式为：{clientIdPrefix}-{timestamp}，确保唯一性。</p>
     *
     * @param properties VDA5050 配置属性
     * @return MQTT 客户端实例
     * @throws MqttException 连接创建失败时抛出
     */
    @Bean
    @ConditionalOnMissingBean
    public MqttClient mqttClient(Vda5050Properties properties) {
        Vda5050Properties.MqttConfig mqtt = properties.getMqtt();
        String serverUri = mqtt.resolveScheme() + "://" + mqtt.getHost() + ":" + mqtt.getPort();
        String clientId = mqtt.getClientIdPrefix() + "-" + System.currentTimeMillis();
        try {
            return new MqttClient(serverUri, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("Failed to create MQTT client: " + e.getMessage(), e);
        }
    }

    /**
     * 创建 MQTT 主题解析器，负责根据 VDA5050 规范构造主题路径。
     *
     * @param properties VDA5050 配置属性
     * @return 主题解析器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MqttTopicResolver mqttTopicResolver(Vda5050Properties properties) {
        return new MqttTopicResolver(properties);
    }

    /**
     * 创建 MQTT 网关，封装消息的序列化和发布操作。
     *
     * @param mqttClient         MQTT 客户端
     * @param vda5050ObjectMapper VDA5050 专用 ObjectMapper
     * @param topicResolver      主题解析器
     * @return MQTT 网关实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MqttGateway mqttGateway(MqttClient mqttClient, ObjectMapper vda5050ObjectMapper,
                                    MqttTopicResolver topicResolver, VehicleRegistry vehicleRegistry,
                                    Vda5050Properties properties,
                                    ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new MqttGateway(mqttClient, vda5050ObjectMapper, topicResolver, vehicleRegistry,
                properties, meterRegistryProvider);
    }

    /**
     * 创建 MQTT 入站消息路由器，负责反序列化并分发收到的 MQTT 消息。
     *
     * @param vda5050ObjectMapper VDA5050 专用 ObjectMapper
     * @param topicResolver      主题解析器
     * @param vehicleRegistry    车辆注册表
     * @return 入站消息路由器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MqttInboundRouter mqttInboundRouter(ObjectMapper vda5050ObjectMapper,
                                                MqttTopicResolver topicResolver,
                                                VehicleRegistry vehicleRegistry) {
        return new MqttInboundRouter(vda5050ObjectMapper, topicResolver, vehicleRegistry);
    }

    /**
     * 创建 MQTT 连接管理器，负责建立连接、订阅主题和处理断线重连。
     *
     * @param mqttClient          MQTT 客户端
     * @param inboundRouter       入站消息路由器
     * @param topicResolver       主题解析器
     * @param vehicleRegistry     车辆注册表
     * @param properties          VDA5050 配置属性
     * @param vda5050ObjectMapper VDA5050 专用 ObjectMapper
     * @return 连接管理器实例
     */
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

    /**
     * 创建车辆注册表，根据配置文件中的车辆定义初始化所有 VehicleContext。
     *
     * @param properties VDA5050 配置属性（包含车辆列表定义）
     * @return 车辆注册表实例
     */
    @Bean
    @ConditionalOnMissingBean
    public VehicleRegistry vehicleRegistry(Vda5050Properties properties) {
        return new VehicleRegistry(properties);
    }

    /**
     * 创建 VDA5050 错误工厂，负责构造符合 VDA5050 规范的 Error 对象。
     *
     * @return 错误工厂实例
     */
    @Bean
    @ConditionalOnMissingBean
    public Vda5050ErrorFactory vda5050ErrorFactory() {
        return new Vda5050ErrorFactory();
    }

    /**
     * 创建错误聚合器，负责管理车辆上下文中的错误列表（添加、清除、查询）。
     *
     * @param errorFactory VDA5050 错误工厂
     * @return 错误聚合器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorAggregator errorAggregator(Vda5050ErrorFactory errorFactory) {
        return new ErrorAggregator(errorFactory);
    }
}
