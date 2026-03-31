/**
 * Server 模式订单派发，提供向 AGV 发送订单和即时动作的 API。
 *
 * <ul>
 *   <li>{@link com.navasmart.vda5050.server.dispatch.OrderDispatcher} — 发送订单和订单更新</li>
 *   <li>{@link com.navasmart.vda5050.server.dispatch.InstantActionSender}
 *       — 发送即时动作（含 cancelOrder、pauseVehicle 等便捷方法）</li>
 *   <li>{@link com.navasmart.vda5050.server.dispatch.OrderBuilder} — 链式构建 Order 对象</li>
 * </ul>
 */
package com.navasmart.vda5050.server.dispatch;
