package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.InstantActions;
import com.navasmart.vda5050.model.enums.BlockingType;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class InstantActionSender {

    private static final Logger log = LoggerFactory.getLogger(InstantActionSender.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    public InstantActionSender(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                               Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    public SendResult send(String vehicleId, List<Action> actions) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        InstantActions msg = new InstantActions();
        msg.setHeaderId(ctx.nextInstantActionsHeaderId());
        msg.setTimestamp(TimestampUtil.now());
        msg.setVersion(properties.getMqtt().getProtocolVersion());
        msg.setManufacturer(ctx.getManufacturer());
        msg.setSerialNumber(ctx.getSerialNumber());
        msg.setInstantActions(actions);

        mqttGateway.publishInstantActions(ctx.getManufacturer(), ctx.getSerialNumber(), msg);
        log.info("Sent {} instant action(s) to vehicle {}", actions.size(), vehicleId);
        return SendResult.success();
    }

    public SendResult cancelOrder(String vehicleId) {
        return sendBuiltinAction(vehicleId, "cancelOrder");
    }

    public SendResult pauseVehicle(String vehicleId) {
        return sendBuiltinAction(vehicleId, "startPause");
    }

    public SendResult resumeVehicle(String vehicleId) {
        return sendBuiltinAction(vehicleId, "stopPause");
    }

    public SendResult requestFactsheet(String vehicleId) {
        return sendBuiltinAction(vehicleId, "factsheetRequest");
    }

    private SendResult sendBuiltinAction(String vehicleId, String actionType) {
        Action action = new Action();
        action.setActionId(UUID.randomUUID().toString());
        action.setActionType(actionType);
        action.setBlockingType(BlockingType.NONE.getValue());
        return send(vehicleId, Collections.singletonList(action));
    }
}
