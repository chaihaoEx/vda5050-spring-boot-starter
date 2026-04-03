package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.validation.OrderValidator;
import com.navasmart.vda5050.proxy.heartbeat.ProxyConnectionPublisher;
import com.navasmart.vda5050.proxy.heartbeat.ProxyHeartbeatScheduler;
import com.navasmart.vda5050.proxy.statemachine.ProxyNavigationController;
import com.navasmart.vda5050.proxy.statemachine.ProxyNodeActionDispatcher;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 Proxy 模式的自动配置类，当 {@code vda5050.proxy.enabled=true} 时激活。
 *
 * <p><b>前置条件（用户必须提供的 Bean）：</b>
 * <ul>
 *   <li>{@link Vda5050ProxyVehicleAdapter} - 车辆控制适配器（必须）</li>
 *   <li>{@link Vda5050ProxyStateProvider} - 车辆状态提供者（必须）</li>
 *   <li>{@link com.navasmart.vda5050.proxy.action.ActionHandler} - 自定义动作处理器（可选，0 到多个）</li>
 * </ul>
 *
 * <p>缺少必要的 Bean 时，对应的组件不会被创建（通过 {@link ConditionalOnBean} 控制），
 * 但不会报错。例如缺少 Vda5050ProxyStateProvider 时，ProxyHeartbeatScheduler 不会被创建。</p>
 *
 * @see Vda5050AutoConfiguration
 * @see Vda5050ProxyVehicleAdapter
 * @see Vda5050ProxyStateProvider
 */
@Configuration
@ConditionalOnProperty(prefix = "vda5050.proxy", name = "enabled", havingValue = "true")
public class Vda5050ProxyAutoConfiguration {

    /**
     * 创建动作处理器注册中心，自动发现并注册所有 {@link com.navasmart.vda5050.proxy.action.ActionHandler} Bean。
     *
     * @return 动作处理器注册中心实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ActionHandlerRegistry actionHandlerRegistry() {
        return new ActionHandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public OrderValidator orderValidator() {
        return new OrderValidator();
    }

    /**
     * 创建订单状态机，负责接收和处理 VDA5050 订单及即时动作。
     *
     * <p>前置条件：需要用户提供 {@link Vda5050ProxyVehicleAdapter} 和 {@link Vda5050ProxyStateProvider} Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({Vda5050ProxyVehicleAdapter.class, Vda5050ProxyStateProvider.class})
    public ProxyOrderStateMachine proxyOrderStateMachine(ErrorAggregator errorAggregator,
                                                          Vda5050ProxyVehicleAdapter vehicleAdapter,
                                                          Vda5050ProxyStateProvider stateProvider,
                                                          MqttGateway mqttGateway,
                                                          ApplicationEventPublisher eventPublisher,
                                                          OrderValidator orderValidator) {
        return new ProxyOrderStateMachine(errorAggregator, vehicleAdapter, stateProvider,
                mqttGateway, eventPublisher, orderValidator);
    }

    /**
     * 创建节点动作调度器，负责按 BlockingType 规则调度和执行节点上的动作。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyVehicleAdapter.class)
    public ProxyNodeActionDispatcher proxyNodeActionDispatcher(ActionHandlerRegistry actionHandlerRegistry,
                                                               Vda5050ProxyVehicleAdapter vehicleAdapter,
                                                               Vda5050Properties properties) {
        return new ProxyNodeActionDispatcher(actionHandlerRegistry, vehicleAdapter, properties);
    }

    /**
     * 创建导航控制器，负责节点推进、航点构建和异步导航管理。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyVehicleAdapter.class)
    public ProxyNavigationController proxyNavigationController(Vda5050ProxyVehicleAdapter vehicleAdapter,
                                                                ErrorAggregator errorAggregator,
                                                                ProxyNodeActionDispatcher actionDispatcher) {
        return new ProxyNavigationController(vehicleAdapter, errorAggregator, actionDispatcher);
    }

    /**
     * 创建订单执行器，以 200ms 间隔轮询执行当前订单的导航和动作。
     *
     * <p>前置条件：需要用户提供 {@link Vda5050ProxyVehicleAdapter} Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyVehicleAdapter.class)
    public ProxyOrderExecutor proxyOrderExecutor(VehicleRegistry vehicleRegistry,
                                                  ErrorAggregator errorAggregator,
                                                  ProxyNodeActionDispatcher actionDispatcher,
                                                  ProxyNavigationController navigationController,
                                                  Vda5050ProxyVehicleAdapter vehicleAdapter,
                                                  Vda5050Properties properties,
                                                  ApplicationEventPublisher eventPublisher) {
        return new ProxyOrderExecutor(vehicleRegistry, errorAggregator,
                actionDispatcher, navigationController, vehicleAdapter, properties, eventPublisher);
    }

    /**
     * 创建心跳调度器，按配置的间隔周期性发布 VDA5050 State 消息。
     *
     * <p>前置条件：需要用户提供 {@link Vda5050ProxyStateProvider} Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyStateProvider.class)
    public ProxyHeartbeatScheduler proxyHeartbeatScheduler(VehicleRegistry vehicleRegistry,
                                                            MqttGateway mqttGateway,
                                                            Vda5050ProxyStateProvider stateProvider,
                                                            Vda5050Properties properties) {
        return new ProxyHeartbeatScheduler(vehicleRegistry, mqttGateway, stateProvider, properties);
    }

    /**
     * 创建连接状态发布器，在应用启动时发布 ONLINE、关闭时发布 OFFLINE。
     */
    @Bean
    @ConditionalOnMissingBean
    public ProxyConnectionPublisher proxyConnectionPublisher(VehicleRegistry vehicleRegistry,
                                                              MqttGateway mqttGateway,
                                                              Vda5050Properties properties) {
        return new ProxyConnectionPublisher(vehicleRegistry, mqttGateway, properties);
    }

    /**
     * 创建 MQTT 消息处理器绑定，将入站的 Order 和 InstantActions 消息路由到状态机。
     */
    @Bean
    @ConditionalOnBean({ProxyOrderStateMachine.class})
    public ProxyMqttHandlerWiring proxyMqttHandlerWiring(MqttInboundRouter router,
                                                          ProxyOrderStateMachine stateMachine) {
        return new ProxyMqttHandlerWiring(router, stateMachine);
    }

    static class ProxyMqttHandlerWiring {
        ProxyMqttHandlerWiring(MqttInboundRouter router, ProxyOrderStateMachine stateMachine) {
            router.setOrderHandler((ctx, order) -> stateMachine.receiveOrder(ctx, order));
            router.setInstantActionsHandler((ctx, actions) -> stateMachine.receiveInstantActions(ctx, actions));
        }
    }

    /**
     * 启动后校验：确保用户提供了 Proxy 模式所需的必要 Bean。
     * 在所有单例初始化完成后执行，如果缺少必要的 Bean 则抛出异常使启动失败。
     */
    @Bean
    SmartInitializingSingleton proxyAdapterValidator(ApplicationContext context) {
        return () -> {
            List<String> missing = new ArrayList<>();
            if (context.getBeanNamesForType(Vda5050ProxyVehicleAdapter.class).length == 0) {
                missing.add("Vda5050ProxyVehicleAdapter");
            }
            if (context.getBeanNamesForType(Vda5050ProxyStateProvider.class).length == 0) {
                missing.add("Vda5050ProxyStateProvider");
            }
            if (!missing.isEmpty()) {
                throw new BeanCreationException(
                        "vda5050.proxy.enabled=true but required bean(s) missing: "
                                + String.join(", ", missing)
                                + ". Please provide @Bean definition(s) in your configuration."
                                + " See docs/13-proxy-quick-start.md for setup instructions.");
            }
        };
    }
}
