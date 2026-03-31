package com.navasmart.vda5050.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimestampUtilTest {

    @Test
    void now_returnsIso8601Format() {
        String ts = TimestampUtil.now();
        assertNotNull(ts);
        // 格式: yyyy-MM-ddTHH:mm:ss.SSSZ
        assertTrue(ts.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "Timestamp should match ISO 8601 format, got: " + ts);
    }

    @Test
    void format_convertsEpochMillis() {
        // 2024-01-01T00:00:00.000Z
        long epoch = Instant.parse("2024-01-01T00:00:00.000Z").toEpochMilli();
        String ts = TimestampUtil.format(epoch);
        assertEquals("2024-01-01T00:00:00.000Z", ts);
    }

    @Test
    void format_handlesNonZeroMillis() {
        long epoch = Instant.parse("2024-06-15T13:45:30.123Z").toEpochMilli();
        String ts = TimestampUtil.format(epoch);
        assertEquals("2024-06-15T13:45:30.123Z", ts);
    }
}
