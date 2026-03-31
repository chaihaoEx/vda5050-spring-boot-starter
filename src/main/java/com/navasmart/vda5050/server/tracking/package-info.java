/**
 * Server 模式状态追踪，负责检测 AGV 状态变化并触发回调。
 *
 * <ul>
 *   <li>{@link com.navasmart.vda5050.server.tracking.AgvStateTracker}
 *       — 处理 AGV 状态消息，检测节点到达、动作变化、订单完成和错误变更</li>
 *   <li>{@link com.navasmart.vda5050.server.tracking.OrderProgressTracker}
 *       — 查询当前订单执行进度</li>
 *   <li>{@link com.navasmart.vda5050.server.tracking.OrderProgress}
 *       — 订单进度快照 DTO</li>
 * </ul>
 */
package com.navasmart.vda5050.server.tracking;
