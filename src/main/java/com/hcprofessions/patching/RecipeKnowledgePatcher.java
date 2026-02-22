package com.hcprofessions.patching;

import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

/**
 * Patches KnowledgeRequired on item recipes at runtime via reflection.
 *
 * Items whose recipe gate level is >= SCROLL_LEVEL_THRESHOLD get KnowledgeRequired=true
 * (hidden from crafting bench until learned via scroll). Items below the threshold get
 * KnowledgeRequired=false (freely known).
 *
 * Two-pass approach:
 * 1. Patch item.recipeToGenerate (covers items with inline recipes)
 * 2. Scan CraftingRecipe asset store for standalone recipes producing gated items
 *    (covers items without recipeToGenerate, like ingredients and components)
 *
 * Called once after LoadAssetEvent when all item JSONs are loaded into memory.
 * This replaces the old approach of maintaining 128+ JSON override files.
 */
public class RecipeKnowledgePatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-KnowledgePatch");

    /** Recipes below this level are freely known (no scroll needed). */
    public static final int SCROLL_LEVEL_THRESHOLD = 10;

    /**
     * Patch KnowledgeRequired on all items that have recipe gates.
     */
    public static void patchAll(CraftingGateManager gateManager) {
        if (gateManager == null) {
            LOGGER.at(Level.WARNING).log("CraftingGateManager is null -- skipping KnowledgeRequired patching");
            return;
        }

        int patched = 0;
        int cleared = 0;
        int noRecipe = 0;
        int notFound = 0;
        int alreadyCorrect = 0;
        List<String> noRecipeItems = new ArrayList<>();
        List<String> notFoundItems = new ArrayList<>();

        for (RecipeGate gate : gateManager.getAllGates()) {
            if (!gate.enabled()) continue;

            String itemId = gate.recipeOutputId();
            boolean needsKnowledge = gate.requiredLevel() >= SCROLL_LEVEL_THRESHOLD;

            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                notFound++;
                notFoundItems.add(itemId);
                continue;
            }

            try {
                // Get the CraftingRecipe from the item
                Object recipe = getFieldValue(item, "recipeToGenerate");
                if (recipe == null) {
                    noRecipe++;
                    noRecipeItems.add(itemId + " (Lv." + gate.requiredLevel() + ")");
                    continue;
                }

                // Get current knowledgeRequired value
                Field krField = findField(recipe.getClass(), "knowledgeRequired");
                if (krField == null) {
                    LOGGER.at(Level.FINE).log("knowledgeRequired field not found on %s", recipe.getClass().getName());
                    continue;
                }
                krField.setAccessible(true);
                boolean current = krField.getBoolean(recipe);

                if (needsKnowledge && !current) {
                    krField.setBoolean(recipe, true);
                    clearCachedPacket(item);
                    patched++;
                } else if (!needsKnowledge && current) {
                    krField.setBoolean(recipe, false);
                    clearCachedPacket(item);
                    cleared++;
                } else {
                    alreadyCorrect++;
                }

            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to patch KnowledgeRequired on %s: %s: %s",
                        itemId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        LOGGER.at(Level.INFO).log("Pass 1 (item.recipeToGenerate): %d patched, %d cleared, %d already correct, %d no recipe, %d not in asset map",
                patched, cleared, alreadyCorrect, noRecipe, notFound);

        if (!noRecipeItems.isEmpty()) {
            LOGGER.at(Level.INFO).log("Items without recipeToGenerate: %s", String.join(", ", noRecipeItems));
        }
        if (!notFoundItems.isEmpty()) {
            LOGGER.at(Level.INFO).log("Items not in asset map: %s", String.join(", ", notFoundItems));
        }

        // Pass 2: Patch standalone CraftingRecipes in the asset store
        int pass2Patched = patchStandaloneRecipes(gateManager, noRecipeItems.isEmpty() ? Set.of() : null);

        LOGGER.at(Level.INFO).log("KnowledgeRequired patching complete (threshold: Lv.%d): Pass1=%d+%d, Pass2=%d, noRecipe=%d, notInMap=%d",
                SCROLL_LEVEL_THRESHOLD, patched, cleared, pass2Patched, noRecipe, notFound);
    }

    /**
     * Pass 2: Scan ALL CraftingRecipe assets and patch knowledgeRequired for any
     * recipe whose output matches a gated item at or above the threshold.
     *
     * This catches standalone recipe JSONs (e.g., for ingredients, components)
     * that aren't linked via item.recipeToGenerate.
     */
    private static int patchStandaloneRecipes(CraftingGateManager gateManager) {
        return patchStandaloneRecipes(gateManager, null);
    }

    private static int patchStandaloneRecipes(CraftingGateManager gateManager, Set<String> unused) {
        // Build a lookup: itemId -> whether it needs knowledge
        Map<String, Boolean> gateRequirements = new HashMap<>();
        for (RecipeGate gate : gateManager.getAllGates()) {
            if (!gate.enabled()) continue;
            gateRequirements.put(gate.recipeOutputId(), gate.requiredLevel() >= SCROLL_LEVEL_THRESHOLD);
        }

        int patched = 0;

        try {
            Map<String, CraftingRecipe> allRecipes = CraftingRecipe.getAssetMap().getAssetMap();

            for (Map.Entry<String, CraftingRecipe> entry : allRecipes.entrySet()) {
                CraftingRecipe recipe = entry.getValue();

                // Check recipe outputs for gated items
                String outputItemId = getRecipeOutputItemId(recipe);
                if (outputItemId == null) continue;

                Boolean needsKnowledge = gateRequirements.get(outputItemId);
                if (needsKnowledge == null) continue;

                try {
                    Field krField = findField(recipe.getClass(), "knowledgeRequired");
                    if (krField == null) continue;
                    krField.setAccessible(true);
                    boolean current = krField.getBoolean(recipe);

                    if (needsKnowledge && !current) {
                        krField.setBoolean(recipe, true);
                        patched++;
                        LOGGER.at(Level.INFO).log("Pass 2: Patched standalone recipe %s (output: %s) -> knowledgeRequired=true",
                                entry.getKey(), outputItemId);
                    } else if (!needsKnowledge && current) {
                        krField.setBoolean(recipe, false);
                        patched++;
                        LOGGER.at(Level.INFO).log("Pass 2: Cleared standalone recipe %s (output: %s) -> knowledgeRequired=false",
                                entry.getKey(), outputItemId);
                    }
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).log("Pass 2: Failed to patch recipe %s: %s",
                            entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Pass 2 failed: %s: %s", e.getClass().getSimpleName(), e.getMessage());
        }

        return patched;
    }

    /**
     * Get the primary output item ID from a CraftingRecipe.
     * Checks primaryOutput first, then falls back to outputs[0].
     */
    private static String getRecipeOutputItemId(CraftingRecipe recipe) {
        try {
            MaterialQuantity primary = recipe.getPrimaryOutput();
            if (primary != null && primary.getItemId() != null) {
                return primary.getItemId();
            }

            MaterialQuantity[] outputs = recipe.getOutputs();
            if (outputs != null && outputs.length > 0 && outputs[0].getItemId() != null) {
                return outputs[0].getItemId();
            }
        } catch (Exception e) {
            // Ignore - some recipes may have null outputs
        }
        return null;
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void clearCachedPacket(Item item) {
        try {
            Field field = findField(item.getClass(), "cachedPacket");
            if (field != null) {
                field.setAccessible(true);
                field.set(item, null);
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Could not clear cachedPacket: %s", e.toString());
        }
    }

    /**
     * Remove recipes for any gate entry with enabled=false.
     * This completely strips the recipe from all benches.
     */
    public static void removeDisabledRecipes(CraftingGateManager gateManager) {
        if (gateManager == null) return;

        Set<String> disabledOutputs = new HashSet<>();
        for (RecipeGate gate : gateManager.getAllGates()) {
            if (!gate.enabled()) {
                disabledOutputs.add(gate.recipeOutputId());
            }
        }

        if (disabledOutputs.isEmpty()) return;

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, CraftingRecipe> entry : CraftingRecipe.getAssetMap().getAssetMap().entrySet()) {
            String outputId = getRecipeOutputItemId(entry.getValue());
            if (outputId != null && disabledOutputs.contains(outputId)) {
                toRemove.add(entry.getKey());
            }
        }

        if (!toRemove.isEmpty()) {
            CraftingRecipe.getAssetStore().removeAssets(toRemove);
            LOGGER.at(Level.INFO).log("Removed %d recipes for disabled gates: %s",
                    toRemove.size(), String.join(", ", toRemove));
        }
    }

    /**
     * Patch BenchRequirement.id on item recipes that have placeholder "TODO" bench IDs.
     * Replaces "TODO" with the correct bench ID based on the item's armor slot category.
     * This fixes base game leather/cloth armor items that were shipped with unfinished bench references.
     */
    public static void patchTodoBenchIds(CraftingGateManager gateManager) {
        if (gateManager == null) return;

        int patched = 0;
        int alreadyCorrect = 0;
        // Scan ALL items in the asset map, not just gated ones — any item with "TODO" bench needs fixing
        Map<String, Item> allItems = Item.getAssetMap().getAssetMap();
        for (Map.Entry<String, Item> entry : allItems.entrySet()) {
            String itemId = entry.getKey();
            Item item = entry.getValue();

            try {
                Object recipe = getFieldValue(item, "recipeToGenerate");
                if (recipe == null) continue;

                // Cast to CraftingRecipe to access getBenchRequirement()
                if (!(recipe instanceof CraftingRecipe craftingRecipe)) continue;

                BenchRequirement[] reqs = craftingRecipe.getBenchRequirement();
                if (reqs == null) continue;

                boolean changed = false;
                for (BenchRequirement req : reqs) {
                    if ("TODO".equals(req.id)) {
                        // Determine correct bench from categories
                        String correctBench = inferBenchFromCategories(req.categories);
                        if (correctBench != null) {
                            req.id = correctBench;
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    clearCachedPacket(item);
                    patched++;
                } else {
                    alreadyCorrect++;
                }

            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to patch bench ID on %s: %s", itemId, e.getMessage());
            }
        }

        LOGGER.at(Level.INFO).log("Bench ID patching: %d items patched (TODO -> correct bench), %d already correct", patched, alreadyCorrect);
    }

    /**
     * Infer the correct bench ID from recipe categories.
     * Armor_Head/Chest/Hands/Legs/Shield -> Armor_Bench
     * Weapon categories -> Weapon_Bench
     */
    private static String inferBenchFromCategories(String[] categories) {
        if (categories == null) return null;
        for (String cat : categories) {
            if (cat.startsWith("Armor_") || cat.equals("Weapon_Shield")) return "Armor_Bench";
            if (cat.startsWith("Weapon_")) return "Weapon_Bench";
        }
        return null;
    }

    /**
     * Remove ALL recipes that belong to a specific bench.
     * Matches on BenchRequirement.id (e.g. "Arcanebench").
     */
    public static void removeRecipesForBench(String benchId) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, CraftingRecipe> entry : CraftingRecipe.getAssetMap().getAssetMap().entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            BenchRequirement[] reqs = recipe.getBenchRequirement();
            if (reqs == null) continue;
            for (BenchRequirement req : reqs) {
                if (benchId.equals(req.id)) {
                    toRemove.add(entry.getKey());
                    break;
                }
            }
        }

        if (!toRemove.isEmpty()) {
            CraftingRecipe.getAssetStore().removeAssets(toRemove);
            LOGGER.at(Level.INFO).log("Removed %d recipes for bench '%s': %s",
                    toRemove.size(), benchId, String.join(", ", toRemove));
        }
    }

    private static Field findField(Class<?> startClass, String fieldName) {
        for (Class<?> clazz = startClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                continue;
            }
        }
        return null;
    }
}
