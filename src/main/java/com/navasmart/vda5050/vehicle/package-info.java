/**
 * 多车辆管理，提供线程安全的车辆状态容器和注册表。
 *
 * <ul>
 *   <li>{@link com.navasmart.vda5050.vehicle.VehicleContext}
 *       — 单车状态容器，使用 {@link java.util.concurrent.locks.ReentrantLock} 保证线程安全</li>
 *   <li>{@link com.navasmart.vda5050.vehicle.VehicleRegistry}
 *       — 车辆注册表，基于 {@link java.util.concurrent.ConcurrentHashMap} 实现</li>
 * </ul>
 *
 * <h2>线程安全规则</h2>
 * <ul>
 *   <li>修改车辆状态前必须持有该车辆的锁（{@code ctx.lock()}）</li>
 *   <li>持锁期间禁止进行 I/O 操作（MQTT 发布等），应在释放锁后执行</li>
 *   <li>锁粒度为单车，不同车辆之间无竞争</li>
 * </ul>
 */
package com.navasmart.vda5050.vehicle;
