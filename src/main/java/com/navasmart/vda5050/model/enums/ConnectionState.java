package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConnectionState {
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    CONNECTIONBROKEN("CONNECTIONBROKEN");

    private final String value;

    ConnectionState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
