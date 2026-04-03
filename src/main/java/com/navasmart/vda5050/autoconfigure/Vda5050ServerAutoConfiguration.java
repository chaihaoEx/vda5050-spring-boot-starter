package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.server.dispatch.InstantActionSender;
import com.navasmart.vda5050.server.dispatch.OrderDispatcher;
import com.navasmart.vda5050.server.heartbeat.ServerConnectionMonitor;
import com.navasmart.vda5050.server.tracking.AgvStateTracker;
import com.navasmart.vda5050.server.tracking.OrderProgressTracker;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VDA5050 Server 模式的自动配置类，当 {@code vda5050.server.enabled=true} 时激活。
 *
 * <p><b>前置条件（用户必须提供的 Bean）：</b>
 * <ul>
 *   <li>{@link Vda5050ServerAdapter} - Server 回调适配器（必须，用于接收状态变更通知）</li>
 * </ul>
 *
 * <p>缺少 {@link Vda5050ServerAdapter} Bean 时，AgvStateTracker 和 ServerConnectionMonitor
 * 不会被创建（通过 {@link ConditionalOnBean} 控制），但 OrderDispatcher、InstantActionSender
 * 和 OrderProgressTracker 仍会创建。</p>
 *
 * @see Vda5050AutoConfiguration
 * @see Vda5050ServerAdapter
 */
@Configuration
@ConditionalOnProperty(prefix = "vda5050.server", name = "enabled", havingValue = "true")
public class Vda5050ServerAutoConfiguration {

    /**
     * 创建订单下发器，用于向 AGV 发送 Order 消息。
     */
    @Bean
    @ConditionalOnMissingBean
    public OrderDispatcher orderDispatcher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                            Vda5050Properties properties,
                                            ErrorAggregator errorAggregator) {
        return new OrderDispatcher(vehicleRegistry, mqttGateway, properties, errorAggregator);
    }

    /**
     * 创建即时动作发送器，用于向 AGV 发送 InstantActions 消息。
     */
    @Bean
    @ConditionalOnMissingBean
    public InstantActionSender instantActionSender(VehicleRegistry vehicleRegistry,
                                                    MqttGateway mqttGateway,
                                                    Vda5050Properties properties) {
        return new InstantActionSender(vehicleRegistry, mqttGateway, properties);
    }

    /**
     * 创建 AGV 状态追踪器，解析 State 消息并检测变更。
     *
     * <p>前置条件：需要用户提供 {@link Vda5050ServerAdapter} Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ServerAdapter.class)
    public AgvStateTracker agvStateTracker(VehicleRegistry vehicleRegistry,
                                            Vda5050ServerAdapter serverAdapter,
                                            ApplicationEventPublisher eventPublisher) {
        return new AgvStateTracker(vehicleRegistry, serverAdapter, eventPublisher);
    }

    /**
     * 创建订单进度追踪器，用于查询指定车辆的订单执行进度。
     */
    @Bean
    @ConditionalOnMissingBean
    public OrderProgressTracker orderProgressTracker(VehicleRegistry vehicleRegistry) {
        return new OrderProgressTracker(vehicleRegistry);
    }

    /**
     * 创建连接状态监控器，定期检测 AGV 超时并处理 Connection 消息。
     *
     * <p>前置条件：需要用户提供 {@link Vda5050ServerAdapter} Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ServerAdapter.class)
    public ServerConnectionMonitor serverConnectionMonitor(VehicleRegistry vehicleRegistry,
                                                            Vda5050Properties properties,
                                                            Vda5050ServerAdapter serverAdapter,
                                                            ApplicationEventPublisher eventPublisher) {
        return new ServerConnectionMonitor(vehicleRegistry, properties, serverAdapter, eventPublisher);
    }

    /**
     * 创建 MQTT 消息处理器绑定，将入站的 State、Connection、Factsheet 消息路由到对应的处理器。
     */
    @Bean
    @ConditionalOnBean({AgvStateTracker.class, ServerConnectionMonitor.class})
    public ServerMqttHandlerWiring serverMqttHandlerWiring(MqttInboundRouter router,
                                                            AgvStateTracker stateTracker,
                                                            ServerConnectionMonitor connectionMonitor,
                                                            Vda5050ServerAdapter serverAdapter) {
        return new ServerMqttHandlerWiring(router, stateTracker, connectionMonitor, serverAdapter);
    }

    static class ServerMqttHandlerWiring {
        ServerMqttHandlerWiring(MqttInboundRouter router, AgvStateTracker stateTracker,
                                ServerConnectionMonitor connectionMonitor,
                                Vda5050ServerAdapter serverAdapter) {
            router.setStateHandler((ctx, state) ->
                    stateTracker.processState(ctx.getVehicleId(), state));
            router.setConnectionHandler((ctx, conn) ->
                    connectionMonitor.processConnection(ctx.getVehicleId(), conn));
            router.setFactsheetHandler((ctx, fs) ->
                    serverAdapter.onFactsheetReceived(ctx.getVehicleId(), fs));
        }
    }
}
