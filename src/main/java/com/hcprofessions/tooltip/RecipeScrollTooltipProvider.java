package com.hcprofessions.tooltip;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.RecipeGate;
import org.herolias.tooltips.api.ItemVisualOverrides;
import org.herolias.tooltips.api.TooltipData;
import org.herolias.tooltips.api.TooltipProvider;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * DynamicTooltipsLib provider for recipe scroll items.
 * Provides custom name, description, and quality color for scroll items
 * whose IDs start with "Recipe_". This eliminates the need for .lang files.
 */
public class RecipeScrollTooltipProvider implements TooltipProvider {

    private static final String PROVIDER_ID = "hc_professions_scrolls";
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ScrollTooltip");
    private static final String SCROLL_PREFIX = "Recipe_";

    // Runtime-resolved quality indices from ItemQuality asset map
    private static final Map<String, Integer> QUALITY_INDEX_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean qualityIndicesResolved = false;

    @Nonnull
    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public int getPriority() {
        return 100; // DEFAULT
    }

    @Nullable
    @Override
    public TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata) {
        if (!itemId.startsWith(SCROLL_PREFIX)) {
            return null;
        }

        // Extract the target item ID from the scroll ID
        String targetItemId = itemId.substring(SCROLL_PREFIX.length());

        // Look up the recipe gate for this target item
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null) return null;

        CraftingGateManager gateManager = plugin.getCraftingGateManager();
        if (gateManager == null) return null;

        RecipeGate gate = gateManager.getGate(targetItemId);

        // Build display name
        String humanName = humanizeName(targetItemId);
        String displayName = "Recipe: " + humanName;

        // Build description lines
        String profName = "Unknown";
        String profColor = "#AAAAAA";
        int level = 0;
        String quality = "Common";

        if (gate != null && gate.enabled()) {
            Profession prof = gate.requiredProfession();
            profName = prof.getDisplayName();
            profColor = prof.getColorHex();
            level = gate.requiredLevel();
            quality = getQuality(level);
        }

        String description = "Teaches how to craft: " + humanName
                + "\n" + profName + " Level " + level
                + "\nRight-click to learn.";

        int qualityIndex = resolveQualityIndex(quality);

        return TooltipData.builder()
                .nameOverride(displayName)
                .descriptionOverride(description)
                .visualOverrides(ItemVisualOverrides.builder()
                        .qualityIndex(qualityIndex)
                        .build())
                .hashInput(itemId + "|" + level + "|" + profName)
                .build();
    }

    /**
     * Convert an item ID like "Weapon_Sword_Iron" to "Sword Iron".
     */
    private static String humanizeName(String itemId) {
        String[] prefixes = {
                "Consumable_", "Weapon_", "Armor_", "Food_", "Potion_",
                "Ingredient_", "Component_", "Furniture_", "Bench_",
        };
        String name = itemId;
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
                break;
            }
        }
        return name.replace("_", " ");
    }

    private static String getQuality(int level) {
        if (level >= 55) return "Epic";
        if (level >= 35) return "Rare";
        if (level >= 15) return "Uncommon";
        return "Common";
    }

    // ── Quality index resolution via reflection ──

    private static int resolveQualityIndex(String qualityId) {
        if (!qualityIndicesResolved) {
            initQualityIndices();
        }
        return QUALITY_INDEX_CACHE.getOrDefault(qualityId, 0);
    }

    private static synchronized void initQualityIndices() {
        if (qualityIndicesResolved) return;
        try {
            Class<?> itemQualityClass = Class.forName(
                    "com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality");
            Method getAssetMapMethod = itemQualityClass.getMethod("getAssetMap");
            Object assetMap = getAssetMapMethod.invoke(null);

            Method getIndexMethod = assetMap.getClass().getMethod("getIndexOrDefault", Object.class, int.class);

            for (String quality : List.of("Common", "Uncommon", "Rare", "Epic", "Legendary")) {
                int index = (int) getIndexMethod.invoke(assetMap, quality, 0);
                QUALITY_INDEX_CACHE.put(quality, index);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to resolve ItemQuality indices: %s", e.getMessage());
            QUALITY_INDEX_CACHE.put("Common", 0);
            QUALITY_INDEX_CACHE.put("Uncommon", 10);
            QUALITY_INDEX_CACHE.put("Rare", 6);
            QUALITY_INDEX_CACHE.put("Epic", 3);
            QUALITY_INDEX_CACHE.put("Legendary", 5);
        }
        qualityIndicesResolved = true;
    }
}
