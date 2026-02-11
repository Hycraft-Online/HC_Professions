package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum Tradeskill {
    MINING("Mining", "#B4783C"),
    WOODCUTTING("Woodcutting", "#64B43C"),
    FARMING("Farming", "#3CB43C"),
    HERBALISM("Herbalism", "#8B5CF6"),
    SKINNING("Skinning", "#C8966E"),
    FISHING("Fishing", "#3C78C8");

    private final String defaultDisplayName;
    private final String defaultColorHex;

    private static volatile Map<String, SkillDefinition> definitions = Collections.emptyMap();

    Tradeskill(String defaultDisplayName, String defaultColorHex) {
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
    public static Tradeskill fromString(String value) {
        if (value == null) return null;
        for (Tradeskill t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.getDisplayName().equalsIgnoreCase(value)) {
                return t;
            }
        }
        return null;
    }
}
