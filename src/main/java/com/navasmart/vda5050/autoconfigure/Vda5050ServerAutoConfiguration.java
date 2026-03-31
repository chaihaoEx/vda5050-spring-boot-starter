package com.navasmart.vda5050.autoconfigure;

import com.navasmart.vda5050.model.Factsheet;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "vda5050.server", name = "enabled", havingValue = "true")
public class Vda5050ServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OrderDispatcher orderDispatcher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                            Vda5050Properties properties) {
        return new OrderDispatcher(vehicleRegistry, mqttGateway, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public InstantActionSender instantActionSender(VehicleRegistry vehicleRegistry,
                                                    MqttGateway mqttGateway,
                                                    Vda5050Properties properties) {
        return new InstantActionSender(vehicleRegistry, mqttGateway, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ServerAdapter.class)
    public AgvStateTracker agvStateTracker(VehicleRegistry vehicleRegistry,
                                            Vda5050ServerAdapter serverAdapter) {
        return new AgvStateTracker(vehicleRegistry, serverAdapter);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrderProgressTracker orderProgressTracker(VehicleRegistry vehicleRegistry) {
        return new OrderProgressTracker(vehicleRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Vda5050ServerAdapter.class)
    public ServerConnectionMonitor serverConnectionMonitor(VehicleRegistry vehicleRegistry,
                                                            Vda5050Properties properties,
                                                            Vda5050ServerAdapter serverAdapter) {
        return new ServerConnectionMonitor(vehicleRegistry, properties, serverAdapter);
    }

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
