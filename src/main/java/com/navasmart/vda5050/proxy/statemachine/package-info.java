/**
 * Proxy 模式订单状态机，管理订单的接收、执行和状态转换。
 *
 * <h2>状态转换</h2>
 * <pre>
 * IDLE ──收到 Order──→ RUNNING
 * RUNNING ──startPause──→ PAUSED
 * PAUSED ──stopPause──→ RUNNING
 * RUNNING ──订单完成/cancelOrder/FATAL──→ IDLE
 * PAUSED ──cancelOrder──→ IDLE
 * </pre>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.proxy.statemachine.ProxyOrderStateMachine} — 订单接收与即时动作处理</li>
 *   <li>{@link com.navasmart.vda5050.proxy.statemachine.ProxyOrderExecutor} — 200ms 执行循环</li>
 *   <li>{@link com.navasmart.vda5050.proxy.statemachine.ProxyClientState} — 状态枚举</li>
 * </ul>
 */
package com.navasmart.vda5050.proxy.statemachine;
