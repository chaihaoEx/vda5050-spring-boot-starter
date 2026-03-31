package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 急停类型枚举。
 * <p>
 * 对应 VDA5050 协议中 AGV 状态（State）的 {@code eStop} 字段，
 * 用于描述 AGV 当前的急停状态及其触发方式。
 * </p>
 */
public enum EStopType {
    /**
     * 自动确认急停。急停条件消除后可自动恢复，无需人工手动确认。
     */
    AUTOACK("AUTOACK"),
    /**
     * 手动急停。由现场操作人员通过物理急停按钮触发，需人工手动解除。
     */
    MANUAL("MANUAL"),
    /**
     * 远程急停。由远程监控系统触发的急停。
     */
    REMOTE("REMOTE"),
    /**
     * 无急停。AGV 当前未处于任何急停状态。
     */
    NONE("NONE");

    private final String value;

    EStopType(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 急停类型的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 急停类型的字符串值
     * @return 对应的 {@link EStopType} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static EStopType fromValue(String value) {
        for (EStopType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EStopType: " + value);
    }
}
