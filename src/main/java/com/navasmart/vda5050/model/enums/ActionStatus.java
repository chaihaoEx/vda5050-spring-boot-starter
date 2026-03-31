package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 动作执行状态枚举。
 * <p>
 * 对应 VDA5050 协议中动作状态（ActionState）的 {@code actionStatus} 字段，
 * 用于描述 AGV 上某个动作当前的执行状态。
 * </p>
 */
public enum ActionStatus {
    /**
     * 等待中。动作已被 AGV 接受，等待触发条件满足后开始执行。
     */
    WAITING("WAITING"),
    /**
     * 初始化中。动作正在进行初始化准备工作。
     */
    INITIALIZING("INITIALIZING"),
    /**
     * 执行中。动作正在运行。
     */
    RUNNING("RUNNING"),
    /**
     * 已暂停。动作执行被暂停，可恢复继续执行。
     */
    PAUSED("PAUSED"),
    /**
     * 已完成。动作已成功执行完毕。此为终态。
     */
    FINISHED("FINISHED"),
    /**
     * 已失败。动作执行失败。此为终态。
     */
    FAILED("FAILED");

    private final String value;

    ActionStatus(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 动作状态的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 动作状态的字符串值
     * @return 对应的 {@link ActionStatus} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static ActionStatus fromValue(String value) {
        for (ActionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ActionStatus: " + value);
    }

    /**
     * 判断当前动作状态是否为终态（已完成或已失败）。
     *
     * @return 如果状态为 {@link #FINISHED} 或 {@link #FAILED} 则返回 {@code true}
     */
    public boolean isTerminal() {
        return this == FINISHED || this == FAILED;
    }
}
