package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 错误级别枚举。
 * <p>
 * 对应 VDA5050 协议中错误对象（Error）的 {@code errorLevel} 字段，
 * 用于区分错误的严重程度，主控系统据此决定相应的处理策略。
 * </p>
 */
public enum ErrorLevel {
    /**
     * 警告级别。非致命错误，AGV 仍可继续运行，但需要关注。
     */
    WARNING("WARNING"),
    /**
     * 致命级别。严重错误，AGV 将触发订单中止，需要人工干预或恢复。
     */
    FATAL("FATAL");

    private final String value;

    ErrorLevel(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 错误级别的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 错误级别的字符串值
     * @return 对应的 {@link ErrorLevel} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static ErrorLevel fromValue(String value) {
        for (ErrorLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown ErrorLevel: " + value);
    }
}
