/**
 * Proxy 模式动作处理器框架，支持自定义动作类型的注册和调度。
 *
 * <h2>动作调度优先级</h2>
 * <ol>
 *   <li>内置即时动作（cancelOrder、startPause、stopPause、factsheetRequest）</li>
 *   <li>通过 {@link com.navasmart.vda5050.proxy.action.ActionHandler} 注册的自定义处理器</li>
 *   <li>回退到 {@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter#onActionExecute}</li>
 *   <li>以上均无法处理时标记为 FAILED</li>
 * </ol>
 *
 * <p>使用者可通过实现 {@link com.navasmart.vda5050.proxy.action.ActionHandler} 接口并注册为
 * Spring Bean，自动被 {@link com.navasmart.vda5050.proxy.action.ActionHandlerRegistry} 发现和注册。
 */
package com.navasmart.vda5050.proxy.action;
