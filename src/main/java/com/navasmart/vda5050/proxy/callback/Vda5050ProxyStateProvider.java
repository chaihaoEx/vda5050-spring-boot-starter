package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Factsheet;

/**
 * VDA5050 Proxy 模式下的车辆状态提供者接口。
 *
 * <p>用户必须实现此接口并注册为 Spring Bean。框架会按照心跳间隔
 * （默认每 1000ms，可通过 {@code vda5050.proxy.heartbeatIntervalMs} 配置）
 * 周期性调用 {@link #getVehicleStatus(String)} 方法获取车辆最新状态，
 * 并将状态填充到 VDA5050 State 消息中通过 MQTT 发布。</p>
 *
 * <p>线程安全：此接口的方法会在心跳调度线程中被周期性调用，
 * 实现类应确保线程安全，避免返回过时或不一致的数据。</p>
 *
 * @see VehicleStatus
 * @see Vda5050ProxyVehicleAdapter
 */
public interface Vda5050ProxyStateProvider {

    /**
     * 获取指定车辆的当前状态信息。
     *
     * <p>此方法按心跳间隔被周期性调用（默认 1000ms），返回值将被映射到 VDA5050 State 消息的
     * 位置、速度、电池、安全状态等字段中。</p>
     *
     * <p>返回值要求：
     * <ul>
     *   <li>不应返回 {@code null}，否则本轮心跳将跳过状态更新</li>
     *   <li>{@link VehicleStatus#getMapId()} 和位置坐标应始终填充有效值</li>
     *   <li>{@link VehicleStatus#getEStop()} 应返回 VDA5050 规范定义的急停状态字符串</li>
     *   <li>{@link VehicleStatus#getBatteryCharge()} 应返回 0.0 - 100.0 之间的百分比值</li>
     * </ul>
     * </p>
     *
     * @param vehicleId 车辆标识符
     * @return 车辆当前状态，不应返回 {@code null}
     */
    VehicleStatus getVehicleStatus(String vehicleId);

    /**
     * 获取指定车辆的 Factsheet（车辆能力描述）。
     *
     * <p>调用时机：当收到 {@code factsheetRequest} 即时动作时被调用，不会周期性调用。
     * 返回的 Factsheet 将通过 MQTT 发布到对应的 factsheet 主题。</p>
     *
     * @param vehicleId 车辆标识符
     * @return 车辆的 Factsheet 信息；如果返回 {@code null}，则不发布 factsheet 消息
     */
    Factsheet getFactsheet(String vehicleId);
}
