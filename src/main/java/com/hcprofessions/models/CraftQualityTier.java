package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CraftQualityTier {

    private static volatile List<QualityTierDefinition> tiers = buildDefaults();

    private CraftQualityTier() {}

    public static void setTiers(List<QualityTierDefinition> newTiers) {
        tiers = newTiers.isEmpty() ? buildDefaults() : List.copyOf(newTiers);
    }

    public static List<QualityTierDefinition> getTiers() {
        return tiers;
    }

    @Nullable
    public static QualityTierDefinition fromLevel(int professionLevel) {
        return QualityTierDefinition.fromLevel(professionLevel, tiers);
    }

    public static boolean isGrandmaster(QualityTierDefinition tier) {
        return tier != null && tier.minLevel() == tier.maxLevel() && tier.maxLevel() >= 100;
    }

    private static List<QualityTierDefinition> buildDefaults() {
        List<QualityTierDefinition> defaults = new ArrayList<>();
        defaults.add(new QualityTierDefinition(0, "Novice",      1,   14,  "Common",    0, 0, 0.0,  0,  0));
        defaults.add(new QualityTierDefinition(0, "Apprentice",  15,  29,  "Uncommon",  0, 1, 0.10, 2,  1));
        defaults.add(new QualityTierDefinition(0, "Journeyman",  30,  49,  "Rare",      0, 1, 0.30, 3,  2));
        defaults.add(new QualityTierDefinition(0, "Expert",      50,  69,  "Rare",      1, 1, 1.00, 5,  3));
        defaults.add(new QualityTierDefinition(0, "Artisan",     70,  84,  "Epic",      1, 2, 0.25, 8,  4));
        defaults.add(new QualityTierDefinition(0, "Master",      85,  99,  "Epic",      1, 2, 0.50, 10, 5));
        defaults.add(new QualityTierDefinition(0, "Grandmaster", 100, 100, "Legendary", 2, 2, 0.05, 15, 6));
        return Collections.unmodifiableList(defaults);
    }
}
