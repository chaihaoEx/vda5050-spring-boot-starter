package com.navasmart.vda5050.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimestampUtil {

    private static final DateTimeFormatter ISO8601 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private TimestampUtil() {}

    public static String now() {
        return ISO8601.format(Instant.now());
    }

    public static String format(long epochMillis) {
        return ISO8601.format(Instant.ofEpochMilli(epochMillis));
    }
}
