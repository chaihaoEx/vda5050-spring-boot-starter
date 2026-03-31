package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InfoLevel {
    DEBUG("DEBUG"),
    INFO("INFO");

    private final String value;

    InfoLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
