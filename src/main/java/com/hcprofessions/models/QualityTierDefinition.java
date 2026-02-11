package com.hcprofessions.models;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public record QualityTierDefinition(
    int id,
    String name,
    int minLevel,
    int maxLevel,
    String maxRarity,
    int minAffixes,
    int maxAffixes,
    double bonusAffixChance,
    int ilvlVariance,
    int sortOrder
) {
    public int rollAffixCount(Random random) {
        if (minAffixes == maxAffixes) return minAffixes;
        return random.nextDouble() < bonusAffixChance ? maxAffixes : minAffixes;
    }

    public int rollItemLevel(int profLevel, Random random) {
        if (ilvlVariance <= 0) return Math.max(1, profLevel);
        int delta = random.nextInt(ilvlVariance * 2 + 1) - ilvlVariance;
        return Math.max(1, profLevel + delta);
    }

    @Nullable
    public static QualityTierDefinition fromLevel(int professionLevel, List<QualityTierDefinition> tiers) {
        for (QualityTierDefinition tier : tiers) {
            if (professionLevel >= tier.minLevel && professionLevel <= tier.maxLevel) {
                return tier;
            }
        }
        return tiers.isEmpty() ? null : tiers.get(0);
    }
}
