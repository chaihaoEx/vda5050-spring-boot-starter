package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 AGV 操作模式枚举。
 * <p>
 * 对应 VDA5050 协议中 AGV 状态（State）的 {@code operatingMode} 字段，
 * 用于描述 AGV 当前的操作模式。
 * </p>
 */
public enum OperatingMode {
    /**
     * 全自动模式。AGV 完全由主控系统控制，自动执行订单和导航。
     */
    AUTOMATIC("AUTOMATIC"),
    /**
     * 半自动模式。AGV 自动执行任务，但可能需要人工辅助确认或干预。
     */
    SEMIAUTOMATIC("SEMIAUTOMATIC"),
    /**
     * 手动模式。AGV 由操作人员手动控制行驶和操作。
     */
    MANUAL("MANUAL"),
    /**
     * 维护模式。AGV 处于维护或服务状态，不接受主控系统的订单。
     */
    SERVICE("SERVICE"),
    /**
     * 示教模式。AGV 处于路径或动作示教状态，用于录制地图或动作参数。
     */
    TEACHIN("TEACHIN");

    private final String value;

    OperatingMode(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 操作模式的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 操作模式的字符串值
     * @return 对应的 {@link OperatingMode} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static OperatingMode fromValue(String value) {
        for (OperatingMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown OperatingMode: " + value);
    }
}
