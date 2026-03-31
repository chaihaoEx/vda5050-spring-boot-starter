/**
 * Spring Boot 自动配置，根据配置属性自动注册 Proxy 和 Server 模式所需的 Bean。
 *
 * <h2>配置类</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.autoconfigure.Vda5050AutoConfiguration}
 *       — 共享层 Bean（MQTT、车辆注册表、错误处理、ObjectMapper）</li>
 *   <li>{@link com.navasmart.vda5050.autoconfigure.Vda5050ProxyAutoConfiguration}
 *       — Proxy 模式 Bean（需 {@code vda5050.proxy.enabled=true}）</li>
 *   <li>{@link com.navasmart.vda5050.autoconfigure.Vda5050ServerAutoConfiguration}
 *       — Server 模式 Bean（需 {@code vda5050.server.enabled=true}）</li>
 * </ul>
 *
 * <h2>配置属性</h2>
 * <p>所有属性通过 {@link com.navasmart.vda5050.autoconfigure.Vda5050Properties} 定义，
 * 前缀为 {@code vda5050}。
 */
package com.navasmart.vda5050.autoconfigure;
