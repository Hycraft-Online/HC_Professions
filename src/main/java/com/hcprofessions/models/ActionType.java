package com.hcprofessions.models;

import javax.annotation.Nullable;

public enum ActionType {
    KILL,
    PICKUP,
    TEMPER,
    CRAFT,
    PLACE,
    GATHER;

    @Nullable
    public static ActionType fromString(String value) {
        if (value == null) return null;
        for (ActionType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }
}
