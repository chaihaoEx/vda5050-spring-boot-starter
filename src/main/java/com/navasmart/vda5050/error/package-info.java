/**
 * VDA5050 错误处理，提供错误创建和聚合管理。
 *
 * <p>VDA5050 定义了两个错误级别：
 * <ul>
 *   <li><b>WARNING</b> — 非致命错误，不影响订单执行</li>
 *   <li><b>FATAL</b> — 致命错误，触发订单中止</li>
 * </ul>
 *
 * @see com.navasmart.vda5050.error.Vda5050ErrorFactory
 * @see com.navasmart.vda5050.error.ErrorAggregator
 */
package com.navasmart.vda5050.error;
