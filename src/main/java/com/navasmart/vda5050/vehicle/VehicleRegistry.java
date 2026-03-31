package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 车辆注册表，管理所有已注册 AGV 的 {@link VehicleContext} 实例。
 *
 * <p>内部基于 {@link ConcurrentHashMap} 存储，key 格式为 {@code "manufacturer:serialNumber"}，
 * 线程安全，支持并发读写。</p>
 *
 * <h3>初始化时机</h3>
 * <p>在 Spring 容器启动时通过 {@link PostConstruct} 回调自动从配置中读取 Proxy 和 Server 模式的车辆列表，
 * 并将其注册到表中。后续也可通过 {@link #getOrCreate(String, String)} 动态添加车辆。</p>
 */
@Component
public class VehicleRegistry {

    private static final Logger log = LoggerFactory.getLogger(VehicleRegistry.class);

    /** 车辆上下文映射表，key 格式为 {@code "manufacturer:serialNumber"} */
    private final ConcurrentHashMap<String, VehicleContext> vehicles = new ConcurrentHashMap<>();
    private final Vda5050Properties properties;

    public VehicleRegistry(Vda5050Properties properties) {
        this.properties = properties;
    }

    /**
     * 容器启动时初始化车辆注册表。
     *
     * <p>依次读取配置中 Proxy 和 Server 模式的车辆列表，为每辆车创建（或获取已有的）
     * {@link VehicleContext} 并设置相应模式标志。同一辆车可同时注册为 Proxy 和 Server。</p>
     */
    @PostConstruct
    public void init() {
        // 注册 Proxy 模式车辆
        if (properties.getProxy().isEnabled()) {
            for (Vda5050Properties.VehicleConfig vc : properties.getProxy().getVehicles()) {
                VehicleContext ctx = getOrCreate(vc.getManufacturer(), vc.getSerialNumber());
                ctx.setProxyMode(true);
                log.info("Registered proxy vehicle: {}", ctx.getVehicleId());
            }
        }
        // 注册 Server 模式车辆
        if (properties.getServer().isEnabled()) {
            for (Vda5050Properties.VehicleConfig vc : properties.getServer().getVehicles()) {
                VehicleContext ctx = getOrCreate(vc.getManufacturer(), vc.getSerialNumber());
                ctx.setServerMode(true);
                log.info("Registered server vehicle: {}", ctx.getVehicleId());
            }
        }
    }

    /**
     * 获取或创建指定车辆的上下文。如果注册表中已存在则直接返回，否则创建新实例并存入。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 对应的 {@link VehicleContext}，永不为 null
     */
    public VehicleContext getOrCreate(String manufacturer, String serialNumber) {
        String key = manufacturer + ":" + serialNumber;
        return vehicles.computeIfAbsent(key, k -> new VehicleContext(manufacturer, serialNumber));
    }

    /**
     * 根据车辆 ID 查询上下文。
     *
     * @param vehicleId 车辆 ID，格式为 {@code "manufacturer:serialNumber"}
     * @return 对应的 {@link VehicleContext}，不存在时返回 null
     */
    public VehicleContext get(String vehicleId) {
        return vehicles.get(vehicleId);
    }

    /**
     * 根据制造商和序列号查询车辆上下文。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     * @return 对应的 {@link VehicleContext}，不存在时返回 null
     */
    public VehicleContext get(String manufacturer, String serialNumber) {
        return vehicles.get(manufacturer + ":" + serialNumber);
    }

    /**
     * 获取所有启用 Proxy 模式的车辆。
     *
     * @return Proxy 模式车辆集合
     */
    public Collection<VehicleContext> getProxyVehicles() {
        return vehicles.values().stream()
                .filter(VehicleContext::isProxyMode)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用 Server 模式的车辆。
     *
     * @return Server 模式车辆集合
     */
    public Collection<VehicleContext> getServerVehicles() {
        return vehicles.values().stream()
                .filter(VehicleContext::isServerMode)
                .collect(Collectors.toList());
    }

    /**
     * 获取注册表中所有车辆。
     *
     * @return 全部车辆集合
     */
    public Collection<VehicleContext> getAll() {
        return vehicles.values();
    }
}
