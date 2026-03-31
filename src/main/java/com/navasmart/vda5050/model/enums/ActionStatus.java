package com.navasmart.vda5050.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ActionStatus {
    WAITING("WAITING"),
    INITIALIZING("INITIALIZING"),
    RUNNING("RUNNING"),
    PAUSED("PAUSED"),
    FINISHED("FINISHED"),
    FAILED("FAILED");

    private final String value;

    ActionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ActionStatus fromValue(String value) {
        for (ActionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ActionStatus: " + value);
    }

    public boolean isTerminal() {
        return this == FINISHED || this == FAILED;
    }
}
