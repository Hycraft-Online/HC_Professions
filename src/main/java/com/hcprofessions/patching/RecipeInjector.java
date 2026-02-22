package com.hcprofessions.patching;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Injects crafting recipes into base game items that ship without them.
 * Uses reflection to set recipeToGenerate on items at runtime,
 * avoiding the need to maintain full item JSON override files.
 *
 * Called once after LoadAssetEvent, BEFORE RecipeKnowledgePatcher.patchAll().
 */
public class RecipeInjector {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-RecipeInjector");

    /**
     * Inject all missing recipes.
     */
    public static void injectAll() {
        List<RecipeDef> recipes = buildAllRecipes();

        int injected = 0;
        int patched = 0;

        for (RecipeDef def : recipes) {
            Item item = Item.getAssetMap().getAsset(def.itemId);
            if (item == null) {
                LOGGER.at(Level.WARNING).log("Item not found in asset map: %s", def.itemId);
                continue;
            }

            try {
                Object existingRecipe = getFieldValue(item, "recipeToGenerate");

                CraftingRecipe recipe = createRecipe(def);
                setFieldValue(item, "recipeToGenerate", recipe);
                clearCachedPacket(item);

                if (existingRecipe == null) {
                    injected++;
                    LOGGER.at(Level.INFO).log("Injected recipe for %s (%d inputs, bench=%s T%d)",
                            def.itemId, def.inputs.length, def.benchId, def.benchTier);
                } else {
                    patched++;
                    LOGGER.at(Level.INFO).log("Replaced recipe for %s (%d inputs)",
                            def.itemId, def.inputs.length);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to inject recipe for %s: %s: %s",
                        def.itemId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        LOGGER.at(Level.INFO).log("Recipe injection complete: %d injected (new), %d replaced (empty/placeholder)",
                injected, patched);
    }

    private static CraftingRecipe createRecipe(RecipeDef def) {
        MaterialQuantity[] inputs = new MaterialQuantity[def.inputs.length];
        for (int i = 0; i < def.inputs.length; i++) {
            inputs[i] = new MaterialQuantity(def.inputs[i].itemId, null, null, def.inputs[i].quantity, null);
        }

        // Primary output is the item itself
        MaterialQuantity primaryOutput = new MaterialQuantity(def.itemId, null, null, 1, null);

        // Build bench requirements
        List<BenchRequirement> benchReqs = new ArrayList<>();
        benchReqs.add(new BenchRequirement(BenchType.Crafting, def.benchId,
                def.benchCategories, def.benchTier));
        if (def.armoryCategories != null) {
            benchReqs.add(new BenchRequirement(BenchType.DiagramCrafting, "Armory",
                    def.armoryCategories, 0));
        }

        return new CraftingRecipe(
                inputs,
                primaryOutput,
                new MaterialQuantity[]{primaryOutput},
                1,
                benchReqs.toArray(new BenchRequirement[0]),
                def.timeSeconds,
                false,  // knowledgeRequired - patched by RecipeKnowledgePatcher later
                1       // requiredMemoriesLevel
        );
    }

    // ═══════════════════════════════════════════════════════
    // ONYXIUM RECIPE DEFINITIONS
    // ═══════════════════════════════════════════════════════
    // Scaled up ~1.3-1.5x from Adamantite (the previous tier).
    // Adamantite: Bar_Adamantite + Leather_Heavy + Cindercloth, Bench T3, 4.5s
    // Onyxium:    Bar_Onyxium    + Leather_Heavy + Cindercloth, Bench T3, 5.0s

    private static List<RecipeDef> buildAllRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();

        // ── MITHRIL (3 missing weapons) ──
        // Existing Mithril: Bar_Mithril + Leather_Storm + Voidheart, Weapon_Bench T3, 5.0s
        // Sword=6bar/3leath/1void, Battleaxe=10/4/1, Mace=10/4/1, Daggers=10/3/1, Shortbow=6/2/1
        float mTime = 5.0f;

        recipes.add(weapon("Weapon_Longsword_Mithril", mTime, "Weapon_Longsword",
                mat("Ingredient_Bar_Mithril", 6), mat("Ingredient_Leather_Storm", 3), mat("Ingredient_Voidheart", 1)));

        recipes.add(weapon("Weapon_Axe_Mithril", mTime, "Weapon_Axe",
                mat("Ingredient_Bar_Mithril", 4), mat("Ingredient_Leather_Storm", 3), mat("Ingredient_Voidheart", 1)));

        recipes.add(weapon("Weapon_Club_Mithril", mTime, "Weapon_Club",
                mat("Ingredient_Bar_Mithril", 4), mat("Ingredient_Leather_Storm", 3), mat("Ingredient_Voidheart", 1)));

        // ── ONYXIUM (all 13 items) ──
        float time = 5.0f;

        // --- Weapons (Weapon_Bench T3) ---

        recipes.add(weapon("Weapon_Sword_Onyxium", time, "Weapon_Sword",
                mat("Ingredient_Bar_Onyxium", 15), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        recipes.add(weapon("Weapon_Longsword_Onyxium", time, "Weapon_Longsword",
                mat("Ingredient_Bar_Onyxium", 8), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        recipes.add(weapon("Weapon_Axe_Onyxium", time, "Weapon_Axe",
                mat("Ingredient_Bar_Onyxium", 6), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        recipes.add(weapon("Weapon_Battleaxe_Onyxium", time, "Weapon_Battleaxe",
                mat("Ingredient_Bar_Onyxium", 22), mat("Ingredient_Leather_Heavy", 6), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        // Mace - also requires Armory (DiagramCrafting)
        recipes.add(weaponWithArmory("Weapon_Mace_Onyxium", time, "Weapon_Mace", "Weapons.Mace",
                mat("Ingredient_Bar_Onyxium", 22), mat("Ingredient_Leather_Heavy", 6), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        // Club - also requires Armory (DiagramCrafting)
        recipes.add(weaponWithArmory("Weapon_Club_Onyxium", time, "Weapon_Club", "Weapons.Club",
                mat("Ingredient_Bar_Onyxium", 6), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        recipes.add(weapon("Weapon_Daggers_Onyxium", time, "Weapon_Daggers",
                mat("Ingredient_Bar_Onyxium", 12), mat("Ingredient_Leather_Heavy", 4), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        // Shortbow - also requires Armory (DiagramCrafting)
        recipes.add(weaponWithArmory("Weapon_Shortbow_Onyxium", time, "Weapon_Bow", "Weapons.Bow",
                mat("Ingredient_Bar_Onyxium", 15), mat("Ingredient_Leather_Heavy", 4), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        // --- Shield (Armor_Bench T3) ---

        recipes.add(armor("Weapon_Shield_Onyxium", time, "Weapon_Shield",
                mat("Ingredient_Bar_Onyxium", 11), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        // --- Plate Armor (Armor_Bench T3) ---

        recipes.add(armor("Armor_Onyxium_Head", time, "Armor_Head",
                mat("Ingredient_Bar_Onyxium", 20), mat("Ingredient_Leather_Heavy", 6), mat("Ingredient_Fabric_Scrap_Cindercloth", 5)));

        recipes.add(armor("Armor_Onyxium_Chest", time, "Armor_Chest",
                mat("Ingredient_Bar_Onyxium", 38), mat("Ingredient_Leather_Heavy", 10), mat("Ingredient_Fabric_Scrap_Cindercloth", 9)));

        recipes.add(armor("Armor_Onyxium_Hands", time, "Armor_Hands",
                mat("Ingredient_Bar_Onyxium", 16), mat("Ingredient_Leather_Heavy", 5), mat("Ingredient_Fabric_Scrap_Cindercloth", 4)));

        recipes.add(armor("Armor_Onyxium_Legs", time, "Armor_Legs",
                mat("Ingredient_Bar_Onyxium", 30), mat("Ingredient_Leather_Heavy", 9), mat("Ingredient_Fabric_Scrap_Cindercloth", 8)));

        return recipes;
    }

    // --- Helper builders ---

    private static RecipeDef weapon(String itemId, float time, String category, InputMat... inputs) {
        RecipeDef def = new RecipeDef();
        def.itemId = itemId;
        def.timeSeconds = time;
        def.benchId = "Weapon_Bench";
        def.benchCategories = new String[]{category};
        def.benchTier = 3;
        def.inputs = inputs;
        return def;
    }

    private static RecipeDef weaponWithArmory(String itemId, float time, String category, String armoryCategory, InputMat... inputs) {
        RecipeDef def = weapon(itemId, time, category, inputs);
        def.armoryCategories = new String[]{armoryCategory};
        return def;
    }

    private static RecipeDef armor(String itemId, float time, String category, InputMat... inputs) {
        RecipeDef def = new RecipeDef();
        def.itemId = itemId;
        def.timeSeconds = time;
        def.benchId = "Armor_Bench";
        def.benchCategories = new String[]{category};
        def.benchTier = 3;
        def.inputs = inputs;
        return def;
    }

    private static InputMat mat(String itemId, int quantity) {
        return new InputMat(itemId, quantity);
    }

    // --- Data structures ---

    private static class RecipeDef {
        String itemId;
        float timeSeconds;
        String benchId;
        String[] benchCategories;
        int benchTier;
        String[] armoryCategories; // null if no Armory requirement
        InputMat[] inputs;
    }

    private record InputMat(String itemId, int quantity) {}

    // --- Reflection utilities ---

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

    private static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException("Field '" + fieldName + "' not found on " + obj.getClass().getName());
        }
        field.setAccessible(true);
        field.set(obj, value);
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
