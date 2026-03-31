/**
 * Proxy 模式回调接口，由使用者实现以对接自有车队管理系统（FMS）。
 *
 * <h2>必须实现的接口</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter}
 *       — 接收导航、动作执行、暂停/恢复等指令</li>
 *   <li>{@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider}
 *       — 提供车辆实时状态和能力描述</li>
 * </ul>
 *
 * <h2>DTO 类</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.proxy.callback.NavigationResult} — 导航结果</li>
 *   <li>{@link com.navasmart.vda5050.proxy.callback.ActionResult} — 动作执行结果</li>
 *   <li>{@link com.navasmart.vda5050.proxy.callback.VehicleStatus} — 车辆状态快照</li>
 * </ul>
 */
package com.navasmart.vda5050.proxy.callback;
