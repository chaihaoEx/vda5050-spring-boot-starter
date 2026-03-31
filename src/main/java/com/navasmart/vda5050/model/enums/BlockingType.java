package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 动作阻塞类型枚举。
 * <p>
 * 对应 VDA5050 协议中动作（Action）的 {@code blockingType} 字段，
 * 用于定义动作在执行时对 AGV 行驶和其他动作的阻塞行为。
 * </p>
 */
public enum BlockingType {
    /**
     * 不阻塞。AGV 在执行该动作时可以继续行驶，也可以同时执行其他动作。
     */
    NONE("NONE"),
    /**
     * 软阻塞。AGV 在执行该动作时不可行驶，但允许同时执行其他非阻塞动作。
     */
    SOFT("SOFT"),
    /**
     * 硬阻塞。AGV 在执行该动作时不可行驶，也不可执行其他任何动作，独占执行。
     */
    HARD("HARD");

    private final String value;

    BlockingType(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 阻塞类型的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 阻塞类型的字符串值
     * @return 对应的 {@link BlockingType} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static BlockingType fromValue(String value) {
        for (BlockingType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BlockingType: " + value);
    }
}
