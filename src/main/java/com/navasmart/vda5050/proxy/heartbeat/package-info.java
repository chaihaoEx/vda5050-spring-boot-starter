/**
 * Proxy 模式心跳管理，负责 AGV 状态上报和连接状态发布。
 *
 * <ul>
 *   <li>{@link com.navasmart.vda5050.proxy.heartbeat.ProxyHeartbeatScheduler}
 *       — 定时发布 AgvState（默认 1 秒间隔）</li>
 *   <li>{@link com.navasmart.vda5050.proxy.heartbeat.ProxyConnectionPublisher}
 *       — 启动时发布 ONLINE，关闭时发布 OFFLINE</li>
 * </ul>
 */
package com.navasmart.vda5050.proxy.heartbeat;
