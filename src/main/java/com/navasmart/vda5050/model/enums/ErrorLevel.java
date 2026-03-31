package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorLevel {
    WARNING("WARNING"),
    FATAL("FATAL");

    private final String value;

    ErrorLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
