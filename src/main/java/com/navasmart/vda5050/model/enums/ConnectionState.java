package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * VDA5050 连接状态枚举。
 * <p>
 * 对应 VDA5050 协议中 AGV 与主控系统之间的连接状态（Connection）的 {@code connectionState} 字段，
 * 用于表示 AGV 的 MQTT 连接当前状态。
 * </p>
 */
public enum ConnectionState {
    /**
     * 在线。AGV 与主控系统之间的 MQTT 连接正常。
     */
    ONLINE("ONLINE"),
    /**
     * 离线。AGV 正常断开了与主控系统的连接。
     */
    OFFLINE("OFFLINE"),
    /**
     * 连接中断。由 MQTT 的 Last Will and Testament（LWT）机制触发，
     * 表示 AGV 的连接异常断开。
     */
    CONNECTIONBROKEN("CONNECTIONBROKEN");

    private final String value;

    ConnectionState(String value) {
        this.value = value;
    }

    /**
     * 获取枚举对应的字符串值。
     *
     * @return 连接状态的字符串表示
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值反序列化为对应的枚举实例。
     *
     * @param value 连接状态的字符串值
     * @return 对应的 {@link ConnectionState} 枚举实例
     * @throws IllegalArgumentException 如果给定值不匹配任何枚举常量
     */
    @JsonCreator
    public static ConnectionState fromValue(String value) {
        for (ConnectionState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown ConnectionState: " + value);
    }
}
