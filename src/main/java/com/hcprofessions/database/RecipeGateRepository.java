package com.hcprofessions.database;

import com.hcprofessions.models.Profession;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class RecipeGateRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-RecipeGate");

    private final DatabaseManager databaseManager;

    public RecipeGateRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Map<String, RecipeGate> loadAll() {
        Map<String, RecipeGate> gates = new HashMap<>();

        String sql = """
            SELECT g.recipe_output_id, g.required_profession, g.required_level,
                   g.profession_xp_granted, g.enabled, g.ingredients, g.time_seconds,
                   g.learn_cost, g.bench_category
            FROM prof_recipe_gates g
            LEFT JOIN items_registry ir ON g.recipe_output_id = ir.item_id
            WHERE (ir.item_id IS NULL OR ir.enabled != false)
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String outputId = rs.getString("recipe_output_id");
                Profession prof = Profession.fromString(rs.getString("required_profession"));
                if (prof != null) {
                    String ingredientsJson = rs.getString("ingredients");
                    if (ingredientsJson == null) ingredientsJson = "[]";
                    gates.put(outputId.toLowerCase(), new RecipeGate(
                        outputId,
                        prof,
                        rs.getInt("required_level"),
                        rs.getInt("profession_xp_granted"),
                        rs.getBoolean("enabled"),
                        ingredientsJson,
                        rs.getInt("time_seconds"),
                        rs.getInt("learn_cost"),
                        rs.getString("bench_category")
                    ));
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load recipe gates: " + e.getMessage());
        }

        LOGGER.at(Level.INFO).log("Loaded " + gates.size() + " recipe gates");
        return gates;
    }

    /**
     * Returns all enabled recipe gates for a specific profession, sorted by required level.
     */
    public java.util.List<RecipeGate> loadByProfession(Profession profession) {
        java.util.List<RecipeGate> gates = new java.util.ArrayList<>();
        String sql = """
            SELECT g.recipe_output_id, g.required_profession, g.required_level,
                   g.profession_xp_granted, g.enabled, g.ingredients, g.time_seconds,
                   g.learn_cost, g.bench_category
            FROM prof_recipe_gates g
            LEFT JOIN items_registry ir ON g.recipe_output_id = ir.item_id
            WHERE g.enabled = true AND g.required_profession = ?
                  AND (ir.item_id IS NULL OR ir.enabled != false)
            ORDER BY g.required_level ASC, g.recipe_output_id ASC
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, profession.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ingredientsJson = rs.getString("ingredients");
                    if (ingredientsJson == null) ingredientsJson = "[]";
                    gates.add(new RecipeGate(
                        rs.getString("recipe_output_id"),
                        profession,
                        rs.getInt("required_level"),
                        rs.getInt("profession_xp_granted"),
                        rs.getBoolean("enabled"),
                        ingredientsJson,
                        rs.getInt("time_seconds"),
                        rs.getInt("learn_cost"),
                        rs.getString("bench_category")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load recipe gates for " + profession + ": " + e.getMessage());
        }

        return gates;
    }

    /**
     * Returns all enabled recipe output IDs for auto-teaching to players.
     */
    public java.util.List<String> loadAllRecipeOutputIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String sql = "SELECT recipe_output_id FROM prof_recipe_gates WHERE enabled = true";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString("recipe_output_id"));
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load recipe output IDs: " + e.getMessage());
        }

        return ids;
    }

    public void seedDefaults() {
        try (Connection conn = databaseManager.getConnection()) {
            int before = 0;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                if (rs.next()) before = rs.getInt(1);
            }

            String insertSql = """
                INSERT INTO prof_recipe_gates (recipe_output_id, required_profession, required_level, profession_xp_granted)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (recipe_output_id) DO NOTHING
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                // Weaponsmith: Swords
                insertGate(stmt, "Weapon_Sword_Copper",         "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Sword_Iron",           "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Sword_Thorium",        "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Sword_Cobalt",         "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Sword_Mithril",        "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Sword_Adamantite",     "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Sword_Onyxium",       "WEAPONSMITH", 85, 125);

                // Weaponsmith: Longswords
                insertGate(stmt, "Weapon_Longsword_Crude",      "WEAPONSMITH", 1,  5);
                insertGate(stmt, "Weapon_Longsword_Copper",     "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Longsword_Iron",       "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Longsword_Thorium",    "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Longsword_Cobalt",     "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Longsword_Scarab",     "WEAPONSMITH", 50, 65);
                insertGate(stmt, "Weapon_Longsword_Adamantite", "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Longsword_Onyxium",   "WEAPONSMITH", 85, 125);

                // Weaponsmith: Axes
                insertGate(stmt, "Weapon_Axe_Copper",           "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Axe_Iron",             "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Axe_Thorium",          "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Axe_Cobalt",           "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Axe_Adamantite",       "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Axe_Onyxium",         "WEAPONSMITH", 85, 125);

                // Weaponsmith: Battleaxes
                insertGate(stmt, "Weapon_Battleaxe_Copper",     "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Battleaxe_Iron",       "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Battleaxe_Thorium",    "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Battleaxe_Cobalt",     "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Battleaxe_Mithril",    "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Battleaxe_Adamantite", "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Battleaxe_Onyxium",   "WEAPONSMITH", 85, 125);

                // Weaponsmith: Maces
                insertGate(stmt, "Weapon_Mace_Copper",          "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Mace_Iron",            "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Mace_Thorium",         "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Mace_Cobalt",          "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Mace_Mithril",         "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Mace_Adamantite",      "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Mace_Onyxium",        "WEAPONSMITH", 85, 125);

                // Weaponsmith: Clubs
                insertGate(stmt, "Weapon_Club_Copper",          "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Club_Iron",            "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Club_Thorium",         "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Club_Cobalt",          "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Club_Adamantite",      "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Club_Onyxium",        "WEAPONSMITH", 85, 125);

                // Weaponsmith: Whirlwind Weapons (named variants)
                insertGate(stmt, "Weapon_Battleaxe_Whirlwind",  "WEAPONSMITH", 30, 55);
                insertGate(stmt, "Weapon_Mace_Whirlwind",       "WEAPONSMITH", 30, 55);

                // Weaponsmith: Daggers
                insertGate(stmt, "Weapon_Daggers_Copper",       "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Daggers_Iron",         "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Daggers_Thorium",      "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Daggers_Cobalt",       "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Daggers_Mithril",      "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Daggers_Adamantite",   "WEAPONSMITH", 70, 100);
                insertGate(stmt, "Weapon_Daggers_Onyxium",     "WEAPONSMITH", 85, 125);

                // Carpenter: Shortbows (ranged weapons crafted from wood)
                insertGate(stmt, "Weapon_Shortbow_Crude",       "CARPENTER", 1,  5);
                insertGate(stmt, "Weapon_Shortbow_Copper",      "CARPENTER", 1,  10);
                insertGate(stmt, "Weapon_Shortbow_Iron",        "CARPENTER", 10, 20);
                insertGate(stmt, "Weapon_Shortbow_Thorium",     "CARPENTER", 25, 35);
                insertGate(stmt, "Weapon_Shortbow_Cobalt",      "CARPENTER", 40, 55);
                insertGate(stmt, "Weapon_Shortbow_Mithril",     "CARPENTER", 55, 75);
                insertGate(stmt, "Weapon_Shortbow_Adamantite",  "CARPENTER", 70, 100);
                insertGate(stmt, "Weapon_Shortbow_Onyxium",    "CARPENTER", 85, 125);

                // Carpenter: Crossbow & Arrow (ranged weapons crafted from wood)
                insertGate(stmt, "Weapon_Crossbow_Iron",        "CARPENTER", 10, 20);
                insertGate(stmt, "Weapon_Arrow_Crude",           "CARPENTER", 1,  5);

                // Armorsmith: Copper
                insertGate(stmt, "Armor_Copper_Head",           "ARMORSMITH", 1,  10);
                insertGate(stmt, "Armor_Copper_Chest",          "ARMORSMITH", 1,  10);
                insertGate(stmt, "Armor_Copper_Hands",          "ARMORSMITH", 1,  10);
                insertGate(stmt, "Armor_Copper_Legs",           "ARMORSMITH", 1,  10);

                // Armorsmith: Iron
                insertGate(stmt, "Armor_Iron_Head",             "ARMORSMITH", 10, 20);
                insertGate(stmt, "Armor_Iron_Chest",            "ARMORSMITH", 10, 20);
                insertGate(stmt, "Armor_Iron_Hands",            "ARMORSMITH", 10, 20);
                insertGate(stmt, "Armor_Iron_Legs",             "ARMORSMITH", 10, 20);

                // Armorsmith: Thorium
                insertGate(stmt, "Armor_Thorium_Head",          "ARMORSMITH", 25, 35);
                insertGate(stmt, "Armor_Thorium_Chest",         "ARMORSMITH", 25, 35);
                insertGate(stmt, "Armor_Thorium_Hands",         "ARMORSMITH", 25, 35);
                insertGate(stmt, "Armor_Thorium_Legs",          "ARMORSMITH", 25, 35);

                // Armorsmith: Cobalt
                insertGate(stmt, "Armor_Cobalt_Head",           "ARMORSMITH", 40, 55);
                insertGate(stmt, "Armor_Cobalt_Chest",          "ARMORSMITH", 40, 55);
                insertGate(stmt, "Armor_Cobalt_Hands",          "ARMORSMITH", 40, 55);
                insertGate(stmt, "Armor_Cobalt_Legs",           "ARMORSMITH", 40, 55);

                // Armorsmith: Mithril
                insertGate(stmt, "Armor_Mithril_Head",          "ARMORSMITH", 55, 75);
                insertGate(stmt, "Armor_Mithril_Chest",         "ARMORSMITH", 55, 75);
                insertGate(stmt, "Armor_Mithril_Hands",         "ARMORSMITH", 55, 75);
                insertGate(stmt, "Armor_Mithril_Legs",          "ARMORSMITH", 55, 75);

                // Armorsmith: Adamantite
                insertGate(stmt, "Armor_Adamantite_Head",       "ARMORSMITH", 70, 100);
                insertGate(stmt, "Armor_Adamantite_Chest",      "ARMORSMITH", 70, 100);
                insertGate(stmt, "Armor_Adamantite_Hands",      "ARMORSMITH", 70, 100);
                insertGate(stmt, "Armor_Adamantite_Legs",       "ARMORSMITH", 70, 100);

                // Armorsmith: Onyxium
                insertGate(stmt, "Armor_Onyxium_Head",          "ARMORSMITH", 85, 125);
                insertGate(stmt, "Armor_Onyxium_Chest",         "ARMORSMITH", 85, 125);
                insertGate(stmt, "Armor_Onyxium_Hands",         "ARMORSMITH", 85, 125);
                insertGate(stmt, "Armor_Onyxium_Legs",          "ARMORSMITH", 85, 125);

                // Leatherworker: Leather Armor
                insertGate(stmt, "Armor_Leather_Soft_Head",     "LEATHERWORKER", 1,  10);
                insertGate(stmt, "Armor_Leather_Soft_Chest",    "LEATHERWORKER", 1,  10);
                insertGate(stmt, "Armor_Leather_Soft_Hands",    "LEATHERWORKER", 1,  10);
                insertGate(stmt, "Armor_Leather_Soft_Legs",     "LEATHERWORKER", 1,  10);
                insertGate(stmt, "Armor_Leather_Light_Head",    "LEATHERWORKER", 5,  15);
                insertGate(stmt, "Armor_Leather_Light_Chest",   "LEATHERWORKER", 5,  15);
                insertGate(stmt, "Armor_Leather_Light_Hands",   "LEATHERWORKER", 5,  15);
                insertGate(stmt, "Armor_Leather_Light_Legs",    "LEATHERWORKER", 5,  15);
                insertGate(stmt, "Armor_Leather_Medium_Head",   "LEATHERWORKER", 10, 20);
                insertGate(stmt, "Armor_Leather_Medium_Chest",  "LEATHERWORKER", 10, 20);
                insertGate(stmt, "Armor_Leather_Medium_Hands",  "LEATHERWORKER", 10, 20);
                insertGate(stmt, "Armor_Leather_Medium_Legs",   "LEATHERWORKER", 10, 20);
                insertGate(stmt, "Armor_Leather_Heavy_Head",    "LEATHERWORKER", 20, 30);
                insertGate(stmt, "Armor_Leather_Heavy_Chest",   "LEATHERWORKER", 20, 30);
                insertGate(stmt, "Armor_Leather_Heavy_Hands",   "LEATHERWORKER", 20, 30);
                insertGate(stmt, "Armor_Leather_Heavy_Legs",    "LEATHERWORKER", 20, 30);
                insertGate(stmt, "Armor_Leather_Bandits_Head",  "LEATHERWORKER", 30, 45);
                insertGate(stmt, "Armor_Leather_Bandits_Chest", "LEATHERWORKER", 30, 45);
                insertGate(stmt, "Armor_Leather_Bandits_Hands", "LEATHERWORKER", 30, 45);
                insertGate(stmt, "Armor_Leather_Bandits_Legs",  "LEATHERWORKER", 30, 45);
                insertGate(stmt, "Armor_Leather_Scout_Head",    "LEATHERWORKER", 40, 60);
                insertGate(stmt, "Armor_Leather_Scout_Chest",   "LEATHERWORKER", 40, 60);
                insertGate(stmt, "Armor_Leather_Scout_Hands",   "LEATHERWORKER", 40, 60);
                insertGate(stmt, "Armor_Leather_Scout_Legs",    "LEATHERWORKER", 40, 60);

                // Tailor: Cloth Armor
                insertGate(stmt, "Armor_Cloth_Linen_Head",      "TAILOR", 1,  10);
                insertGate(stmt, "Armor_Cloth_Linen_Chest",     "TAILOR", 1,  10);
                insertGate(stmt, "Armor_Cloth_Linen_Hands",     "TAILOR", 1,  10);
                insertGate(stmt, "Armor_Cloth_Linen_Legs",      "TAILOR", 1,  10);
                insertGate(stmt, "Armor_Cloth_Cotton_Head",     "TAILOR", 5,  15);
                insertGate(stmt, "Armor_Cloth_Cotton_Chest",    "TAILOR", 5,  15);
                insertGate(stmt, "Armor_Cloth_Cotton_Hands",    "TAILOR", 5,  15);
                insertGate(stmt, "Armor_Cloth_Cotton_Legs",     "TAILOR", 5,  15);
                insertGate(stmt, "Armor_Cloth_Wool_Head",       "TAILOR", 10, 20);
                insertGate(stmt, "Armor_Cloth_Wool_Chest",      "TAILOR", 10, 20);
                insertGate(stmt, "Armor_Cloth_Wool_Hands",      "TAILOR", 10, 20);
                insertGate(stmt, "Armor_Cloth_Wool_Legs",       "TAILOR", 10, 20);
                insertGate(stmt, "Armor_Cloth_Silk_Head",       "TAILOR", 15, 30);
                insertGate(stmt, "Armor_Cloth_Silk_Chest",      "TAILOR", 15, 30);
                insertGate(stmt, "Armor_Cloth_Silk_Hands",      "TAILOR", 15, 30);
                insertGate(stmt, "Armor_Cloth_Silk_Legs",       "TAILOR", 15, 30);
                insertGate(stmt, "Armor_Cloth_Cindercloth_Head",  "TAILOR", 18, 40);
                insertGate(stmt, "Armor_Cloth_Cindercloth_Chest", "TAILOR", 18, 40);
                insertGate(stmt, "Armor_Cloth_Cindercloth_Hands", "TAILOR", 18, 40);
                insertGate(stmt, "Armor_Cloth_Cindercloth_Legs",  "TAILOR", 18, 40);
                insertGate(stmt, "Armor_Cloth_Shadoweave_Head",   "TAILOR", 20, 55);
                insertGate(stmt, "Armor_Cloth_Shadoweave_Chest",  "TAILOR", 20, 55);
                insertGate(stmt, "Armor_Cloth_Shadoweave_Hands",  "TAILOR", 20, 55);
                insertGate(stmt, "Armor_Cloth_Shadoweave_Legs",   "TAILOR", 20, 55);

                // Armorsmith: Shields
                insertGate(stmt, "Weapon_Shield_Copper",        "ARMORSMITH", 1,  10);
                insertGate(stmt, "Weapon_Shield_Iron",          "ARMORSMITH", 10, 20);
                insertGate(stmt, "Weapon_Shield_Thorium",       "ARMORSMITH", 25, 35);
                insertGate(stmt, "Weapon_Shield_Cobalt",        "ARMORSMITH", 40, 55);
                insertGate(stmt, "Weapon_Shield_Mithril",       "ARMORSMITH", 55, 75);
                insertGate(stmt, "Weapon_Shield_Adamantite",    "ARMORSMITH", 70, 100);
                insertGate(stmt, "Weapon_Shield_Onyxium",      "ARMORSMITH", 85, 125);

                // ═══════════════════════════════════════════════════════
                // ALCHEMIST — Bench_Alchemy (potions and bombs)
                // ═══════════════════════════════════════════════════════
                // Tier 1: Basic remedies
                insertGate(stmt, "Potion_Antidote",             "ALCHEMIST", 1,  10);
                insertGate(stmt, "Potion_Health_Lesser",        "ALCHEMIST", 1,  15);
                // Tier 2: Stamina/Signature
                insertGate(stmt, "Potion_Stamina_Lesser",       "ALCHEMIST", 10, 20);
                insertGate(stmt, "Potion_Signature_Lesser",     "ALCHEMIST", 15, 25);
                // Tier 3: Bombs
                insertGate(stmt, "Weapon_Bomb_Popberry",        "ALCHEMIST", 20, 30);
                // Tier 4: Greater potions
                insertGate(stmt, "Potion_Health_Greater",       "ALCHEMIST", 30, 40);
                insertGate(stmt, "Potion_Stamina_Greater",      "ALCHEMIST", 40, 50);
                insertGate(stmt, "Potion_Signature_Greater",    "ALCHEMIST", 50, 65);
                // Tier 5: Morph potions (endgame)
                insertGate(stmt, "Potion_Morph_Dog",            "ALCHEMIST", 60, 80);
                insertGate(stmt, "Potion_Morph_Frog",           "ALCHEMIST", 60, 80);
                insertGate(stmt, "Potion_Morph_Mouse",          "ALCHEMIST", 65, 85);
                insertGate(stmt, "Potion_Morph_Pigeon",         "ALCHEMIST", 65, 85);

                // ═══════════════════════════════════════════════════════
                // COOK — Bench_Cooking (food and meals)
                // ═══════════════════════════════════════════════════════
                // Tier 1: Simple kebabs (IL 1-4)
                insertGate(stmt, "Food_Kebab_Mushroom",         "COOK", 1,  8);
                insertGate(stmt, "Food_Kebab_Fruit",            "COOK", 1,  8);
                insertGate(stmt, "Food_Kebab_Meat",             "COOK", 1,  10);
                insertGate(stmt, "Food_Kebab_Vegetable",        "COOK", 5,  12);
                // Tier 2: Salads and basic baking (IL 5-7)
                insertGate(stmt, "Food_Salad_Mushroom",         "COOK", 10, 15);
                insertGate(stmt, "Food_Salad_Berry",            "COOK", 10, 15);
                insertGate(stmt, "Food_Bread",                  "COOK", 15, 20);
                // Tier 3: Processed foods (IL 8-10)
                insertGate(stmt, "Food_Cheese",                 "COOK", 20, 25);
                insertGate(stmt, "Food_Popcorn",                "COOK", 20, 25);
                insertGate(stmt, "Food_Salad_Caesar",           "COOK", 25, 30);
                // Tier 4: Pies (IL 11-13, require Knowledge)
                insertGate(stmt, "Food_Pie_Apple",              "COOK", 35, 40);
                insertGate(stmt, "Food_Pie_Meat",               "COOK", 45, 55);
                insertGate(stmt, "Food_Pie_Pumpkin",            "COOK", 55, 65);

                // ═══════════════════════════════════════════════════════
                // LEATHERWORKER — Bench_Tannery (leather processing)
                // ═══════════════════════════════════════════════════════
                // Progression follows hide rarity: soft → light → medium → heavy → scaled → storm → dark → prismic
                insertGate(stmt, "Ingredient_Leather_Soft",     "LEATHERWORKER", 1,  10);
                insertGate(stmt, "Ingredient_Leather_Light",    "LEATHERWORKER", 1,  15);
                insertGate(stmt, "Ingredient_Leather_Medium",   "LEATHERWORKER", 10, 20);
                insertGate(stmt, "Ingredient_Leather_Heavy",    "LEATHERWORKER", 20, 30);
                insertGate(stmt, "Ingredient_Leather_Scaled",   "LEATHERWORKER", 30, 40);
                insertGate(stmt, "Ingredient_Leather_Storm",    "LEATHERWORKER", 40, 55);
                insertGate(stmt, "Ingredient_Leather_Dark",     "LEATHERWORKER", 55, 70);
                insertGate(stmt, "Ingredient_Leather_Prismic",  "LEATHERWORKER", 70, 90);

                // ═══════════════════════════════════════════════════════
                // TAILOR — Bench_Loom (cloth weaving)
                // ═══════════════════════════════════════════════════════
                // Progression follows fabric rarity: linen → cotton → silk → shadoweave → stormsilk → cindercloth → prismaloom
                insertGate(stmt, "Ingredient_Bolt_Linen",       "TAILOR", 1,  10);
                insertGate(stmt, "Ingredient_Bolt_Cotton",      "TAILOR", 10, 20);
                insertGate(stmt, "Ingredient_Bolt_Silk",        "TAILOR", 25, 35);
                insertGate(stmt, "Ingredient_Bolt_Shadoweave",  "TAILOR", 35, 45);
                insertGate(stmt, "Ingredient_Bolt_Stormsilk",   "TAILOR", 45, 55);
                insertGate(stmt, "Ingredient_Bolt_Cindercloth", "TAILOR", 55, 70);
                insertGate(stmt, "Ingredient_Bolt_Prismaloom",  "TAILOR", 70, 90);

                // ═══════════════════════════════════════════════════════
                // ENCHANTER — Bench_Arcane (arcane items and portals)
                // ═══════════════════════════════════════════════════════
                // Tier 1: Basic utility
                insertGate(stmt, "Teleporter",                           "ENCHANTER", 1,  20);
                // Tier 2: Deployable totems
                insertGate(stmt, "Weapon_Deployable_Healing_Totem",      "ENCHANTER", 20, 35);
                insertGate(stmt, "Weapon_Deployable_Slowness_Totem",     "ENCHANTER", 25, 35);
                // Tier 3: Crystal staves
                insertGate(stmt, "Weapon_Staff_Crystal_Flame",           "ENCHANTER", 40, 55);
                insertGate(stmt, "Weapon_Staff_Crystal_Ice",             "ENCHANTER", 40, 55);
                // Tier 4: Portal devices (endgame)
                insertGate(stmt, "Portal_Device",                        "ENCHANTER", 55, 70);
                insertGate(stmt, "PortalKey_Hederas_Lair",               "ENCHANTER", 60, 80);
                insertGate(stmt, "PortalKey_Taiga",                      "ENCHANTER", 60, 80);
                insertGate(stmt, "PortalKey_Windsurf_Valley",            "ENCHANTER", 60, 80);

                // ═══════════════════════════════════════════════════════
                // CARPENTER — Bench_Furniture (furniture crafting)
                // ═══════════════════════════════════════════════════════
                // Tier 1: Kweebec set (IL 1, simple wood + fibre)
                insertGate(stmt, "Furniture_Kweebec_Bed",            "CARPENTER", 1,  10);
                insertGate(stmt, "Furniture_Kweebec_Chest_Small",    "CARPENTER", 1,  10);
                insertGate(stmt, "Furniture_Kweebec_Candle",         "CARPENTER", 1,  8);
                insertGate(stmt, "Furniture_Kweebec_Lantern",        "CARPENTER", 1,  8);
                // Tier 2: Tavern set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Tavern_Bed",             "CARPENTER", 1, 15);
                insertGate(stmt, "Furniture_Tavern_Chest_Small",     "CARPENTER", 1, 15);
                insertGate(stmt, "Furniture_Tavern_Chest_Large",     "CARPENTER", 1, 20);
                insertGate(stmt, "Furniture_Tavern_Candle",          "CARPENTER", 1, 12);
                insertGate(stmt, "Furniture_Tavern_Chandelier",      "CARPENTER", 1, 15);
                // Tier 3: Ancient set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Ancient_Bed",            "CARPENTER", 1, 25);
                insertGate(stmt, "Furniture_Ancient_Chest_Small",    "CARPENTER", 1, 25);
                insertGate(stmt, "Furniture_Ancient_Chest_Large",    "CARPENTER", 1, 30);
                insertGate(stmt, "Furniture_Ancient_Candle",         "CARPENTER", 1, 20);
                // Tier 4: Lumberjack set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Lumberjack_Bed",         "CARPENTER", 1, 35);
                insertGate(stmt, "Furniture_Lumberjack_Chest_Small", "CARPENTER", 1, 35);
                insertGate(stmt, "Furniture_Lumberjack_Chest_Large", "CARPENTER", 1, 40);
                insertGate(stmt, "Furniture_Lumberjack_Lamp",        "CARPENTER", 1, 30);
                insertGate(stmt, "Furniture_Lumberjack_Lantern",     "CARPENTER", 1, 30);
                // Tier 5: Feran set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Feran_Bed",              "CARPENTER", 1, 45);
                insertGate(stmt, "Furniture_Feran_Chest_Small",      "CARPENTER", 1, 45);
                insertGate(stmt, "Furniture_Feran_Chest_Large",      "CARPENTER", 1, 50);
                insertGate(stmt, "Furniture_Feran_Chandelier",       "CARPENTER", 1, 40);
                insertGate(stmt, "Furniture_Feran_Candle",           "CARPENTER", 1, 40);
                insertGate(stmt, "Furniture_Feran_Torch",            "CARPENTER", 1, 35);
                insertGate(stmt, "Furniture_Feran_Torch_Tall",       "CARPENTER", 1, 40);
                // Tier 6: Temple Dark set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Temple_Dark_Bed",        "CARPENTER", 1, 55);
                insertGate(stmt, "Furniture_Temple_Dark_Chest_Small","CARPENTER", 1, 55);
                insertGate(stmt, "Furniture_Temple_Dark_Chest_Large","CARPENTER", 1, 60);
                insertGate(stmt, "Furniture_Temple_Dark_Brazier",    "CARPENTER", 1, 50);
                // Tier 7: Jungle set — freely craftable, grants XP
                insertGate(stmt, "Furniture_Jungle_Bed",             "CARPENTER", 1, 70);
                insertGate(stmt, "Furniture_Jungle_Chest_Small",     "CARPENTER", 1, 70);
                insertGate(stmt, "Furniture_Jungle_Chest_Large",     "CARPENTER", 1, 75);
                insertGate(stmt, "Furniture_Jungle_Candle",          "CARPENTER", 1, 60);
                insertGate(stmt, "Furniture_Jungle_Torch",           "CARPENTER", 1, 60);

                // ═══════════════════════════════════════════════════════
                // Profession bench crafting (at Workbench)
                // ═══════════════════════════════════════════════════════
                insertGate(stmt, "Bench_Weaponsmith_Forge",     "WEAPONSMITH", 1, 0);
                insertGate(stmt, "Bench_Armorsmith_Anvil",      "ARMORSMITH",  1, 0);

                int after = 0;
                try (Statement s2 = conn.createStatement();
                     ResultSet rs2 = s2.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                    if (rs2.next()) after = rs2.getInt(1);
                }
                int inserted = after - before;
                LOGGER.at(Level.INFO).log("Seeded recipe gate defaults: %d new (total %d)", inserted, after);
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed recipe gate defaults: " + e.getMessage());
        }
    }

    /**
     * Seeds cross-profession component and consumable recipe gates.
     * Uses ON CONFLICT DO NOTHING so it safely merges with existing data.
     */
    public void seedComponentGates() {
        String insertSql = """
            INSERT INTO prof_recipe_gates (recipe_output_id, required_profession, required_level, profession_xp_granted, ingredients, time_seconds)
            VALUES (?, ?, ?, ?, '[]'::jsonb, 0)
            ON CONFLICT (recipe_output_id) DO NOTHING
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            int before = 0;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                if (rs.next()) before = rs.getInt(1);
            }


            // =======================================================
            // WEAPONSMITH -- Components
            // =======================================================
            insertGate(stmt, "Component_Crude_Blade_Blank",             "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Filing",                  "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Handle_Wrap",             "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Crossguard",              "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Iron_Nails",                    "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Wire_Coil",                     "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Latch",                   "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Raw_Metal_Scrap",               "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Flux",                    "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Etching",                 "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Raw_Tang_Insert",               "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Component_Blade_Blank",                   "WEAPONSMITH",  10,  12);
            insertGate(stmt, "Component_Basic_Blade_Form",              "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Edge_Strip",              "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Grip_Cord",               "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Pommel_Cap",              "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Pins",                    "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Chain_Links",             "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Simple_Gear_Set",               "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Crude_Alloy_Bar",               "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Raw_Quenching_Oil",             "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Inlay_Strip",             "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Crude_Ricasso_Piece",           "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Component_Steel_Blade_Blank",             "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Edge_Filing",              "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Leather_Grip_Wrap",             "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Crossguard",               "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Metal_Brackets",                "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Wire_Coil",                "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Spring_Mechanism",              "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Alloy_Binding",                 "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Flux",                 "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Filigree_Band",            "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Fuller_Strip",             "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Component_Folded_Steel_Blank",            "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Steel_Edge_Piece",              "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Steel_Tang_Insert",             "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Counterweight",                 "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Standard_Bolts",                "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Steel_Chain_Links",             "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Trigger_Assembly",         "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Alloy_Blend",              "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Tempering_Compound",       "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Steel_Inlay_Ring",              "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Standard_Quillon_Bar",          "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Component_Cobalt_Blade_Blank",            "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Edge_Strip",             "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Reinforced_Handle",             "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Guard_Ring",             "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Rivets",                 "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Chain",                  "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Mechanism",              "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Alloy_Bar",              "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Flux",                   "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Inlay",                  "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Tang",                   "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Component_Reinforced_Blade_Plate",        "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Filing",               "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Grip_Wrap",            "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Quillon",            "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Fasteners",          "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Mithril_Wire_Strand",           "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Gears",              "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Alloy_Blend",          "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Quench_Oil",           "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Etching_Panel",        "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Ricasso",            "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Component_Thorium_Blade_Core",            "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Serrated_Filing",               "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Master_Handle_Core",            "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Crossguard",            "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Pins",                  "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Chain_Links",           "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Mechanism",             "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Alloy_Bar",             "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Master_Flux",                   "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Filigree",              "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Master_Fuller",                 "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Component_Ancient_Blade_Relic",           "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Edge_Grind",             "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Grip_Wrap",             "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Masterwork_Pommel",             "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Rivets",                "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Wire_Coil",             "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Trigger",                "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Metal_Fusion",          "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Tempering_Essence",     "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Engraving",              "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Tang_Insert",           "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Component_Starforged_Edge",               "WEAPONSMITH",  76,  68);
            insertGate(stmt, "Component_Starforged_Rivet",              "WEAPONSMITH",  76,  68);
            insertGate(stmt, "Component_Starforged_Blade_Blank",        "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Handle_Core",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Guard_Plate",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Chain",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Mechanism",                "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Alloy",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Flux",                     "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Inlay_Strip",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Ricasso",            "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Razor_Strip",              "WEAPONSMITH",  90,  80);
            insertGate(stmt, "Component_Void_Bolts",                    "WEAPONSMITH",  90,  80);
            insertGate(stmt, "Component_Divine_Blade_Essence",          "WEAPONSMITH",  91,  80);
            insertGate(stmt, "Component_Cosmic_Edge_Grind",             "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Tang_Insert",         "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Godforged_Pommel",              "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Fasteners",           "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Wire_Mesh",              "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Gears",               "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Alloy_Core",             "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Forge_Compound",      "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Filigree_Band",          "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Quillon",                "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Component_Godforged_Edge_Core",           "WEAPONSMITH", 100,  88);

            // Weaponsmith -- Consumables
            insertGate(stmt, "Consumable_Roughing_Stone",               "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Weapon_Oil",             "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Repair_Kit",             "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Polish",                 "WEAPONSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Whetstone",              "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Consumable_Basic_Blade_Grease",           "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Consumable_Basic_Mending_Tools",          "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Consumable_Raw_Finish_Wax",               "WEAPONSMITH",  19,  19);
            insertGate(stmt, "Consumable_Honing_Paste",                 "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Consumable_Fine_Weapon_Oil",              "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Consumable_Standard_Repair_Kit",          "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Consumable_Fine_Polish",                  "WEAPONSMITH",  20,  20);
            insertGate(stmt, "Consumable_Fine_Sharpening_Stone",        "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Consumable_Standard_Blade_Coat",          "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Consumable_Fine_Tool_Set",                "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Consumable_Standard_Lacquer_Coat",        "WEAPONSMITH",  35,  33);
            insertGate(stmt, "Consumable_Whetstone_Kit",                "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Consumable_Superior_Weapon_Oil",          "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Consumable_Superior_Repair_Kit",          "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Consumable_Strop_Compound",               "WEAPONSMITH",  36,  34);
            insertGate(stmt, "Consumable_Superior_Hone",                "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Consumable_Cobalt_Blade_Polish",          "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Mending_Kit",       "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Consumable_Superior_Polish",              "WEAPONSMITH",  55,  50);
            insertGate(stmt, "Consumable_Master_Sharpening_Set",        "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Consumable_Enchanted_Weapon_Oil",         "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Consumable_Master_Repair_Kit",            "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Consumable_Master_Finish_Wax",            "WEAPONSMITH",  56,  51);
            insertGate(stmt, "Consumable_Razor_Edge_Kit",               "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Consumable_Master_Blade_Essence",         "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Tool_Set",             "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Lacquer",              "WEAPONSMITH",  75,  67);
            insertGate(stmt, "Consumable_Vorpal_Whetstone",             "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Consumable_Starforged_Weapon_Oil",        "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Consumable_Void_Repair_Kit",              "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Consumable_Worldsplitter_Wax",            "WEAPONSMITH",  83,  74);
            insertGate(stmt, "Consumable_Cosmic_Sharpening_Crystal",    "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Consumable_Divine_Weapon_Anointment",     "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Restoration_Kit",    "WEAPONSMITH",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Polish",                "WEAPONSMITH",  95,  84);

            // =======================================================
            // ARMORSMITH -- Components
            // =======================================================
            insertGate(stmt, "Component_Crude_Plate_Piece",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Chain_Piece",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Scale_Piece",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Buckles",                       "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Hinge_Pin",               "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Frame_Piece",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Metal_Band",              "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Shield_Boss",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Brace_Bar",               "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Emblem_Plate",            "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Crude_Visor_Plate",             "ARMORSMITH",   1,   4);
            insertGate(stmt, "Component_Rough_Iron_Plate",              "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Raw_Chain_Rings",               "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Iron_Scales",             "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Crude_Clasps",                  "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Raw_Joint_Bracket",             "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Form_Bar",                "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Iron_Strip",              "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Shield_Rim",              "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Raw_Support_Strut",             "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Crest_Piece",             "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Rough_Cheek_Guard",             "ARMORSMITH",  19,  19);
            insertGate(stmt, "Component_Steel_Plate_Panel",             "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Steel_Chainmail_Ring",          "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Iron_Studs",                    "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Buckles",              "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Hinge",                    "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Rivet_Plates",                  "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Metal_Band",               "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Shield_Boss",          "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Brace",                "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Fine_Emblem",                   "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Visor",                "ARMORSMITH",  20,  20);
            insertGate(stmt, "Component_Standard_Plate_Sheet",          "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Chain_Links",              "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Standard_Scale_Strip",          "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Clasps",                   "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Articulated_Joint",             "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Standard_Frame_Bar",            "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Steel_Strip",                   "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Shield_Frame",             "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Fine_Reinforcement_Bar",        "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Gorget_Ring",                   "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Visor_Hinge",                   "ARMORSMITH",  35,  33);
            insertGate(stmt, "Component_Cobalt_Plate",                  "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Chainmail",              "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Scale_Plate",            "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Buckles",                "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Joint_Assembly",         "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Frame_Piece",            "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Band",                   "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Shield_Core",            "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Brace",                  "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Crest",                  "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Cobalt_Visor_Plate",            "ARMORSMITH",  36,  34);
            insertGate(stmt, "Component_Reinforced_Steel_Plate",        "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Chain_Rings",          "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Tempered_Scale",                "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Clasps",             "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Hinge",              "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Form_Bar",           "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Reinforced_Metal_Strip",        "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Shield_Rim",           "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Support_Strut",        "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Emblem_Plate",         "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Superior_Cheek_Guard",          "ARMORSMITH",  55,  50);
            insertGate(stmt, "Component_Thorium_Plate_Core",            "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Chain_Rings",           "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Scale_Strip",           "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Clasps",                "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Joint_Pin",             "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Frame_Core",            "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Band",                  "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Shield_Boss",           "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Reinforcement",         "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Filigree_Inlay",                "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Thorium_Visor_Plate",           "ARMORSMITH",  56,  51);
            insertGate(stmt, "Component_Ancient_Plate_Panel",           "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Chainmail",             "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Scale_Plate",           "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Buckles",               "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Hinge_Set",              "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Form_Piece",             "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Metal_Strip",           "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Ancient_Shield_Frame",          "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Brace_Bar",              "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Crest_Piece",            "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Master_Cheek_Guard",            "ARMORSMITH",  75,  67);
            insertGate(stmt, "Component_Starforged_Plate",              "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Chain_Links",              "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Scales",             "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Clasps",                   "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Joint",              "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Frame_Bar",                "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Starforged_Band",               "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Shield_Core",              "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Brace",                    "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Emblem",                   "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Void_Visor",                    "ARMORSMITH",  83,  74);
            insertGate(stmt, "Component_Divine_Plate_Essence",          "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Chainmail_Weave",        "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Godforged_Scale_Piece",         "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Buckles",             "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Joint_Assembly",         "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Divine_Frame_Core",             "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Strip",                  "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Divine_Shield_Boss",            "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Celestial_Support_Beam",        "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Cosmic_Crest",                  "ARMORSMITH",  95,  84);
            insertGate(stmt, "Component_Divine_Crown_Piece",            "ARMORSMITH",  95,  84);

            // Armorsmith -- Consumables
            insertGate(stmt, "Consumable_Crude_Armor_Sealant",          "ARMORSMITH",   1,   4);
            insertGate(stmt, "Consumable_Patch_Kit",                    "ARMORSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Spike_Kit",              "ARMORSMITH",   1,   4);
            insertGate(stmt, "Consumable_Crude_Armor_Lining",           "ARMORSMITH",   1,   4);
            insertGate(stmt, "Consumable_Rust_Ward",                    "ARMORSMITH",  19,  19);
            insertGate(stmt, "Consumable_Crude_Armor_Mending_Kit",      "ARMORSMITH",  19,  19);
            insertGate(stmt, "Consumable_Rough_Armor_Thorns",           "ARMORSMITH",  19,  19);
            insertGate(stmt, "Consumable_Rough_Armor_Padding",          "ARMORSMITH",  19,  19);
            insertGate(stmt, "Consumable_Standard_Armor_Polish",        "ARMORSMITH",  20,  20);
            insertGate(stmt, "Consumable_Standard_Armor_Repair_Kit",    "ARMORSMITH",  20,  20);
            insertGate(stmt, "Consumable_Thorncoat_Resin",              "ARMORSMITH",  20,  20);
            insertGate(stmt, "Consumable_Standard_Armor_Padding",       "ARMORSMITH",  20,  20);
            insertGate(stmt, "Consumable_Fine_Armor_Coating",           "ARMORSMITH",  35,  33);
            insertGate(stmt, "Consumable_Padding_Insert",               "ARMORSMITH",  35,  33);
            insertGate(stmt, "Consumable_Fine_Spike_Set",               "ARMORSMITH",  35,  33);
            insertGate(stmt, "Consumable_Fine_Armor_Lining",            "ARMORSMITH",  35,  33);
            insertGate(stmt, "Consumable_Cobalt_Armor_Finish",          "ARMORSMITH",  36,  34);
            insertGate(stmt, "Consumable_Superior_Armor_Repair_Kit",    "ARMORSMITH",  36,  34);
            insertGate(stmt, "Consumable_Cobalt_Spike_Kit",             "ARMORSMITH",  36,  34);
            insertGate(stmt, "Consumable_Cobalt_Armor_Insert",          "ARMORSMITH",  36,  34);
            insertGate(stmt, "Consumable_Superior_Armor_Sealant",       "ARMORSMITH",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Mending_Set",       "ARMORSMITH",  55,  50);
            insertGate(stmt, "Consumable_Reflective_Polish",            "ARMORSMITH",  55,  50);
            insertGate(stmt, "Consumable_Superior_Armor_Padding",       "ARMORSMITH",  55,  50);
            insertGate(stmt, "Consumable_Master_Armor_Finish",          "ARMORSMITH",  56,  51);
            insertGate(stmt, "Consumable_Master_Armor_Repair_Kit",      "ARMORSMITH",  56,  51);
            insertGate(stmt, "Consumable_Master_Spike_Set",             "ARMORSMITH",  56,  51);
            insertGate(stmt, "Consumable_Master_Armor_Insert",          "ARMORSMITH",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Armor_Coating",        "ARMORSMITH",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Mending_Set",          "ARMORSMITH",  75,  67);
            insertGate(stmt, "Consumable_Dragonscale_Glaze",            "ARMORSMITH",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Armor_Lining",         "ARMORSMITH",  75,  67);
            insertGate(stmt, "Consumable_Void_Armor_Finish",            "ARMORSMITH",  83,  74);
            insertGate(stmt, "Consumable_Void_Armor_Repair_Kit",        "ARMORSMITH",  83,  74);
            insertGate(stmt, "Consumable_Void_Spike_Kit",               "ARMORSMITH",  83,  74);
            insertGate(stmt, "Consumable_Voidward_Sealant",             "ARMORSMITH",  83,  74);
            insertGate(stmt, "Consumable_Cosmic_Armor_Sealant",         "ARMORSMITH",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Armor_Restoration",  "ARMORSMITH",  95,  84);
            insertGate(stmt, "Consumable_Divine_Modification_Kit",      "ARMORSMITH",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Armor_Lining",          "ARMORSMITH",  95,  84);

            // =======================================================
            // LEATHERWORKER -- Components
            // =======================================================
            insertGate(stmt, "Component_Raw_Hide_Scrap",                "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Rawhide_Cord",                  "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Raw_Leather_Scrap",             "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Armor_Leather",           "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Raw_Reptile_Skin",              "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Tooled_Strip",            "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Leather_Strap",           "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Raw_Scale_Patch",               "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Bone_Needle",             "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Hide_Oil",                "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Leather_Dye",             "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Component_Crude_Tanned_Hide",             "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Sinew_Thread",                  "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Leather_Scraps",                "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Raw_Boiled_Leather",            "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Crude_Exotic_Scrap",            "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Rough_Embossed_Piece",          "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Raw_Binding_Strip",             "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Crude_Webbing_Strip",           "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Raw_Thread_Spool",              "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Raw_Leather_Conditioner",       "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Raw_Dye_Bath",                  "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Component_Cured_Hide_Panel",              "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Waxed_Lacing",                  "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Leather_Patch",        "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Hardened_Patches",              "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Fine_Reptile_Hide",             "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Tooled_Strips",                 "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Leather_Strap",        "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Scale_Webbing",        "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Sewing_Kit",           "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Leather_Oil",          "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Leather_Dye",          "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Component_Standard_Tanned_Leather",       "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Sinew_Cord",               "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Leather_Scrap",            "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Standard_Armor_Hide",           "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Standard_Exotic_Leather",       "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Embossed_Panel",           "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Binding_Cord",             "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Scaled_Webbing",                "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Bone_Needle",              "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Conditioner_Paste",        "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Fine_Dye_Solution",             "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Component_Cobalt_Cured_Hide",             "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Cord",                   "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Leather_Scrap",          "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Armor_Leather",          "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Exotic_Hide",            "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Tooled_Strip",           "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Binding",                "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Scale_Webbing",          "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Needle_Set",             "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Leather_Oil",            "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Leather_Dye",            "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Component_Reinforced_Tanned_Panel",       "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Sinew_Thread",       "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Leather_Patch",      "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Boiled_Hide",        "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Superior_Reptile_Leather",      "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Superior_Embossed_Piece",       "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Strap",              "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Superior_Scale_Patch",          "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Thread_Spool",       "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Superior_Conditioner",          "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Superior_Dye_Bath",             "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Component_Thorium_Treated_Hide",          "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Lacing",                "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Leather_Scrap",         "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Armor_Leather",         "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Exotic_Hide",           "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Master_Tooled_Strip",           "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Binding",               "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Thorium_Scale_Webbing",         "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Master_Sewing_Kit",             "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Master_Leather_Oil",            "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Master_Leather_Dye",            "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Component_Ancient_Cured_Leather",         "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Sinew_Cord",            "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Leather_Patch",         "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Boiled_Leather",        "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Reptile_Skin",          "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Embossed_Panel",        "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Leather_Strap",         "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Scale_Patch",           "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Bone_Needle",           "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Conditioner",           "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Ancient_Dye_Solution",          "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Component_Starforged_Hide_Panel",         "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Starforged_Cord",               "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Leather_Scrap",            "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Starforged_Armor_Hide",         "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Starforged_Exotic_Hide",        "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Tooled_Strip",             "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Starforged_Binding",            "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Scale_Webbing",            "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Needle",                   "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Leather_Oil",              "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Void_Leather_Dye",              "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Component_Divine_Hide_Essence",           "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Thread",                 "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Celestial_Leather_Patch",       "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Divine_Armor_Leather",          "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Celestial_Exotic_Leather",      "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Embossed_Panel",         "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Celestial_Strap",               "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Scale_Mesh",             "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Celestial_Thread_Spool",        "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Conditioner",            "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Component_Celestial_Dye_Bath",            "LEATHERWORKER",  95,  84);

            // Leatherworker -- Consumables
            insertGate(stmt, "Consumable_Mink_Oil",                     "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Wire_Snare",             "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Armor_Patch_Kit",        "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Camo_Paste",             "LEATHERWORKER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Leather_Balm",           "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Consumable_Simple_Net_Trap",              "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Consumable_Basic_Leather_Repair",         "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Consumable_Raw_Foliage_Wrap",             "LEATHERWORKER",  19,  19);
            insertGate(stmt, "Consumable_Dubbing_Wax",                  "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Consumable_Snare_Trap",                   "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Armor_Kit",           "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Camo_Kit",            "LEATHERWORKER",  20,  20);
            insertGate(stmt, "Consumable_Fine_Leather_Polish",          "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Wire_Trap",               "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Consumable_Studded_Overlay",              "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Foliage_Wrap",            "LEATHERWORKER",  35,  33);
            insertGate(stmt, "Consumable_Superior_Leather_Balm",        "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Consumable_Spike_Trap",                   "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Consumable_Superior_Armor_Kit",           "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Consumable_Superior_Camo_Kit",            "LEATHERWORKER",  36,  34);
            insertGate(stmt, "Consumable_Cobalt_Leather_Wax",           "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Consumable_Superior_Net_Trap",            "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Leather_Kit",       "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Foliage_Wrap",      "LEATHERWORKER",  55,  50);
            insertGate(stmt, "Consumable_Master_Leather_Care_Kit",      "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Consumable_Bear_Trap",                    "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Consumable_Master_Armor_Kit",             "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Consumable_Camouflage_Kit",               "LEATHERWORKER",  56,  51);
            insertGate(stmt, "Consumable_Dragonskin_Strip",             "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Consumable_Master_Jaw_Trap",              "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Leather_Kit",          "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Consumable_Master_Camo_Set",              "LEATHERWORKER",  75,  67);
            insertGate(stmt, "Consumable_Void_Leather_Polish",          "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Consumable_Void_Trap",                    "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Consumable_Void_Armor_Kit",               "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Consumable_Void_Camo_Kit",                "LEATHERWORKER",  83,  74);
            insertGate(stmt, "Consumable_Celestial_Leather_Balm",       "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Snare",              "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Consumable_Primal_Binding",               "LEATHERWORKER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Camo_Kit",           "LEATHERWORKER",  95,  84);

            // =======================================================
            // TAILOR -- Components
            // =======================================================
            insertGate(stmt, "Component_Crude_Thread_Spool",            "TAILOR",   1,   4);
            insertGate(stmt, "Component_Cotton_Batting",                "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Cloth_Scrap",             "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Fabric_Pin",              "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Padding_Piece",           "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Trim_Strip",              "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Spellcloth",              "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Bead_Strand",             "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Dyed_Cloth",              "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Reinforced_Cloth",        "TAILOR",   1,   4);
            insertGate(stmt, "Component_Crude_Rune_Stitch",             "TAILOR",   1,   4);
            insertGate(stmt, "Component_Raw_Cord_Bundle",               "TAILOR",  19,  19);
            insertGate(stmt, "Component_Hemmed_Scraps",                 "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Fabric_Piece",              "TAILOR",  19,  19);
            insertGate(stmt, "Component_Bone_Buttons",                  "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Cotton_Pad",                "TAILOR",  19,  19);
            insertGate(stmt, "Component_Rough_Ribbon",                  "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Magic_Weave",               "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Ornament_Piece",            "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Colored_Fabric",            "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Stiffened_Fabric",          "TAILOR",  19,  19);
            insertGate(stmt, "Component_Raw_Glyph_Thread",              "TAILOR",  19,  19);
            insertGate(stmt, "Component_Silk_Cord",                     "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Fabric_Bolt",          "TAILOR",  20,  20);
            insertGate(stmt, "Component_Dyed_Patch",                    "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Fabric_Clasp",         "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Batting",              "TAILOR",  20,  20);
            insertGate(stmt, "Component_Embroidered_Trim",              "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Spellcloth",           "TAILOR",  20,  20);
            insertGate(stmt, "Component_Fine_Bead_Strand",              "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Dyed_Fabric",          "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Reinforced_Cloth",     "TAILOR",  20,  20);
            insertGate(stmt, "Component_Standard_Rune_Stitch",          "TAILOR",  20,  20);
            insertGate(stmt, "Component_Fine_Thread_Spool",             "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Cotton_Bolt",              "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Cloth_Scrap",              "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Fabric_Pin",               "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Padding_Panel",            "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Ribbon",                   "TAILOR",  35,  33);
            insertGate(stmt, "Component_Silver_Clasp",                  "TAILOR",  35,  33);
            insertGate(stmt, "Component_Standard_Ornament",             "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Colored_Cloth",            "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Stiffened_Panel",          "TAILOR",  35,  33);
            insertGate(stmt, "Component_Fine_Glyph_Thread",             "TAILOR",  35,  33);
            insertGate(stmt, "Component_Cobalt_Thread",                 "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Fabric_Bolt",            "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Cloth_Patch",            "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Fabric_Clasp",           "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Padding",                "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Trim_Strip",             "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Spellcloth",             "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Beads",                  "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Dyed_Fabric",            "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Reinforced_Fabric",      "TAILOR",  36,  34);
            insertGate(stmt, "Component_Cobalt_Rune_Embroidery",        "TAILOR",  36,  34);
            insertGate(stmt, "Component_Reinforced_Cord_Bundle",        "TAILOR",  55,  50);
            insertGate(stmt, "Component_Reinforced_Cloth_Panel",        "TAILOR",  55,  50);
            insertGate(stmt, "Component_Reinforced_Fabric_Scrap",       "TAILOR",  55,  50);
            insertGate(stmt, "Component_Reinforced_Buttons",            "TAILOR",  55,  50);
            insertGate(stmt, "Component_Reinforced_Batting_Panel",      "TAILOR",  55,  50);
            insertGate(stmt, "Component_Superior_Ribbon",               "TAILOR",  55,  50);
            insertGate(stmt, "Component_Superior_Magic_Weave",          "TAILOR",  55,  50);
            insertGate(stmt, "Component_Starlight_Beads",               "TAILOR",  55,  50);
            insertGate(stmt, "Component_Superior_Colored_Cloth",        "TAILOR",  55,  50);
            insertGate(stmt, "Component_Superior_Stiffened_Cloth",      "TAILOR",  55,  50);
            insertGate(stmt, "Component_Superior_Glyph_Stitch",         "TAILOR",  55,  50);
            insertGate(stmt, "Component_Thorium_Thread",                "TAILOR",  56,  51);
            insertGate(stmt, "Component_Thorium_Fabric_Bolt",           "TAILOR",  56,  51);
            insertGate(stmt, "Component_Thorium_Cloth_Patch",           "TAILOR",  56,  51);
            insertGate(stmt, "Component_Thorium_Buttons",               "TAILOR",  56,  51);
            insertGate(stmt, "Component_Thorium_Padding",               "TAILOR",  56,  51);
            insertGate(stmt, "Component_Master_Trim",                   "TAILOR",  56,  51);
            insertGate(stmt, "Component_Thorium_Spellcloth",            "TAILOR",  56,  51);
            insertGate(stmt, "Component_Master_Bead_Strand",            "TAILOR",  56,  51);
            insertGate(stmt, "Component_Master_Dyed_Fabric",            "TAILOR",  56,  51);
            insertGate(stmt, "Component_Moonweave_Thread",              "TAILOR",  56,  51);
            insertGate(stmt, "Component_Master_Rune_Embroidery",        "TAILOR",  56,  51);
            insertGate(stmt, "Component_Ancient_Cord_Spool",            "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Cloth_Panel",           "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Fabric_Scrap",          "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Fabric_Pin",            "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Batting",               "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Ribbon",                "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Magic_Weave",           "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Ornament",              "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Colored_Cloth",         "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Reinforced_Cloth",      "TAILOR",  75,  67);
            insertGate(stmt, "Component_Ancient_Glyph_Thread",          "TAILOR",  75,  67);
            insertGate(stmt, "Component_Void_Thread_Spool",             "TAILOR",  83,  74);
            insertGate(stmt, "Component_Starforged_Fabric",             "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Cloth_Patch",              "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Fabric_Clasp",             "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Padding",                  "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Trim_Strip",               "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Spellcloth",               "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Beads",                    "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Dyed_Fabric",              "TAILOR",  83,  74);
            insertGate(stmt, "Component_Voidsilk_Sash",                 "TAILOR",  83,  74);
            insertGate(stmt, "Component_Void_Embroidery",               "TAILOR",  83,  74);
            insertGate(stmt, "Component_Cosmic_Cord",                   "TAILOR",  95,  84);
            insertGate(stmt, "Component_Divine_Fabric_Bolt",            "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Fabric_Scrap",        "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Buttons",             "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Batting",             "TAILOR",  95,  84);
            insertGate(stmt, "Component_Cosmic_Ribbon",                 "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Magic_Weave",         "TAILOR",  95,  84);
            insertGate(stmt, "Component_Cosmic_Ornament",               "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Colored_Fabric",      "TAILOR",  95,  84);
            insertGate(stmt, "Component_Cosmic_Thread",                 "TAILOR",  95,  84);
            insertGate(stmt, "Component_Celestial_Glyph_Stitch",        "TAILOR",  95,  84);

            // Tailor -- Consumables
            insertGate(stmt, "Consumable_Mending_Patch",                "TAILOR",   1,   4);
            insertGate(stmt, "Consumable_Crude_Warmth_Wrap",            "TAILOR",   1,   4);
            insertGate(stmt, "Consumable_Crude_Battle_Banner",          "TAILOR",   1,   4);
            insertGate(stmt, "Consumable_Crude_Garment_Lining",         "TAILOR",   1,   4);
            insertGate(stmt, "Consumable_Crude_Sewing_Kit",             "TAILOR",  19,  19);
            insertGate(stmt, "Consumable_Warmth_Lining",                "TAILOR",  19,  19);
            insertGate(stmt, "Consumable_Raw_Standard",                 "TAILOR",  19,  19);
            insertGate(stmt, "Consumable_Raw_Inner_Cloth",              "TAILOR",  19,  19);
            insertGate(stmt, "Consumable_Standard_Mending_Kit",         "TAILOR",  20,  20);
            insertGate(stmt, "Consumable_Standard_Buff_Wrap",           "TAILOR",  20,  20);
            insertGate(stmt, "Consumable_Enchanted_Banner",             "TAILOR",  20,  20);
            insertGate(stmt, "Consumable_Standard_Garment_Lining",      "TAILOR",  20,  20);
            insertGate(stmt, "Consumable_Fine_Sewing_Kit",              "TAILOR",  35,  33);
            insertGate(stmt, "Consumable_Fine_Warmth_Wrap",             "TAILOR",  35,  33);
            insertGate(stmt, "Consumable_Fine_Battle_Standard",         "TAILOR",  35,  33);
            insertGate(stmt, "Consumable_Fine_Inner_Cloth",             "TAILOR",  35,  33);
            insertGate(stmt, "Consumable_Superior_Mending_Kit",         "TAILOR",  36,  34);
            insertGate(stmt, "Consumable_Spellthread_Wrap",             "TAILOR",  36,  34);
            insertGate(stmt, "Consumable_Cobalt_Battle_Banner",         "TAILOR",  36,  34);
            insertGate(stmt, "Consumable_Superior_Garment_Lining",      "TAILOR",  36,  34);
            insertGate(stmt, "Consumable_Reinforced_Sewing_Kit",        "TAILOR",  55,  50);
            insertGate(stmt, "Consumable_Superior_Buff_Wrap",           "TAILOR",  55,  50);
            insertGate(stmt, "Consumable_Superior_Standard",            "TAILOR",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Inner_Cloth",       "TAILOR",  55,  50);
            insertGate(stmt, "Consumable_Master_Mending_Kit",           "TAILOR",  56,  51);
            insertGate(stmt, "Consumable_Master_Buff_Wrap",             "TAILOR",  56,  51);
            insertGate(stmt, "Consumable_Master_Battle_Banner",         "TAILOR",  56,  51);
            insertGate(stmt, "Consumable_Phoenix_Stitch",               "TAILOR",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Sewing_Kit",           "TAILOR",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Warmth_Wrap",          "TAILOR",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Standard",             "TAILOR",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Garment_Lining",       "TAILOR",  75,  67);
            insertGate(stmt, "Consumable_Void_Mending_Kit",             "TAILOR",  83,  74);
            insertGate(stmt, "Consumable_Void_Buff_Wrap",               "TAILOR",  83,  74);
            insertGate(stmt, "Consumable_Void_Banner",                  "TAILOR",  83,  74);
            insertGate(stmt, "Consumable_Astral_Mantle",                "TAILOR",  83,  74);
            insertGate(stmt, "Consumable_Celestial_Mending_Kit",        "TAILOR",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Warmth_Wrap",        "TAILOR",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Standard",           "TAILOR",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Garment_Lining",     "TAILOR",  95,  84);

            // =======================================================
            // ALCHEMIST -- Components
            // =======================================================
            insertGate(stmt, "Component_Purifying_Salt",                "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Solvent",                 "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Catalyst_Powder",         "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Ite_Binding",                   "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Dye_Red",                       "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Herbal_Extract",          "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Primer_Coat",             "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Processing_Flux",         "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Reagent_Powder",          "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Preservative",            "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Crude_Poultice",                "ALCHEMIST",   1,   4);
            insertGate(stmt, "Component_Dye_Blue",                      "ALCHEMIST",  10,  12);
            insertGate(stmt, "Component_Crude_Mineral_Dust",            "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Acid_Solution",             "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Reaction_Agent",            "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Crude_Adhesive_Paste",          "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Dye_Yellow",                    "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Essence_Vial",              "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Fixative_Solution",         "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Smelting_Aid",              "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Grinding_Dust",             "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Pickling_Salt",             "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Raw_Herb_Bundle",               "ALCHEMIST",  19,  19);
            insertGate(stmt, "Component_Standard_Mineral_Salt",         "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Distilled_Solvent",             "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Catalyst",             "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Adhesive_Paste",                "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Dye_Green",                     "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Herbal_Extract",       "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Primer",               "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Processing_Flux",      "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Reagent",              "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Preservative",         "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Standard_Poultice",             "ALCHEMIST",  20,  20);
            insertGate(stmt, "Component_Fine_Crystal_Dust",             "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Tanning_Acid",                  "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Reaction_Agent",           "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Binding_Compound",         "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Mordant_Powder",                "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Essence_Drops",            "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Fixative_Coat",            "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Quicksilver_Drops",             "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Grinding_Powder",          "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Pickling_Solution",        "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Fine_Herb_Preparation",         "ALCHEMIST",  35,  33);
            insertGate(stmt, "Component_Cobalt_Mineral_Salt",           "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Solvent",                "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Volatile_Catalyst",             "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Adhesive",               "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Dye_Blend",              "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Extract",                "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Primer",                 "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Flux_Compound",          "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Stabilizing_Agent",             "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Cobalt_Preservative",           "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Transmutation_Primer",          "ALCHEMIST",  36,  34);
            insertGate(stmt, "Component_Superior_Crystal_Dust",         "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Acid_Solution",        "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Catalyst_Powder",      "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Binding_Paste",        "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Dye_Fixative",         "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Essence_Concentrate",  "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Fixative_Solution",    "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Smelting_Aid",         "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Reagent_Powder",       "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Pickling_Salt",        "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Superior_Poultice",             "ALCHEMIST",  55,  50);
            insertGate(stmt, "Component_Thorium_Mineral_Salt",          "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Solvent",               "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Catalyst",              "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Adhesive",              "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Dye_Purple",                    "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Extract",               "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Primer",                "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Flux",                  "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Master_Reagent_Concentrate",    "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Thorium_Preservative",          "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Master_Herb_Preparation",       "ALCHEMIST",  56,  51);
            insertGate(stmt, "Component_Ancient_Crystal_Dust",          "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Acid_Essence",          "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Reaction_Agent",        "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Binding_Compound",      "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Master_Dye_Concentrate",        "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Essence_Vial",          "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Fixative_Coat",         "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Processing_Compound",   "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Grinding_Dust",         "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Pickling_Essence",      "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Ancient_Poultice",              "ALCHEMIST",  75,  67);
            insertGate(stmt, "Component_Void_Mineral_Salt",             "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Solvent",                  "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Catalyst",                 "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Adhesive",                 "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Dye_Black",                     "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Extract",                  "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Primer",                   "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Flux_Compound",            "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Reagent_Powder",           "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Void_Preservative",             "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Philosophers_Ite",              "ALCHEMIST",  83,  74);
            insertGate(stmt, "Component_Cosmic_Crystal_Dust",           "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Acid",                   "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Catalyst",               "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Binding_Paste",          "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Dye_Essence",            "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Essence",                "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Celestial_Fixative",            "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Processing_Flux",        "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Reagent",                "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Celestial_Preservative",        "ALCHEMIST",  95,  84);
            insertGate(stmt, "Component_Cosmic_Herb_Essence",           "ALCHEMIST",  95,  84);

            // Alchemist -- Consumables
            insertGate(stmt, "Consumable_Smelling_Salts",               "ALCHEMIST",   1,   4);
            insertGate(stmt, "Consumable_Crude_Armor_Coating",          "ALCHEMIST",   1,   4);
            insertGate(stmt, "Consumable_Crude_Stamina_Brew",           "ALCHEMIST",   1,   4);
            insertGate(stmt, "Consumable_Crude_Oil_Blend",              "ALCHEMIST",   1,   4);
            insertGate(stmt, "Consumable_Crude_Weapon_Coating",         "ALCHEMIST",  19,  19);
            insertGate(stmt, "Consumable_Raw_Armor_Balm",               "ALCHEMIST",  19,  19);
            insertGate(stmt, "Consumable_Raw_Vigor_Tonic",              "ALCHEMIST",  19,  19);
            insertGate(stmt, "Consumable_Raw_Wax_Stick",                "ALCHEMIST",  19,  19);
            insertGate(stmt, "Consumable_Firebrand_Oil",                "ALCHEMIST",  20,  20);
            insertGate(stmt, "Consumable_Ironbark_Wax",                 "ALCHEMIST",  20,  20);
            insertGate(stmt, "Consumable_Standard_Stamina_Elixir",      "ALCHEMIST",  20,  20);
            insertGate(stmt, "Consumable_Standard_Alchemy_Oil",         "ALCHEMIST",  20,  20);
            insertGate(stmt, "Consumable_Fine_Weapon_Coating",          "ALCHEMIST",  35,  33);
            insertGate(stmt, "Consumable_Fine_Armor_Coating",           "ALCHEMIST",  35,  33);
            insertGate(stmt, "Consumable_Fine_Vigor_Tonic",             "ALCHEMIST",  35,  33);
            insertGate(stmt, "Consumable_Fine_Wax_Blend",               "ALCHEMIST",  35,  33);
            insertGate(stmt, "Consumable_Frostbite_Coating",            "ALCHEMIST",  36,  34);
            insertGate(stmt, "Consumable_Flameguard_Lacquer",           "ALCHEMIST",  36,  34);
            insertGate(stmt, "Consumable_Superior_Stamina_Elixir",      "ALCHEMIST",  36,  34);
            insertGate(stmt, "Consumable_Superior_Alchemy_Oil",         "ALCHEMIST",  36,  34);
            insertGate(stmt, "Consumable_Thunderstrike_Varnish",        "ALCHEMIST",  55,  50);
            insertGate(stmt, "Consumable_Superior_Armor_Coating",       "ALCHEMIST",  55,  50);
            insertGate(stmt, "Consumable_Berserker_Draught",            "ALCHEMIST",  55,  50);
            insertGate(stmt, "Consumable_Reinforced_Wax_Seal",          "ALCHEMIST",  55,  50);
            insertGate(stmt, "Consumable_Master_Weapon_Coating",        "ALCHEMIST",  56,  51);
            insertGate(stmt, "Consumable_Master_Armor_Coating",         "ALCHEMIST",  56,  51);
            insertGate(stmt, "Consumable_Elixir_Fortitude",             "ALCHEMIST",  56,  51);
            insertGate(stmt, "Consumable_Master_Alchemy_Oil",           "ALCHEMIST",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Blade_Venom",          "ALCHEMIST",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Armor_Balm",           "ALCHEMIST",  75,  67);
            insertGate(stmt, "Consumable_Master_Vigor_Tonic",           "ALCHEMIST",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Wax_Seal",             "ALCHEMIST",  75,  67);
            insertGate(stmt, "Consumable_Void_Weapon_Coating",          "ALCHEMIST",  83,  74);
            insertGate(stmt, "Consumable_Void_Armor_Coating",           "ALCHEMIST",  83,  74);
            insertGate(stmt, "Consumable_Void_Elixir",                  "ALCHEMIST",  83,  74);
            insertGate(stmt, "Consumable_Void_Oil_Blend",               "ALCHEMIST",  83,  74);
            insertGate(stmt, "Consumable_Cosmic_Weapon_Anointment",     "ALCHEMIST",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Armor_Sealant",         "ALCHEMIST",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Draught",               "ALCHEMIST",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Wax",                   "ALCHEMIST",  95,  84);

            // =======================================================
            // COOK -- Components
            // =======================================================
            insertGate(stmt, "Component_Rendered_Fat",                  "COOK",   1,   4);
            insertGate(stmt, "Component_Purified_Water",                "COOK",   1,   4);
            insertGate(stmt, "Component_Seasoning_Salt",                "COOK",   1,   4);
            insertGate(stmt, "Component_Dough",                         "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Sauerkraut",              "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Gravy",                   "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Jerky",                   "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Sugar",                   "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Mystery_Spice",           "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Flavor_Extract",          "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Yeast_Starter",           "COOK",   1,   4);
            insertGate(stmt, "Component_Crude_Cooking_Oil",             "COOK",  19,  19);
            insertGate(stmt, "Component_Crude_Broth",                   "COOK",  19,  19);
            insertGate(stmt, "Component_Crude_Spice_Blend",             "COOK",  19,  19);
            insertGate(stmt, "Component_Crude_Batter_Mix",              "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Pickle_Brine",              "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Pan_Drippings",             "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Salted_Meat",               "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Honey",                     "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Exotic_Herb",               "COOK",  19,  19);
            insertGate(stmt, "Component_Raw_Juice_Concentrate",         "COOK",  19,  19);
            insertGate(stmt, "Component_Yeast_Culture",                 "COOK",  19,  19);
            insertGate(stmt, "Component_Clarified_Butter",              "COOK",  20,  20);
            insertGate(stmt, "Component_Fine_Broth",                    "COOK",  20,  20);
            insertGate(stmt, "Component_Smoked_Spice",                  "COOK",  20,  20);
            insertGate(stmt, "Component_Fine_Pastry_Dough",             "COOK",  20,  20);
            insertGate(stmt, "Component_Fermented_Brine",               "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Sauce",                "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Jerky",                "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Sugar",                "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Exotic_Spice",         "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Flavor_Extract",       "COOK",  20,  20);
            insertGate(stmt, "Component_Standard_Yeast_Blend",          "COOK",  20,  20);
            insertGate(stmt, "Component_Fine_Cooking_Oil",              "COOK",  35,  33);
            insertGate(stmt, "Component_Standard_Stock",                "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Spice_Blend",              "COOK",  35,  33);
            insertGate(stmt, "Component_Standard_Batter",               "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Vinegar",                  "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Gravy",                    "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Preserved_Fish",           "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Molasses",                 "COOK",  35,  33);
            insertGate(stmt, "Component_Truffle_Oil",                   "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Juice_Concentrate",        "COOK",  35,  33);
            insertGate(stmt, "Component_Fine_Culture_Starter",          "COOK",  35,  33);
            insertGate(stmt, "Component_Cobalt_Infused_Oil",            "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Spring_Water",           "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Seasoning",              "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Rising_Dough",           "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Fermented_Blend",        "COOK",  36,  34);
            insertGate(stmt, "Component_Aged_Wine_Reduction",           "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Cured_Meat",             "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Sweetener",              "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Exotic_Ingredient",      "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Flavor_Extract",         "COOK",  36,  34);
            insertGate(stmt, "Component_Cobalt_Yeast",                  "COOK",  36,  34);
            insertGate(stmt, "Component_Superior_Cooking_Fat",          "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Stock",                "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Spice_Blend",          "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Batter_Mix",           "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Pickle",               "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Sauce",                "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Preserved_Food",       "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Honey_Blend",          "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Mystery_Spice",        "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Juice_Concentrate",    "COOK",  55,  50);
            insertGate(stmt, "Component_Superior_Culture_Blend",        "COOK",  55,  50);
            insertGate(stmt, "Component_Thorium_Essence_Oil",           "COOK",  56,  51);
            insertGate(stmt, "Component_Thorium_Infused_Water",         "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Spice_Blend",            "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Pastry_Dough",           "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Fermented_Essence",      "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Reduction",              "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Cured_Meat",             "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Sweetener",              "COOK",  56,  51);
            insertGate(stmt, "Component_Dragon_Pepper",                 "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Flavor_Extract",         "COOK",  56,  51);
            insertGate(stmt, "Component_Master_Yeast_Blend",            "COOK",  56,  51);
            insertGate(stmt, "Component_Ancient_Cooking_Oil",           "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Stock",                 "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Seasoning",             "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Batter",                "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Vinegar",               "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Sauce_Essence",         "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Preserved_Food",        "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Honey_Essence",         "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Exotic_Ingredient",     "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Juice_Essence",         "COOK",  75,  67);
            insertGate(stmt, "Component_Ancient_Culture",               "COOK",  75,  67);
            insertGate(stmt, "Component_Starforged_Essence_Oil",        "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Essence_Water",            "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Spice_Essence",            "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Dough",                    "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Fermented_Blend",          "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Reduction",                "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Cured_Meat",               "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Sweetener",                "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Exotic_Spice",             "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Flavor_Extract",           "COOK",  83,  74);
            insertGate(stmt, "Component_Void_Yeast",                    "COOK",  83,  74);
            insertGate(stmt, "Component_Divine_Cooking_Oil",            "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Spring_Water",           "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Seasoning",              "COOK",  95,  84);
            insertGate(stmt, "Component_Celestial_Batter",              "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Vinegar",                "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Sauce",                  "COOK",  95,  84);
            insertGate(stmt, "Component_Celestial_Preserved_Food",      "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Sugar_Crystal",          "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Mystery_Ingredient",     "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Juice_Essence",          "COOK",  95,  84);
            insertGate(stmt, "Component_Cosmic_Culture",                "COOK",  95,  84);

            // Cook -- Consumables
            insertGate(stmt, "Consumable_Trail_Rations",                "COOK",   1,   4);
            insertGate(stmt, "Consumable_Hunters_Stew",                 "COOK",   1,   4);
            insertGate(stmt, "Consumable_Crude_Group_Stew",             "COOK",   1,   4);
            insertGate(stmt, "Consumable_Crude_Surprise_Stew",          "COOK",   1,   4);
            insertGate(stmt, "Consumable_Crude_Travel_Bread",           "COOK",  19,  19);
            insertGate(stmt, "Consumable_Crude_Stamina_Broth",          "COOK",  19,  19);
            insertGate(stmt, "Consumable_Simple_Communal_Bread",        "COOK",  19,  19);
            insertGate(stmt, "Consumable_Simple_Exotic_Dish",           "COOK",  19,  19);
            insertGate(stmt, "Consumable_Standard_Trail_Mix",           "COOK",  20,  20);
            insertGate(stmt, "Consumable_Spiced_Skewers",               "COOK",  20,  20);
            insertGate(stmt, "Consumable_Warriors_Feast",               "COOK",  20,  20);
            insertGate(stmt, "Consumable_Standard_Special_Dish",        "COOK",  20,  20);
            insertGate(stmt, "Consumable_Fine_Travel_Bread",            "COOK",  35,  33);
            insertGate(stmt, "Consumable_Fine_Power_Meal",              "COOK",  35,  33);
            insertGate(stmt, "Consumable_Fine_Group_Spread",            "COOK",  35,  33);
            insertGate(stmt, "Consumable_Fine_Exotic_Plate",            "COOK",  35,  33);
            insertGate(stmt, "Consumable_Superior_Trail_Rations",       "COOK",  36,  34);
            insertGate(stmt, "Consumable_Miners_Pie",                   "COOK",  36,  34);
            insertGate(stmt, "Consumable_Royal_Banquet",                "COOK",  36,  34);
            insertGate(stmt, "Consumable_Superior_Special_Dish",        "COOK",  36,  34);
            insertGate(stmt, "Consumable_Cobalt_Infused_Bread",         "COOK",  55,  50);
            insertGate(stmt, "Consumable_Superior_Stat_Meal",           "COOK",  55,  50);
            insertGate(stmt, "Consumable_Superior_Feast_Platter",       "COOK",  55,  50);
            insertGate(stmt, "Consumable_Cobalt_Infused_Meal",          "COOK",  55,  50);
            insertGate(stmt, "Consumable_Master_Trail_Rations",         "COOK",  56,  51);
            insertGate(stmt, "Consumable_Master_Power_Meal",            "COOK",  56,  51);
            insertGate(stmt, "Consumable_Master_Group_Feast",           "COOK",  56,  51);
            insertGate(stmt, "Consumable_Dragonfire_Chili",             "COOK",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Travel_Cake",          "COOK",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Stat_Feast",           "COOK",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Banquet_Spread",       "COOK",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Special_Dish",         "COOK",  75,  67);
            insertGate(stmt, "Consumable_Void_Trail_Mix",               "COOK",  83,  74);
            insertGate(stmt, "Consumable_Void_Stat_Meal",               "COOK",  83,  74);
            insertGate(stmt, "Consumable_Void_Feast",                   "COOK",  83,  74);
            insertGate(stmt, "Consumable_Void_Special_Dish",            "COOK",  83,  74);
            insertGate(stmt, "Consumable_Celestial_Travel_Cake",        "COOK",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Power_Feast",           "COOK",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Banquet",            "COOK",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Exotic_Feast",          "COOK",  95,  84);

            // =======================================================
            // CARPENTER -- Components
            // =======================================================
            insertGate(stmt, "Component_Planed_Planks",                 "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Wooden_Pegs",                   "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Mortise_Joint",           "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Handle",                  "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Frame",                   "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Veneer",                  "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Glass_Piece",             "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Lacquer",                 "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Platter",                 "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Wand_Blank",              "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Hardwood_Block",          "CARPENTER",   1,   4);
            insertGate(stmt, "Component_Crude_Board",                   "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Crude_Dowel_Set",               "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Simple_Tenon_Joint",            "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Rough_Shaft",                   "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Turned_Spindle",                "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Rough_Inlay_Strip",             "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Raw_Crystal_Piece",             "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Raw_Wood_Finish",               "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Simple_Bowl",                   "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Raw_Staff_Blank",               "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Raw_Heartwood_Piece",           "CARPENTER",  19,  19);
            insertGate(stmt, "Component_Standard_Plank",                "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Carved_Dowels",                 "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Dovetail_Joint",                "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Weapon_Haft",                   "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Standard_Frame",                "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Fine_Veneer",                   "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Glass_Vial",                    "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Lacquer",                       "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Serving_Platter",               "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Runewood_Blank",                "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Standard_Hardwood",             "CARPENTER",  20,  20);
            insertGate(stmt, "Component_Fine_Board",                    "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Peg_Set",                  "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Mortise_Joint",            "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Handle",                   "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Spindle",                  "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Standard_Inlay_Strip",          "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Crystal_Piece",            "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Wood_Finish",              "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Bowl",                     "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Wand_Blank",               "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Fine_Heartwood_Block",          "CARPENTER",  35,  33);
            insertGate(stmt, "Component_Cobalt_Treated_Plank",          "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Dowels",                 "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Joint_Assembly",         "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Handle",                 "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Frame",                  "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Ironwood_Inlay",                "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Glass",                  "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Lacquer",                "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Serving_Set",            "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Staff_Blank",            "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Treated_Wood",           "CARPENTER",  36,  34);
            insertGate(stmt, "Component_Reinforced_Board",              "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Peg_Set",            "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Tenon_Joint",        "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Shaft",              "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Spindle",            "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Superior_Veneer",               "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Crystal_Bezel",                 "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Superior_Wood_Finish",          "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Superior_Platter",              "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Superior_Wand_Blank",           "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Reinforced_Heartwood",          "CARPENTER",  55,  50);
            insertGate(stmt, "Component_Thorium_Treated_Plank",         "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Dowels",                "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Joint_Assembly",        "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Handle",                "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Frame",                 "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Ebony_Veneer",                  "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Glass",                 "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Lacquer",               "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Master_Serving_Set",            "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Staff_Blank",           "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Treated_Wood",          "CARPENTER",  56,  51);
            insertGate(stmt, "Component_Ancient_Board",                 "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Peg_Set",               "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Mortise_Joint",         "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Shaft",                 "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Form",                  "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Inlay_Strip",           "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Crystal_Piece",         "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Wood_Finish",           "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Platter",               "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Wand_Blank",            "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Heartwood",             "CARPENTER",  75,  67);
            insertGate(stmt, "Component_Starforged_Plank",              "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Dowels",                   "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Joint",                    "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Starforged_Handle",             "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Frame",                    "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Veneer",                   "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Crystal",                  "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Lacquer",                  "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Serving_Set",              "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Void_Staff_Blank",              "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Worldtree_Heartwood",           "CARPENTER",  83,  74);
            insertGate(stmt, "Component_Divine_Board",                  "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Peg_Set",             "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Joinery",             "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Divine_Shaft",                  "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Form",                "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Inlay",                  "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Glass",                  "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Wood_Finish",         "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Platter",             "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Wand_Blank",             "CARPENTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Heartwood",              "CARPENTER",  95,  84);

            // Carpenter -- Consumables
            insertGate(stmt, "Consumable_Crude_Wooden_Barrier",         "CARPENTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Workstation",            "CARPENTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Siege_Ladder",           "CARPENTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Lean_To",                "CARPENTER",   1,   4);
            insertGate(stmt, "Consumable_Wooden_Barricade",             "CARPENTER",  19,  19);
            insertGate(stmt, "Consumable_Simple_Repair_Bench",          "CARPENTER",  19,  19);
            insertGate(stmt, "Consumable_Simple_Ram",                   "CARPENTER",  19,  19);
            insertGate(stmt, "Consumable_Simple_Field_Tent",            "CARPENTER",  19,  19);
            insertGate(stmt, "Consumable_Standard_Barricade",           "CARPENTER",  20,  20);
            insertGate(stmt, "Consumable_Campfire_Kit",                 "CARPENTER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Siege_Ladder",        "CARPENTER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Field_Shelter",       "CARPENTER",  20,  20);
            insertGate(stmt, "Consumable_Fine_Wooden_Barrier",          "CARPENTER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Workstation",             "CARPENTER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Ram",                     "CARPENTER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Field_Tent",              "CARPENTER",  35,  33);
            insertGate(stmt, "Consumable_Cobalt_Barricade",             "CARPENTER",  36,  34);
            insertGate(stmt, "Consumable_Superior_Workstation",         "CARPENTER",  36,  34);
            insertGate(stmt, "Consumable_Watchtower_Kit",               "CARPENTER",  36,  34);
            insertGate(stmt, "Consumable_Superior_Field_Shelter",       "CARPENTER",  36,  34);
            insertGate(stmt, "Consumable_Reinforced_Barrier",           "CARPENTER",  55,  50);
            insertGate(stmt, "Consumable_Cobalt_Repair_Bench",          "CARPENTER",  55,  50);
            insertGate(stmt, "Consumable_Superior_Ram",                 "CARPENTER",  55,  50);
            insertGate(stmt, "Consumable_Cobalt_Field_Tent",            "CARPENTER",  55,  50);
            insertGate(stmt, "Consumable_Master_Barricade",             "CARPENTER",  56,  51);
            insertGate(stmt, "Consumable_Master_Workstation",           "CARPENTER",  56,  51);
            insertGate(stmt, "Consumable_Master_Siege_Tower",           "CARPENTER",  56,  51);
            insertGate(stmt, "Consumable_Master_Field_Shelter",         "CARPENTER",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Barrier",              "CARPENTER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Repair_Bench",         "CARPENTER",  75,  67);
            insertGate(stmt, "Consumable_Siege_Ram",                    "CARPENTER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Field_Tent",           "CARPENTER",  75,  67);
            insertGate(stmt, "Consumable_Void_Barricade",               "CARPENTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Workstation",             "CARPENTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Siege_Engine",            "CARPENTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Field_Shelter",           "CARPENTER",  83,  74);
            insertGate(stmt, "Consumable_Celestial_Barrier",            "CARPENTER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Repair_Bench",       "CARPENTER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Siege_Engine",       "CARPENTER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Field_Tent",         "CARPENTER",  95,  84);

            // =======================================================
            // ENCHANTER -- Components
            // =======================================================
            insertGate(stmt, "Component_Glyph_Paper",                   "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Mana_Shard",                    "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Spirit_Wisp",             "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Sigil_Mark",              "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Spell_Weave",             "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Gem_Fragment",            "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Fire_Rune",               "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Strength_Rune",           "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Arcane_Dust",             "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Ward_Stone",              "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Focus_Crystal",           "ENCHANTER",   1,   4);
            insertGate(stmt, "Component_Crude_Enchanting_Ink",          "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Crude_Crystal_Fragment",        "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Essence_Drop",              "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Sigil_Dust",                    "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Magic_Circle",              "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Prism_Piece",               "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Crude_Ice_Rune",                "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Crude_Protection_Rune",         "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Magic_Powder",              "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Protection_Charm",          "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Raw_Lens_Piece",                "ENCHANTER",  19,  19);
            insertGate(stmt, "Component_Standard_Glyph_Paper",          "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Mana_Crystal",         "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Spirit_Essence",                "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Sigil",                "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Ether_Weave",                   "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Gem_Cut",              "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Fire_Rune",            "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Strength_Rune",        "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Arcane_Dust",          "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Ward_Stone",           "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Standard_Focus_Crystal",        "ENCHANTER",  20,  20);
            insertGate(stmt, "Component_Fine_Enchanting_Ink",           "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Resonance_Crystal",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Essence_Drop",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Sigil_Mark",               "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Magic_Circle",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Prism",                    "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Standard_Ice_Rune",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Standard_Protection_Rune",      "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Magic_Powder",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Protection_Charm",         "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Fine_Lens",                     "ENCHANTER",  35,  33);
            insertGate(stmt, "Component_Cobalt_Glyph_Paper",            "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Crystal_Shard",          "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Essence",                "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Sigil",                  "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Spell_Weave",            "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Gem",                    "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Fire_Rune",              "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Rune_Sharpness",                "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Arcane_Dust",            "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Binding_Circle",                "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Cobalt_Focus_Crystal",          "ENCHANTER",  36,  34);
            insertGate(stmt, "Component_Superior_Enchanting_Ink",       "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Mana_Crystal",         "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Spirit_Essence",       "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Rune_Mark",            "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Magic_Circle",         "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Soul_Gem",                      "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Cobalt_Ice_Rune",               "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Rune_Warding",                  "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Magic_Powder",         "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Ward_Stone",           "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Superior_Lens",                 "ENCHANTER",  55,  50);
            insertGate(stmt, "Component_Thorium_Glyph_Paper",           "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Crystal_Shard",         "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Essence",               "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Sigil",                 "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Spell_Weave",           "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Gem",                   "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Fire_Rune",             "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Rune_Vitality",                 "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Thorium_Arcane_Dust",           "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Master_Ward_Stone",             "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Cosmic_Lens",                   "ENCHANTER",  56,  51);
            insertGate(stmt, "Component_Ancient_Enchanting_Ink",        "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Mana_Crystal",          "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Spirit_Essence",        "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Rune_Mark",             "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Magic_Circle",          "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Prism",                 "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Thorium_Ice_Rune",              "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Protection_Rune",       "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Magic_Powder",          "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Protection_Charm",      "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Ancient_Focus_Crystal",         "ENCHANTER",  75,  67);
            insertGate(stmt, "Component_Void_Glyph_Paper",              "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Crystal_Shard",            "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Essence",                  "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Sigil",                    "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Spell_Weave",              "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Prism",                    "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Fire_Rune",                "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Rune_Power",                    "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Arcane_Dust",              "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Ward_Stone",               "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Void_Focus_Crystal",            "ENCHANTER",  83,  74);
            insertGate(stmt, "Component_Cosmic_Enchanting_Ink",         "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Crystal",                "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Spirit_Essence",         "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Rune",                   "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Magic_Circle",           "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Gem",                    "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Ice_Rune",               "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Strength_Rune",          "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Magic_Powder",           "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Cosmic_Protection_Charm",       "ENCHANTER",  95,  84);
            insertGate(stmt, "Component_Celestial_Lens",                "ENCHANTER",  95,  84);

            // Enchanter -- Consumables
            insertGate(stmt, "Consumable_Crude_Flame_Scroll",           "ENCHANTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Shield_Scroll",          "ENCHANTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Speed_Scroll",           "ENCHANTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Prestige_Scroll",        "ENCHANTER",   1,   4);
            insertGate(stmt, "Consumable_Crude_Frost_Scroll",           "ENCHANTER",  19,  19);
            insertGate(stmt, "Consumable_Crude_Heal_Scroll",            "ENCHANTER",  19,  19);
            insertGate(stmt, "Consumable_Crude_Light_Scroll",           "ENCHANTER",  19,  19);
            insertGate(stmt, "Consumable_Raw_Power_Scroll",             "ENCHANTER",  19,  19);
            insertGate(stmt, "Consumable_Scroll_Fireball",              "ENCHANTER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Shield_Scroll",       "ENCHANTER",  20,  20);
            insertGate(stmt, "Consumable_Scroll_Haste",                 "ENCHANTER",  20,  20);
            insertGate(stmt, "Consumable_Standard_Prestige_Scroll",     "ENCHANTER",  20,  20);
            insertGate(stmt, "Consumable_Scroll_Frostbolt",             "ENCHANTER",  35,  33);
            insertGate(stmt, "Consumable_Scroll_Heal",                  "ENCHANTER",  35,  33);
            insertGate(stmt, "Consumable_Standard_Light_Scroll",        "ENCHANTER",  35,  33);
            insertGate(stmt, "Consumable_Fine_Power_Scroll",            "ENCHANTER",  35,  33);
            insertGate(stmt, "Consumable_Cobalt_Flame_Scroll",          "ENCHANTER",  36,  34);
            insertGate(stmt, "Consumable_Scroll_Shield",                "ENCHANTER",  36,  34);
            insertGate(stmt, "Consumable_Scroll_Teleport",              "ENCHANTER",  36,  34);
            insertGate(stmt, "Consumable_Superior_Prestige_Scroll",     "ENCHANTER",  36,  34);
            insertGate(stmt, "Consumable_Scroll_Lightning",             "ENCHANTER",  55,  50);
            insertGate(stmt, "Consumable_Superior_Heal_Scroll",         "ENCHANTER",  55,  50);
            insertGate(stmt, "Consumable_Superior_Speed_Scroll",        "ENCHANTER",  55,  50);
            insertGate(stmt, "Consumable_Cobalt_Power_Scroll",          "ENCHANTER",  55,  50);
            insertGate(stmt, "Consumable_Master_Flame_Scroll",          "ENCHANTER",  56,  51);
            insertGate(stmt, "Consumable_Master_Shield_Scroll",         "ENCHANTER",  56,  51);
            insertGate(stmt, "Consumable_Master_Speed_Scroll",          "ENCHANTER",  56,  51);
            insertGate(stmt, "Consumable_Scroll_Resurrection",          "ENCHANTER",  56,  51);
            insertGate(stmt, "Consumable_Ancient_Frost_Scroll",         "ENCHANTER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Heal_Scroll",          "ENCHANTER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Light_Scroll",         "ENCHANTER",  75,  67);
            insertGate(stmt, "Consumable_Ancient_Prestige_Scroll",      "ENCHANTER",  75,  67);
            insertGate(stmt, "Consumable_Void_Flame_Scroll",            "ENCHANTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Shield_Scroll",           "ENCHANTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Utility_Scroll",          "ENCHANTER",  83,  74);
            insertGate(stmt, "Consumable_Void_Prestige_Scroll",         "ENCHANTER",  83,  74);
            insertGate(stmt, "Consumable_Cosmic_Destruction_Scroll",    "ENCHANTER",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Defense_Scroll",        "ENCHANTER",  95,  84);
            insertGate(stmt, "Consumable_Cosmic_Utility_Scroll",        "ENCHANTER",  95,  84);
            insertGate(stmt, "Consumable_Celestial_Power_Scroll",       "ENCHANTER",  95,  84);

            stmt.executeBatch();

            // Disabled recipes -- items whose recipes should be removed from all benches
            seedDisabledRecipes(conn);

            int after = 0;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                if (rs.next()) after = rs.getInt(1);
            }

            int added = after - before;
            if (added > 0) {
                LOGGER.at(Level.INFO).log("Seeded " + added + " cross-profession component recipe gates");
            } else {
                LOGGER.at(Level.INFO).log("Cross-profession component gates already seeded");
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed component recipe gates: " + e.getMessage());
        }
    }

    /**
     * Seed recipes that should be completely removed from benches.
     * Uses upsert to ensure enabled=false even if the row already exists.
     */
    private void seedDisabledRecipes(Connection conn) throws SQLException {
        String sql = """
            INSERT INTO prof_recipe_gates (recipe_output_id, required_profession, required_level, profession_xp_granted, enabled)
            VALUES (?, ?, 1, 0, false)
            ON CONFLICT (recipe_output_id) DO UPDATE SET enabled = false
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Crude Diving Gear -- not appropriate for our game
            for (String slot : new String[]{"Head", "Chest", "Hands", "Legs"}) {
                stmt.setString(1, "Armor_Diving_Crude_" + slot);
                stmt.setString(2, "ARMORSMITH");
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            LOGGER.at(Level.INFO).log("Seeded %d disabled recipe entries", results.length);
        }

        // Disable ALL Enchanter recipes -- bench not currently in use
        try (Statement s = conn.createStatement()) {
            int updated = s.executeUpdate(
                    "UPDATE prof_recipe_gates SET enabled = false WHERE required_profession = 'ENCHANTER'");
            if (updated > 0) {
                LOGGER.at(Level.INFO).log("Disabled %d Enchanter recipe gates", updated);
            }
        }
    }

    /**
     * Backfills ingredient data for component gates that have empty ingredients.
     * Uses name-pattern matching to assign varied recipes based on component type.
     * Safe to run repeatedly - only updates rows where ingredients = '[]'.
     */
    public void seedComponentIngredients() {
        // Pattern-based ingredient assignment for components.
        // Each pattern matches part of the recipe_output_id (case-insensitive).
        // Format: {pattern, ingredients_json, time_seconds}
        String[][] rules = {
            // Weaponsmith metal components
            {"Blade|Edge|Guard|Crossguard|Tang|Pommel", "[{\"itemId\":\"Ingredient_Bar_Iron\",\"quantity\":2},{\"itemId\":\"Ingredient_Ore_Dust\",\"quantity\":1}]", "5"},
            {"Filing|Etching|Inlay|Wire_Coil|Latch|Rivet|Nail", "[{\"itemId\":\"Ingredient_Bar_Copper\",\"quantity\":1},{\"itemId\":\"Ingredient_Ore_Dust\",\"quantity\":1}]", "3"},
            {"Handle|Grip|Hilt|Wrap", "[{\"itemId\":\"Ingredient_Leather_Strip\",\"quantity\":2},{\"itemId\":\"Ingredient_Stick\",\"quantity\":1}]", "4"},
            {"Flux|Powder|Reagent|Grinding", "[{\"itemId\":\"Ingredient_Ore_Dust\",\"quantity\":2},{\"itemId\":\"Ingredient_Charcoal\",\"quantity\":1}]", "3"},
            // Armorsmith metal components
            {"Plate|Ring|Chain|Chainmail|Scale_Plate|Metal_Strip|Metal_Fusion|Buckle|Fastener|Gear", "[{\"itemId\":\"Ingredient_Bar_Iron\",\"quantity\":3}]", "6"},
            // Leatherworker components
            {"Padding|Lining|Strap|Batting|Leather|Hide|Cured|Boiled|Reptile|Sinew|Conditioner", "[{\"itemId\":\"Ingredient_Hide\",\"quantity\":2},{\"itemId\":\"Ingredient_Leather_Strip\",\"quantity\":1}]", "5"},
            // Alchemist components
            {"Vial|Elixir|Tincture|Poultice|Essence|Acid|Reaction|Processing|Compound", "[{\"itemId\":\"Ingredient_Herb\",\"quantity\":2},{\"itemId\":\"Ingredient_Crystal_Shard\",\"quantity\":1}]", "4"},
            // Cook components
            {"Fillet|Brine|Seasoning|Batter|Sauce|Juice|Honey|Vinegar|Pickling|Wine|Oil|Culture|Preserved|Platter|Exotic_Ingredient", "[{\"itemId\":\"Ingredient_Meat\",\"quantity\":1},{\"itemId\":\"Ingredient_Herb\",\"quantity\":1}]", "3"},
            // Carpenter components
            {"Plank|Dowel|Joint|Board|Peg|Stock|Shaft|Mortise|Ornament|Panel|Embossed|Heartwood|Wood_Finish|Wand|Frame|Shield_Frame|Articulated|Carved", "[{\"itemId\":\"Ingredient_Stick\",\"quantity\":3},{\"itemId\":\"Ingredient_Resin\",\"quantity\":1}]", "5"},
            // Enchanter components
            {"Rune|Sigil|Glyph|Magic|Mana|Crystal|Prism|Focus|Circle|Binding|Protection|Charm|Spirit|Enchant|Ink", "[{\"itemId\":\"Ingredient_Arcane_Dust\",\"quantity\":2},{\"itemId\":\"Ingredient_Crystal_Shard\",\"quantity\":1}]", "6"},
            // Tailor components
            {"Thread|Weave|Patch|Cloth|Fabric|Ribbon|Cord|Button|Bone_Needle|Spool|Pin|Dye|Colored", "[{\"itemId\":\"Ingredient_Fabric_Scrap\",\"quantity\":2},{\"itemId\":\"Ingredient_Fibre\",\"quantity\":1}]", "4"},
            // Catch-all for components with no specific match
            {"Alloy|Adhesive|Fixative|Forge|Coat|Scrap|Form|Blank", "[{\"itemId\":\"Ingredient_Bar_Copper\",\"quantity\":2}]", "4"},
        };

        try (Connection conn = databaseManager.getConnection()) {
            int totalUpdated = 0;

            for (String[] rule : rules) {
                String pattern = rule[0];
                String ingredients = rule[1];
                int timeSeconds = Integer.parseInt(rule[2]);

                // Build regex pattern for PostgreSQL
                String regexPattern = "(" + pattern + ")";

                String sql = """
                    UPDATE prof_recipe_gates
                    SET ingredients = ?::jsonb, time_seconds = ?
                    WHERE recipe_output_id LIKE 'Component_%'
                      AND ingredients = '[]'::jsonb
                      AND recipe_output_id ~* ?
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, ingredients);
                    stmt.setInt(2, timeSeconds);
                    stmt.setString(3, regexPattern);
                    totalUpdated += stmt.executeUpdate();
                }
            }

            if (totalUpdated > 0) {
                LOGGER.at(Level.INFO).log("Backfilled ingredients for " + totalUpdated + " component gates");
            } else {
                LOGGER.at(Level.INFO).log("Component ingredient backfill: no updates needed");
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to backfill component ingredients: " + e.getMessage());
        }
    }

    private void insertGate(PreparedStatement stmt, String outputId, String profession,
                            int level, int xp) throws SQLException {
        stmt.setString(1, outputId);
        stmt.setString(2, profession);
        stmt.setInt(3, level);
        stmt.setInt(4, xp);
        stmt.addBatch();
    }
}
