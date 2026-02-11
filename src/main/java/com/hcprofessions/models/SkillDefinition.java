package com.hcprofessions.models;

import java.awt.Color;

public record SkillDefinition(
    String id,
    String type,
    String displayName,
    String colorHex,
    String description,
    boolean enabled,
    int sortOrder
) {
    public Color getColor() {
        try {
            return Color.decode(colorHex);
        } catch (NumberFormatException e) {
            return Color.GRAY;
        }
    }
}
