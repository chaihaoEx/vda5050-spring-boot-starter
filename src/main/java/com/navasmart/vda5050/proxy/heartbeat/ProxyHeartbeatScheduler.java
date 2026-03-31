package com.navasmart.vda5050.proxy.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.*;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.VehicleStatus;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProxyHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHeartbeatScheduler.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050ProxyStateProvider stateProvider;
    private final Vda5050Properties properties;

    public ProxyHeartbeatScheduler(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                                   Vda5050ProxyStateProvider stateProvider,
                                   Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.stateProvider = stateProvider;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vda5050.proxy.heartbeatIntervalMs:1000}")
    public void publishHeartbeat() {
        for (VehicleContext ctx : vehicleRegistry.getProxyVehicles()) {
            try {
                updateStateFromProvider(ctx);
                AgvState agvState = ctx.getAgvState();
                agvState.setHeaderId(ctx.nextStateHeaderId());
                agvState.setTimestamp(TimestampUtil.now());
                agvState.setVersion(properties.getMqtt().getProtocolVersion());
                agvState.setManufacturer(ctx.getManufacturer());
                agvState.setSerialNumber(ctx.getSerialNumber());

                mqttGateway.publishState(ctx.getManufacturer(), ctx.getSerialNumber(), agvState);
            } catch (Exception e) {
                log.error("Failed to publish heartbeat for vehicle {}: {}",
                        ctx.getVehicleId(), e.getMessage(), e);
            }
        }
    }

    private void updateStateFromProvider(VehicleContext ctx) {
        VehicleStatus status = stateProvider.getVehicleStatus(ctx.getVehicleId());
        if (status == null) return;

        AgvState agvState = ctx.getAgvState();

        // Update position
        AgvPosition pos = new AgvPosition();
        pos.setPositionInitialized(status.isPositionInitialized());
        pos.setX(status.getX());
        pos.setY(status.getY());
        pos.setTheta(status.getTheta());
        pos.setMapId(status.getMapId());
        pos.setLocalizationScore(status.getLocalizationScore());
        agvState.setAgvPosition(pos);

        // Update velocity
        Velocity vel = new Velocity();
        vel.setVx(status.getVx());
        vel.setVy(status.getVy());
        vel.setOmega(status.getOmega());
        agvState.setVelocity(vel);

        // Update battery
        BatteryState battery = new BatteryState();
        battery.setBatteryCharge(status.getBatteryCharge());
        battery.setBatteryVoltage(status.getBatteryVoltage());
        battery.setBatteryHealth(status.getBatteryHealth());
        battery.setCharging(status.isCharging());
        battery.setReach(status.getReach());
        agvState.setBatteryState(battery);

        // Update safety
        SafetyState safety = new SafetyState();
        safety.setEStop(status.getEStop());
        safety.setFieldViolation(status.isFieldViolation());
        agvState.setSafetyState(safety);

        // Update loads
        if (status.getLoads() != null) {
            agvState.setLoads(status.getLoads());
        }

        // Merge external errors
        if (status.getErrors() != null) {
            // Replace non-protocol errors from FMS
            agvState.getErrors().removeIf(e -> !isProtocolError(e));
            agvState.getErrors().addAll(status.getErrors());
        }
    }

    private boolean isProtocolError(com.navasmart.vda5050.model.Error error) {
        String type = error.getErrorType();
        return type != null && (type.startsWith("validation") || type.startsWith("orderUpdate")
                || type.startsWith("noOrderTo") || type.startsWith("navigation")
                || type.startsWith("action"));
    }
}
