/**
 * Server 模式回调接口，由使用者实现以接收 AGV 状态变化通知。
 *
 * <ul>
 *   <li>{@link com.navasmart.vda5050.server.callback.Vda5050ServerAdapter}
 *       — 核心回调接口，接收状态更新、订单完成/失败、连接变化等事件</li>
 *   <li>{@link com.navasmart.vda5050.server.callback.SendResult}
 *       — 指令发送结果 DTO</li>
 * </ul>
 */
package com.navasmart.vda5050.server.callback;
