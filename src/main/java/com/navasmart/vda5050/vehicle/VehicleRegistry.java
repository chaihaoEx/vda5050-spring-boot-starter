package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class VehicleRegistry {

    private static final Logger log = LoggerFactory.getLogger(VehicleRegistry.class);

    private final ConcurrentHashMap<String, VehicleContext> vehicles = new ConcurrentHashMap<>();
    private final Vda5050Properties properties;

    public VehicleRegistry(Vda5050Properties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getProxy().isEnabled()) {
            for (Vda5050Properties.VehicleConfig vc : properties.getProxy().getVehicles()) {
                VehicleContext ctx = getOrCreate(vc.getManufacturer(), vc.getSerialNumber());
                ctx.setProxyMode(true);
                log.info("Registered proxy vehicle: {}", ctx.getVehicleId());
            }
        }
        if (properties.getServer().isEnabled()) {
            for (Vda5050Properties.VehicleConfig vc : properties.getServer().getVehicles()) {
                VehicleContext ctx = getOrCreate(vc.getManufacturer(), vc.getSerialNumber());
                ctx.setServerMode(true);
                log.info("Registered server vehicle: {}", ctx.getVehicleId());
            }
        }
    }

    public VehicleContext getOrCreate(String manufacturer, String serialNumber) {
        String key = manufacturer + ":" + serialNumber;
        return vehicles.computeIfAbsent(key, k -> new VehicleContext(manufacturer, serialNumber));
    }

    public VehicleContext get(String vehicleId) {
        return vehicles.get(vehicleId);
    }

    public VehicleContext get(String manufacturer, String serialNumber) {
        return vehicles.get(manufacturer + ":" + serialNumber);
    }

    public Collection<VehicleContext> getProxyVehicles() {
        return vehicles.values().stream()
                .filter(VehicleContext::isProxyMode)
                .collect(Collectors.toList());
    }

    public Collection<VehicleContext> getServerVehicles() {
        return vehicles.values().stream()
                .filter(VehicleContext::isServerMode)
                .collect(Collectors.toList());
    }

    public Collection<VehicleContext> getAll() {
        return vehicles.values();
    }
}
