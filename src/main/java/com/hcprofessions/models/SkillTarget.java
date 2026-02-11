package com.hcprofessions.models;

import javax.annotation.Nullable;

public enum SkillTarget {
    PROFESSION,
    TRADESKILL;

    @Nullable
    public static SkillTarget fromString(String value) {
        if (value == null) return null;
        for (SkillTarget t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }
}
