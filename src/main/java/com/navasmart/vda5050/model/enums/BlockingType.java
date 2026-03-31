package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BlockingType {
    NONE("NONE"),
    SOFT("SOFT"),
    HARD("HARD");

    private final String value;

    BlockingType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
