package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OperatingMode {
    AUTOMATIC("AUTOMATIC"),
    SEMIAUTOMATIC("SEMIAUTOMATIC"),
    MANUAL("MANUAL"),
    SERVICE("SERVICE"),
    TEACHIN("TEACHIN");

    private final String value;

    OperatingMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
