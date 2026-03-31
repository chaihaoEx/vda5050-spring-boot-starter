package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EStopType {
    AUTOACK("AUTOACK"),
    MANUAL("MANUAL"),
    REMOTE("REMOTE"),
    NONE("NONE");

    private final String value;

    EStopType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
