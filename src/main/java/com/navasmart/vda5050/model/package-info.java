/**
 * VDA5050 协议数据模型。
 *
 * <p>包含 VDA5050 规范定义的所有消息类型 POJO，共 28 个类。
 * 所有模型均使用 {@link com.fasterxml.jackson.annotation.JsonInclude JsonInclude.NON_NULL}
 * 注解，序列化时忽略空值字段。
 *
 * <h2>核心消息类型</h2>
 * <ul>
 *   <li>{@link com.navasmart.vda5050.model.Order} — 订单消息（调度系统 → AGV）</li>
 *   <li>{@link com.navasmart.vda5050.model.AgvState} — AGV 状态上报（AGV → 调度系统）</li>
 *   <li>{@link com.navasmart.vda5050.model.InstantActions} — 即时动作指令</li>
 *   <li>{@link com.navasmart.vda5050.model.Connection} — 连接状态（QoS 1, retained）</li>
 *   <li>{@link com.navasmart.vda5050.model.Factsheet} — AGV 能力描述</li>
 * </ul>
 *
 * <p><b>注意</b>：{@link com.navasmart.vda5050.model.Error} 与 {@link java.lang.Error}
 * 存在命名冲突，在同时引用时需使用全限定名。
 */
package com.navasmart.vda5050.model;
