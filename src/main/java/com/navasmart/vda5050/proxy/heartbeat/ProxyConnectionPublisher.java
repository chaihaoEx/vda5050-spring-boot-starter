package com.navasmart.vda5050.proxy.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.model.enums.ConnectionState;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProxyConnectionPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProxyConnectionPublisher.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    public ProxyConnectionPublisher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                    Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    @PostConstruct
    public void publishOnline() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            publishConnectionState(ctx, ConnectionState.ONLINE);
            log.info("Published ONLINE for vehicle {}", ctx.getVehicleId());
        }
    }

    @PreDestroy
    public void publishOffline() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            publishConnectionState(ctx, ConnectionState.OFFLINE);
            log.info("Published OFFLINE for vehicle {}", ctx.getVehicleId());
        }
    }

    private void publishConnectionState(VehicleContext ctx, ConnectionState state) {
        Connection conn = new Connection();
        conn.setHeaderId(ctx.nextConnectionHeaderId());
        conn.setTimestamp(TimestampUtil.now());
        conn.setVersion(properties.getMqtt().getProtocolVersion());
        conn.setManufacturer(ctx.getManufacturer());
        conn.setSerialNumber(ctx.getSerialNumber());
        conn.setConnectionState(state.getValue());
        mqttGateway.publishConnection(ctx.getManufacturer(), ctx.getSerialNumber(), conn);
    }
}
