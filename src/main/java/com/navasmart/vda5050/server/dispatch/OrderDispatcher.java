package com.navasmart.vda5050.server.dispatch;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.error.ErrorAggregator;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.mqtt.MqttGateway;
import com.navasmart.vda5050.server.callback.SendResult;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Server 模式下的订单下发器，负责向 AGV 发送 VDA5050 Order 消息。
 *
 * <p>自动填充消息头字段（headerId、timestamp、version、manufacturer、serialNumber），
 * 用户只需构建订单内容（nodes、edges 等）。可配合 {@link OrderBuilder} 使用。</p>
 *
 * <p>线程安全：通过 VehicleContext 的锁机制保证线程安全。</p>
 *
 * @see OrderBuilder
 * @see SendResult
 */
@Component
public class OrderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderDispatcher.class);

    private final VehicleRegistry vehicleRegistry;
    private final MqttGateway mqttGateway;
    private final Vda5050Properties properties;
    private final ErrorAggregator errorAggregator;

    public OrderDispatcher(VehicleRegistry vehicleRegistry, MqttGateway mqttGateway,
                           Vda5050Properties properties, ErrorAggregator errorAggregator) {
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
        this.properties = properties;
        this.errorAggregator = errorAggregator;
    }

    /**
     * 向指定车辆发送新订单。
     *
     * <p>自动填充消息头字段并通过 MQTT 发布。发送成功后将订单记录为 lastSentOrder，
     * 用于后续的订单进度追踪和完成检测。</p>
     *
     * @param vehicleId 目标车辆标识符
     * @param order     待发送的订单（headerId 等字段会被自动覆盖）
     * @return 发送结果；车辆未注册时返回失败
     */
    public SendResult sendOrder(String vehicleId, Order order) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        ctx.lock();
        try {
            if (errorAggregator.hasFatalError(ctx)) {
                return SendResult.failure("Vehicle " + vehicleId + " has FATAL error, refusing to send order");
            }
        } finally {
            ctx.unlock();
        }

        ctx.lockServer();
        try {
            order.setHeaderId(ctx.nextOrderHeaderId());
            order.setTimestamp(TimestampUtil.now());
            order.setVersion(properties.getMqtt().getProtocolVersion());
            order.setManufacturer(ctx.getManufacturer());
            order.setSerialNumber(ctx.getSerialNumber());

            ctx.setLastSentOrder(order);
            ctx.removeCompletedOrderId(order.getOrderId());
        } finally {
            ctx.unlockServer();
        }

        boolean published = mqttGateway.publishOrder(ctx.getManufacturer(), ctx.getSerialNumber(), order);
        if (!published) {
            return SendResult.failure("Failed to publish order via MQTT");
        }
        log.info("Sent order {} to vehicle {}", order.getOrderId(), vehicleId);
        return SendResult.success();
    }

    /**
     * 向指定车辆发送订单更新（追加 horizon）。
     *
     * <p>校验 orderId 是否与上次发送的订单一致，不一致时返回失败。
     * 校验通过后委托 {@link #sendOrder} 执行实际发送。</p>
     *
     * @param vehicleId   目标车辆标识符
     * @param orderUpdate 订单更新消息（orderId 必须与上次发送的订单匹配）
     * @return 发送结果；车辆未注册或 orderId 不匹配时返回失败
     */
    public SendResult sendOrderUpdate(String vehicleId, Order orderUpdate) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return SendResult.failure("Vehicle not registered: " + vehicleId);
        }

        ctx.lockServer();
        try {
            Order lastSent = ctx.getLastSentOrder();
            if (lastSent != null && !lastSent.getOrderId().equals(orderUpdate.getOrderId())) {
                return SendResult.failure("Order ID mismatch: expected " + lastSent.getOrderId());
            }
        } finally {
            ctx.unlockServer();
        }

        return sendOrder(vehicleId, orderUpdate);
    }
}
