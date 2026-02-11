package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum Profession {
    WEAPONSMITH("Weaponsmith", "#C83232"),
    ARMORSMITH("Armorsmith", "#3278C8"),
    ALCHEMIST("Alchemist", "#8B5CF6"),
    COOK("Cook", "#E89040"),
    LEATHERWORKER("Leatherworker", "#8B6914"),
    CARPENTER("Carpenter", "#B47832"),
    TAILOR("Tailor", "#C864C8"),
    ENCHANTER("Enchanter", "#6432C8"),
    BUILDER("Builder", "#4A9E5C");

    private final String defaultDisplayName;
    private final String defaultColorHex;

    private static volatile Map<String, SkillDefinition> definitions = Collections.emptyMap();

    Profession(String defaultDisplayName, String defaultColorHex) {
        this.defaultDisplayName = defaultDisplayName;
        this.defaultColorHex = defaultColorHex;
    }

    public String getDisplayName() {
        SkillDefinition def = definitions.get(this.name());
        return def != null ? def.displayName() : defaultDisplayName;
    }

    public Color getColor() {
        SkillDefinition def = definitions.get(this.name());
        if (def != null) return def.getColor();
        try {
            return Color.decode(defaultColorHex);
        } catch (NumberFormatException e) {
            return Color.GRAY;
        }
    }

    public static void setDefinitions(List<SkillDefinition> defs) {
        Map<String, SkillDefinition> map = new LinkedHashMap<>();
        for (SkillDefinition def : defs) {
            map.put(def.id(), def);
        }
        definitions = map;
    }

    public static Map<String, SkillDefinition> getDefinitions() {
        return definitions;
    }

    @Nullable
    public static Profession fromString(String value) {
        if (value == null) return null;
        for (Profession p : values()) {
            if (p.name().equalsIgnoreCase(value) || p.getDisplayName().equalsIgnoreCase(value)) {
                return p;
            }
        }
        return null;
    }
}
