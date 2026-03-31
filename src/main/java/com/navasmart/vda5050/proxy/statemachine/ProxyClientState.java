package com.navasmart.vda5050.proxy.statemachine;

/**
 * Proxy 模式下车辆客户端的状态枚举，表示订单执行的生命周期状态。
 *
 * <p>状态转换规则：
 * <ul>
 *   <li>{@code IDLE -> RUNNING}：收到新订单且满足接受条件时</li>
 *   <li>{@code RUNNING -> PAUSED}：收到 startPause 即时动作时</li>
 *   <li>{@code PAUSED -> RUNNING}：收到 stopPause 即时动作时</li>
 *   <li>{@code RUNNING -> IDLE}：订单执行完成、cancelOrder 或发生 FATAL 错误时</li>
 *   <li>{@code PAUSED -> IDLE}：不允许（PAUSED 状态下不接受新订单也不直接转 IDLE）</li>
 * </ul>
 *
 * @see ProxyOrderStateMachine
 */
public enum ProxyClientState {
    /** 空闲状态，没有正在执行的订单，可以接受新订单 */
    IDLE,
    /** 运行状态，正在执行订单（导航或执行动作） */
    RUNNING,
    /** 暂停状态，订单执行被暂停，等待 stopPause 恢复 */
    PAUSED
}
