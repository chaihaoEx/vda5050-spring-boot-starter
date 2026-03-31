package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderDispatcher.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;

    public OrderDispatcher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                           Vda5050Properties properties) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    public SendResult sendOrder(String vehicleId, Order order) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        ctx.lock();
        try {
            order.setHeaderId(ctx.nextOrderHeaderId());
            order.setTimestamp(TimestampUtil.now());
            order.setVersion(properties.getMqtt().getProtocolVersion());
            order.setManufacturer(ctx.getManufacturer());
            order.setSerialNumber(ctx.getSerialNumber());

            ctx.setLastSentOrder(order);
        } finally {
            ctx.unlock();
        }

        mqttGateway.publishOrder(ctx.getManufacturer(), ctx.getSerialNumber(), order);
        log.info("Sent order {} to vehicle {}", order.getOrderId(), vehicleId);
        return SendResult.success();
    }

    public SendResult sendOrderUpdate(String vehicleId, Order orderUpdate) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        ctx.lock();
        try {
            Order lastSent = ctx.getLastSentOrder();
            if (lastSent != null && !lastSent.getOrderId().equals(orderUpdate.getOrderId())) {
                return SendResult.failure("Order ID mismatch: expected " + lastSent.getOrderId());
            }
        } finally {
            ctx.unlock();
        }

        return sendOrder(vehicleId, orderUpdate);
    }
}
