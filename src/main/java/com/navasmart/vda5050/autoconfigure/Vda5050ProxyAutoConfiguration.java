package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.mqtt.MqttInboundRouter;
import com.navasmart.vda5050.proxy.action.ActionHandlerRegistry;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.heartbeat.ProxyConnectionPublisher;
import com.navasmart.vda5050.proxy.heartbeat.ProxyHeartbeatScheduler;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor;
import com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "vda5050.proxy", name = "enabled", havingValue = "true")
public class Vda5050ProxyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActionHandlerRegistry actionHandlerRegistry() {
        return new ActionHandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({Vda5050ProxyVehicleAdapter.class, Vda5050ProxyStateProvider.class})
    public ProxyOrderStateMachine proxyOrderStateMachine(ErrorAggregator errorAggregator,
                                                          Vda5050ProxyVehicleAdapter vehicleAdapter,
                                                          Vda5050ProxyStateProvider stateProvider,
                                                          MqttGateway mqttGateway) {
        return new ProxyOrderStateMachine(errorAggregator, vehicleAdapter, stateProvider, mqttGateway);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyVehicleAdapter.class)
    public ProxyOrderExecutor proxyOrderExecutor(VehicleRegistry vehicleRegistry,
                                                  ErrorAggregator errorAggregator,
                                                  ActionHandlerRegistry actionHandlerRegistry,
                                                  Vda5050ProxyVehicleAdapter vehicleAdapter) {
        return new ProxyOrderExecutor(vehicleRegistry, errorAggregator,
                actionHandlerRegistry, vehicleAdapter);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ProxyStateProvider.class)
    public ProxyHeartbeatScheduler proxyHeartbeatScheduler(VehicleRegistry vehicleRegistry,
                                                            MqttGateway mqttGateway,
                                                            Vda5050ProxyStateProvider stateProvider,
                                                            Vda5050Properties properties) {
        return new ProxyHeartbeatScheduler(vehicleRegistry, mqttGateway, stateProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyConnectionPublisher proxyConnectionPublisher(VehicleRegistry vehicleRegistry,
                                                              MqttGateway mqttGateway,
                                                              Vda5050Properties properties) {
        return new ProxyConnectionPublisher(vehicleRegistry, mqttGateway, properties);
    }

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
}
