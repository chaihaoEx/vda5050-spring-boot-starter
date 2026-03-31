/**
 * VDA5050 Spring Boot Starter 根包。
 *
 * <p>本项目是一个 VDA5050 协议工具库的 Spring Boot Starter，提供两种运行模式：
 * <ul>
 *   <li><b>Proxy 模式</b>（AGV 侧代理）— 接收外部 VDA5050 调度系统的指令，通过回调接口转发给你的 FMS</li>
 *   <li><b>Server 模式</b>（Master Control）— 向 VDA5050 兼容的 AGV 下发指令，追踪执行状态</li>
 * </ul>
 *
 * <p>两种模式可独立启用或同时运行，通过 {@code vda5050.proxy.enabled} 和
 * {@code vda5050.server.enabled} 配置属性控制。
 *
 * <h2>快速入门</h2>
 * <ol>
 *   <li>引入本 Starter 依赖</li>
 *   <li>在 application.yml 中配置 MQTT 连接和车辆信息</li>
 *   <li>Proxy 模式：实现 {@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter}
 *       和 {@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider}</li>
 *   <li>Server 模式：实现 {@link com.navasmart.vda5050.server.callback.Vda5050ServerAdapter}，
 *       注入 {@link com.navasmart.vda5050.server.dispatch.OrderDispatcher} 下发指令</li>
 * </ol>
 *
 * @see <a href="https://github.com/VDA5050/VDA5050">VDA5050 官方规范</a>
 */
package com.navasmart.vda5050;
