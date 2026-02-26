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
        List<CraftingRecipe> recipesToRegister = new ArrayList<>();

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

                // Set recipe ID for asset store registration
                String recipeId = def.itemId + "_Recipe_Generated_0";
                setFieldValue(recipe, "id", recipeId);
                recipesToRegister.add(recipe);

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

        // Register all recipes with the asset store, triggering CraftingPlugin.onRecipeLoad()
        // which registers them with BenchRecipeRegistry for bench UI visibility
        if (!recipesToRegister.isEmpty()) {
            CraftingRecipe.getAssetStore().loadAssets("ModServer:HC_Professions", recipesToRegister);
            LOGGER.at(Level.INFO).log("Registered %d recipes with bench asset store", recipesToRegister.size());
        }

        LOGGER.at(Level.INFO).log("Recipe injection complete: %d injected (new), %d replaced (empty/placeholder)",
                injected, patched);
    }

    private static CraftingRecipe createRecipe(RecipeDef def) {
        MaterialQuantity[] inputs = new MaterialQuantity[def.inputs.length];
        for (int i = 0; i < def.inputs.length; i++) {
            inputs[i] = new MaterialQuantity(def.inputs[i].itemId(), def.inputs[i].resourceTypeId(), null, def.inputs[i].quantity(), null);
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
    // RECIPE DEFINITIONS
    // ═══════════════════════════════════════════════════════

    private static List<RecipeDef> buildAllRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();

        // ── CRUDE (Lv 1, most primitive weapons) ──
        // Very cheap: just wood sticks and fibre. Longsword, Shortbow, Arrow already have base game recipes.
        float crudeTime = 1.5f;

        recipes.add(weaponTier("Weapon_Sword_Crude", crudeTime, 1, "Weapon_Sword",
                res("Wood_Trunk", 2), mat("Ingredient_Fibre", 1)));

        recipes.add(weaponTier("Weapon_Axe_Crude", crudeTime, 1, "Weapon_Axe",
                res("Wood_Trunk", 2), mat("Ingredient_Fibre", 1)));

        recipes.add(weaponTier("Weapon_Battleaxe_Crude", crudeTime, 1, "Weapon_Battleaxe",
                res("Wood_Trunk", 3), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Mace_Crude", crudeTime, 1, "Weapon_Mace",
                res("Wood_Trunk", 2), res("Rock", 1)));

        recipes.add(weaponTier("Weapon_Club_Crude", crudeTime, 1, "Weapon_Club",
                res("Wood_Trunk", 2)));

        recipes.add(weaponTier("Weapon_Daggers_Crude", crudeTime, 1, "Weapon_Daggers",
                res("Wood_Trunk", 1), mat("Ingredient_Fibre", 1)));

        recipes.add(weaponTier("Weapon_Spear_Crude", crudeTime, 1, "Weapon_Spear",
                res("Wood_Trunk", 2), mat("Ingredient_Fibre", 1)));

        // ── TRIBAL (Lv 5, primitive wooden weapons) ──
        // Simple wood+fibre recipes. Override any base game recipes to ensure correct
        // bench registration and ingredients.
        float tribalTime = 2.0f;

        recipes.add(weaponTier("Weapon_Axe_Tribal", tribalTime, 1, "Weapon_Axe",
                res("Wood_Trunk", 3), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Club_Tribal", tribalTime, 1, "Weapon_Club",
                res("Wood_Trunk", 3), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Longsword_Tribal", tribalTime, 1, "Weapon_Longsword",
                res("Wood_Trunk", 4), mat("Ingredient_Fibre", 3)));

        // ── TRORK / STONE (Lv 8, stone-age weapons) ──
        // All 4 lack base game recipes. Rock + Wood_Trunk + Fibre.
        float trorkTime = 2.0f;

        recipes.add(weaponTier("Weapon_Battleaxe_Stone_Trork", trorkTime, 1, "Weapon_Battleaxe",
                res("Rock", 8), res("Wood_Trunk", 3), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Club_Stone_Trork", trorkTime, 1, "Weapon_Club",
                res("Rock", 4), res("Wood_Trunk", 2), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Daggers_Stone_Trork", trorkTime, 1, "Weapon_Daggers",
                res("Rock", 4), res("Wood_Trunk", 2), mat("Ingredient_Fibre", 2)));

        recipes.add(weaponTier("Weapon_Longsword_Stone_Trork", trorkTime, 1, "Weapon_Longsword",
                res("Rock", 6), res("Wood_Trunk", 3), mat("Ingredient_Fibre", 2)));

        // ── BRONZE (Lv 16, weapons without base game recipes) ──
        // Sword, Daggers, and Shortbow exist but have no recipes.
        float bronzeTime = 3.0f;

        recipes.add(weaponTier("Weapon_Sword_Bronze", bronzeTime, 2, "Weapon_Sword",
                mat("Ingredient_Bar_Bronze", 4), res("Wood_Trunk", 2), mat("Ingredient_Leather_Light", 2)));

        recipes.add(weaponTier("Weapon_Daggers_Bronze", bronzeTime, 2, "Weapon_Daggers",
                mat("Ingredient_Bar_Bronze", 4), res("Wood_Trunk", 2), mat("Ingredient_Leather_Light", 1)));

        recipes.add(weaponTier("Weapon_Shortbow_Bronze", bronzeTime, 2, "Weapon_Shortbow",
                mat("Ingredient_Bar_Bronze", 2), res("Wood_Trunk", 3), mat("Ingredient_Fibre", 3)));

        // ── IRON SPECIAL (Lv 20) ──
        // Kunai - small iron throwing dagger
        recipes.add(weaponTier("Weapon_Kunai", 3.0f, 2, "Weapon_Daggers",
                mat("Ingredient_Bar_Iron", 3), res("Wood_Trunk", 1), mat("Ingredient_Leather_Light", 1)));

        // ── STEEL (Lv 25, no Steel bar exists - uses Iron bars) ──
        float steelTime = 3.5f;

        recipes.add(weaponTier("Weapon_Sword_Steel", steelTime, 2, "Weapon_Sword",
                mat("Ingredient_Bar_Iron", 6), mat("Ingredient_Leather_Light", 3), mat("Ingredient_Fabric_Scrap_Linen", 2)));

        // Katana - refined two-handed blade
        recipes.add(weaponTier("Weapon_Longsword_Katana", steelTime, 2, "Weapon_Longsword",
                mat("Ingredient_Bar_Iron", 8), mat("Ingredient_Leather_Light", 3), mat("Ingredient_Fabric_Scrap_Linen", 2)));

        // ── SCARAB (Lv 44, unique faction weapon) ──
        // Battleaxe exists without a recipe. Longsword has a base game recipe.
        float scarabTime = 4.0f;

        recipes.add(weapon("Weapon_Battleaxe_Scarab", scarabTime, "Weapon_Battleaxe",
                mat("Ingredient_Bar_Cobalt", 12), mat("Ingredient_Leather_Scaled", 4), mat("Ingredient_Fabric_Scrap_Cindercloth", 3)));

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
        // Scaled up ~1.3-1.5x from Adamantite.
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
        recipes.add(weaponWithArmory("Weapon_Shortbow_Onyxium", time, "Weapon_Shortbow", "Weapons.Bow",
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

        // ── PRISMA (Lv 50, ~1.2x from Onyxium) ──
        // Only Mace and Armor exist as items. No Shield/Sword/Longsword/Axe/Battleaxe/Club/Daggers/Shortbow.
        float prismaTime = 5.0f;

        // --- Mace (Weapon_Bench T3) ---
        recipes.add(weaponWithArmory("Weapon_Mace_Prisma", prismaTime, "Weapon_Mace", "Weapons.Mace",
                mat("Ingredient_Bar_Prisma", 26), mat("Ingredient_Leather_Prismic", 7), mat("Ingredient_Fabric_Scrap_Prismaloom", 5)));

        // --- Plate Armor (Armor_Bench T3) ---
        recipes.add(armor("Armor_Prisma_Head", prismaTime, "Armor_Head",
                mat("Ingredient_Bar_Prisma", 24), mat("Ingredient_Leather_Prismic", 7), mat("Ingredient_Fabric_Scrap_Prismaloom", 6)));

        recipes.add(armor("Armor_Prisma_Chest", prismaTime, "Armor_Chest",
                mat("Ingredient_Bar_Prisma", 46), mat("Ingredient_Leather_Prismic", 12), mat("Ingredient_Fabric_Scrap_Prismaloom", 11)));

        recipes.add(armor("Armor_Prisma_Hands", prismaTime, "Armor_Hands",
                mat("Ingredient_Bar_Prisma", 19), mat("Ingredient_Leather_Prismic", 6), mat("Ingredient_Fabric_Scrap_Prismaloom", 5)));

        recipes.add(armor("Armor_Prisma_Legs", prismaTime, "Armor_Legs",
                mat("Ingredient_Bar_Prisma", 36), mat("Ingredient_Leather_Prismic", 11), mat("Ingredient_Fabric_Scrap_Prismaloom", 10)));

        return recipes;
    }

    // --- Helper builders ---

    private static RecipeDef weapon(String itemId, float time, String category, InputMat... inputs) {
        return weaponTier(itemId, time, 3, category, inputs);
    }

    private static RecipeDef weaponTier(String itemId, float time, int tier, String category, InputMat... inputs) {
        RecipeDef def = new RecipeDef();
        def.itemId = itemId;
        def.timeSeconds = time;
        def.benchId = "Weapon_Bench";
        def.benchCategories = new String[]{category};
        def.benchTier = tier;
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
        return new InputMat(itemId, null, quantity);
    }

    /** Resource type (block) ingredient like Rock, Wood_Trunk */
    private static InputMat res(String resourceTypeId, int quantity) {
        return new InputMat(null, resourceTypeId, quantity);
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

    private record InputMat(String itemId, String resourceTypeId, int quantity) {}

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
