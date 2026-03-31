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

    public void processConnection(String vehicleId, Connection connection) {
        VehicleContext ctx = vehicleRegistry.get(vehicleId);
        if (ctx == null) return;

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
