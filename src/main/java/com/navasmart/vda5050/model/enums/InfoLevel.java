package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 信息级别枚举。
 * <p>
 * 对应 VDA5050 协议中信息对象（Information）的 {@code infoLevel} 字段，
 * 用于区分 AGV 上报信息的详细程度。
 * </p>
 */
public enum InfoLevel {
    /**
     * 调试级别。用于调试目的的详细信息，通常仅在排查问题时关注。
     */
    DEBUG("DEBUG"),
    /**
     * 信息级别。一般性运行信息，用于常规状态通知。
     */
    INFO("INFO");

    private final String value;

    InfoLevel(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 信息级别的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 信息级别的字符串值
     * @return 对应的 {@link InfoLevel} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static InfoLevel fromValue(String value) {
        for (InfoLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown InfoLevel: " + value);
    }
}
