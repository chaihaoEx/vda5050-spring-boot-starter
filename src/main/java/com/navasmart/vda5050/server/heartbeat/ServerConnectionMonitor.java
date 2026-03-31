package com.navasmart.vda5050.server.heartbeat;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import com.navasmart.vda5050.model.Connection;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;
import com.navasmart.vda5050.util.TimestampUtil;
import com.navasmart.vda5050.vehicle.VehicleContext;
import com.navasmart.vda5050.vehicle.VehicleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Server 模式下的连接状态监控器，负责检测 AGV 超时和处理 Connection 消息。
 *
 * <p>两项核心职责：
 * <ul>
 *   <li><b>超时检测</b>：按固定间隔（默认 30s，通过 {@code vda5050.server.connectionCheckMs} 配置）
 *       检查每辆车距上次收到 State 消息的时间，超过阈值（{@code vda5050.server.stateTimeoutMs}）
 *       时触发 {@link Vda5050ServerAdapter#onVehicleTimeout} 回调</li>
 *   <li><b>Connection 消息处理</b>：接收 AGV 发布的 Connection 消息，
 *       检测连接状态变化并触发 {@link Vda5050ServerAdapter#onConnectionStateChanged} 回调</li>
 * </ul>
 * </p>
 *
 * <p>线程安全：超时检测在调度线程中执行，Connection 处理通过 VehicleContext 锁保护。</p>
 *
 * @see Vda5050ServerAdapter
 */
@Component
public class ServerConnectionMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServerConnectionMonitor.class);

    private final VehicleRegistry vehicleRegistry;
    private final Vda5050Properties properties;
    private final Vda5050ServerAdapter serverAdapter;

    public ServerConnectionMonitor(VehicleRegistry vehicleRegistry, Vda5050Properties properties,
                                   Vda5050ServerAdapter serverAdapter) {
        this.vehicleRegistry = vehicleRegistry;
        this.properties = properties;
        this.serverAdapter = serverAdapter;
    }

    /**
     * 定期检查所有 Server 模式车辆的连接状态，检测超时车辆。
     *
     * <p>检查间隔默认 30s，可通过 {@code vda5050.server.connectionCheckMs} 配置。
     * 超时阈值由 {@code vda5050.server.stateTimeoutMs} 配置。</p>
     */
    @Scheduled(fixedDelayString = "${vda5050.server.connectionCheckMs:30000}")
    public void checkConnections() {
        long now = System.currentTimeMillis();
        long timeout = properties.getServer().getStateTimeoutMs();

        for (VehicleContext ctx : vehicleRegistry.getServerVehicles()) {
            long lastSeen = ctx.getLastSeenTimestamp();
            if (lastSeen > 0 && (now - lastSeen) > timeout) {
                serverAdapter.onVehicleTimeout(ctx.getVehicleId(), TimestampUtil.format(lastSeen));
            }
        }
    }

    /**
     * 处理收到的 AGV Connection 消息，检测连接状态变化。
     *
     * <p>当连接状态（ONLINE/OFFLINE/CONNECTIONBROKEN）与之前记录的不同时，
     * 触发 {@link Vda5050ServerAdapter#onConnectionStateChanged} 回调。</p>
     *
     * @param vehicleId  车辆标识符
     * @param connection 收到的 Connection 消息
     */
    public void processConnection(String vehicleId, Connection connection) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) {
            return;
        }

        ctx.lock();
        try {
            String prevState = ctx.getConnectionState();
            ctx.setConnectionState(connection.getConnectionState());

            if (!connection.getConnectionState().equals(prevState)) {
                serverAdapter.onConnectionStateChanged(vehicleId, connection.getConnectionState());
                log.info("Vehicle {} connection state: {} -> {}", vehicleId,
                        prevState, connection.getConnectionState());
            }
        } finally {
            ctx.unlock();
        }
    }
}
