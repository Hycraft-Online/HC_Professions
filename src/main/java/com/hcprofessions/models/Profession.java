package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum Profession {
    WEAPONSMITH("Bladesmith", "#C83232", "Forge powerful weapons and blades"),
    ARMORSMITH("Platesmith", "#3278C8", "Craft protective plate armor and shields"),
    ALCHEMIST("Alchemist", "#8B5CF6", "Brew potions and magical elixirs"),
    COOK("Cook", "#E89040", "Prepare hearty meals and feasts"),
    LEATHERWORKER("Leatherworker", "#8B6914", "Tan hides into sturdy leather gear"),
    CARPENTER("Carpenter", "#B47832", "Build furniture and wooden tools"),
    TAILOR("Tailor", "#C864C8", "Weave cloth into fine garments"),
    ENCHANTER("Enchanter", "#6432C8", "Imbue items with arcane power"),
    BUILDER("Builder", "#4A9E5C", "Construct structures and fortifications");

    private final String defaultDisplayName;
    private final String defaultColorHex;
    private final String defaultDescription;

    private static volatile Map<String, SkillDefinition> definitions = Collections.emptyMap();

    Profession(String defaultDisplayName, String defaultColorHex, String defaultDescription) {
        this.defaultDisplayName = defaultDisplayName;
        this.defaultColorHex = defaultColorHex;
        this.defaultDescription = defaultDescription;
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

    public String getDescription() {
        SkillDefinition def = definitions.get(this.name());
        if (def != null && def.description() != null) return def.description();
        return defaultDescription;
    }

    public String getColorHex() {
        return defaultColorHex;
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

    /**
     * Returns true if this profession is enabled (present in loaded definitions).
     * Definitions are loaded with enabled=true filter from the database.
     */
    public boolean isEnabled() {
        return definitions.containsKey(this.name());
    }

    /**
     * Returns only professions that are enabled in the database.
     */
    public static List<Profession> getEnabledProfessions() {
        List<Profession> enabled = new ArrayList<>();
        for (Profession p : values()) {
            if (p.isEnabled()) {
                enabled.add(p);
            }
        }
        return enabled;
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
