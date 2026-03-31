package com.navasmart.vda5050.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * VDA5050 时间戳工具类，提供符合 ISO 8601 格式的 UTC 时间戳生成与格式化。
 *
 * <p>输出格式为 {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}，时区固定为 UTC。
 * VDA5050 协议要求所有消息头中的 {@code timestamp} 字段均使用此格式。</p>
 *
 * <p>本类为无状态工具类，所有方法均为静态方法，线程安全。</p>
 *
 * <p>用法示例：
 * <pre>
 *   String ts = TimestampUtil.now();               // 当前 UTC 时间
 *   String ts = TimestampUtil.format(epochMillis);  // 指定毫秒时间戳
 * </pre>
 * </p>
 */
public final class TimestampUtil {

    /** ISO 8601 格式化器，时区固定为 UTC，精确到毫秒 */
    private static final DateTimeFormatter ISO8601 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    /** 私有构造函数，防止实例化 */
    private TimestampUtil() {}

    /**
     * 获取当前 UTC 时间的 ISO 8601 格式字符串。
     *
     * @return 当前时间的 ISO 8601 字符串，例如 {@code "2024-01-15T08:30:00.123Z"}
     */
    public static String now() {
        return ISO8601.format(Instant.now());
    }

    /**
     * 将 epoch 毫秒时间戳格式化为 ISO 8601 字符串。
     *
     * @param epochMillis 自 Unix 纪元（1970-01-01T00:00:00Z）以来的毫秒数
     * @return 对应时间的 ISO 8601 字符串
     */
    public static String format(long epochMillis) {
        return ISO8601.format(Instant.ofEpochMilli(epochMillis));
    }
}
