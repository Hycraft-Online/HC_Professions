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

        String sql = "SELECT recipe_output_id, required_profession, required_level, profession_xp_granted, enabled FROM prof_recipe_gates WHERE enabled = true";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String outputId = rs.getString("recipe_output_id");
                Profession prof = Profession.fromString(rs.getString("required_profession"));
                if (prof != null) {
                    gates.put(outputId.toLowerCase(), new RecipeGate(
                        outputId,
                        prof,
                        rs.getInt("required_level"),
                        rs.getInt("profession_xp_granted"),
                        rs.getBoolean("enabled")
                    ));
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load recipe gates: " + e.getMessage());
        }

        LOGGER.at(Level.INFO).log("Loaded " + gates.size() + " recipe gates");
        return gates;
    }

    public void seedDefaults() {
        try (Connection conn = databaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    LOGGER.at(Level.INFO).log("prof_recipe_gates already has data, skipping seed");
                    return;
                }
            }

            String insertSql = """
                INSERT INTO prof_recipe_gates (recipe_output_id, required_profession, required_level, profession_xp_granted)
                VALUES (?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                // Weaponsmith: Swords
                insertGate(stmt, "Weapon_Sword_Copper",         "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Sword_Iron",           "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Sword_Thorium",        "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Sword_Cobalt",         "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Sword_Mithril",        "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Sword_Adamantite",     "WEAPONSMITH", 70, 100);

                // Weaponsmith: Longswords
                insertGate(stmt, "Weapon_Longsword_Crude",      "WEAPONSMITH", 1,  5);
                insertGate(stmt, "Weapon_Longsword_Copper",     "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Longsword_Iron",       "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Longsword_Thorium",    "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Longsword_Cobalt",     "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Longsword_Scarab",     "WEAPONSMITH", 50, 65);
                insertGate(stmt, "Weapon_Longsword_Adamantite", "WEAPONSMITH", 70, 100);

                // Weaponsmith: Axes
                insertGate(stmt, "Weapon_Axe_Copper",           "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Axe_Iron",             "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Axe_Thorium",          "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Axe_Cobalt",           "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Axe_Adamantite",       "WEAPONSMITH", 70, 100);

                // Weaponsmith: Battleaxes
                insertGate(stmt, "Weapon_Battleaxe_Copper",     "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Battleaxe_Iron",       "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Battleaxe_Thorium",    "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Battleaxe_Cobalt",     "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Battleaxe_Mithril",    "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Battleaxe_Adamantite", "WEAPONSMITH", 70, 100);

                // Weaponsmith: Maces
                insertGate(stmt, "Weapon_Mace_Copper",          "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Mace_Iron",            "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Mace_Thorium",         "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Mace_Cobalt",          "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Mace_Mithril",         "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Mace_Adamantite",      "WEAPONSMITH", 70, 100);

                // Weaponsmith: Clubs
                insertGate(stmt, "Weapon_Club_Copper",          "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Club_Iron",            "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Club_Thorium",         "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Club_Cobalt",          "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Club_Adamantite",      "WEAPONSMITH", 70, 100);

                // Weaponsmith: Daggers
                insertGate(stmt, "Weapon_Daggers_Copper",       "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Daggers_Iron",         "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Daggers_Thorium",      "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Daggers_Cobalt",       "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Daggers_Mithril",      "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Daggers_Adamantite",   "WEAPONSMITH", 70, 100);

                // Weaponsmith: Shortbows
                insertGate(stmt, "Weapon_Shortbow_Crude",       "WEAPONSMITH", 1,  5);
                insertGate(stmt, "Weapon_Shortbow_Copper",      "WEAPONSMITH", 1,  10);
                insertGate(stmt, "Weapon_Shortbow_Iron",        "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Shortbow_Thorium",     "WEAPONSMITH", 25, 35);
                insertGate(stmt, "Weapon_Shortbow_Cobalt",      "WEAPONSMITH", 40, 55);
                insertGate(stmt, "Weapon_Shortbow_Mithril",     "WEAPONSMITH", 55, 75);
                insertGate(stmt, "Weapon_Shortbow_Adamantite",  "WEAPONSMITH", 70, 100);

                // Weaponsmith: Crossbow & Arrow
                insertGate(stmt, "Weapon_Crossbow_Iron",        "WEAPONSMITH", 10, 20);
                insertGate(stmt, "Weapon_Arrow_Crude",           "WEAPONSMITH", 1,  5);

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

                // Armorsmith: Shields
                insertGate(stmt, "Weapon_Shield_Copper",        "ARMORSMITH", 1,  10);
                insertGate(stmt, "Weapon_Shield_Iron",          "ARMORSMITH", 10, 20);
                insertGate(stmt, "Weapon_Shield_Thorium",       "ARMORSMITH", 25, 35);
                insertGate(stmt, "Weapon_Shield_Cobalt",        "ARMORSMITH", 40, 55);
                insertGate(stmt, "Weapon_Shield_Mithril",       "ARMORSMITH", 55, 75);
                insertGate(stmt, "Weapon_Shield_Adamantite",    "ARMORSMITH", 70, 100);

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
                // Tier 2: Tavern set (IL 2, darkwood planks + cloth)
                insertGate(stmt, "Furniture_Tavern_Bed",             "CARPENTER", 10, 15);
                insertGate(stmt, "Furniture_Tavern_Chest_Small",     "CARPENTER", 10, 15);
                insertGate(stmt, "Furniture_Tavern_Chest_Large",     "CARPENTER", 10, 20);
                insertGate(stmt, "Furniture_Tavern_Candle",          "CARPENTER", 10, 12);
                insertGate(stmt, "Furniture_Tavern_Chandelier",      "CARPENTER", 10, 15);
                // Tier 3: Ancient set (IL 3, rare blackwood + wool)
                insertGate(stmt, "Furniture_Ancient_Bed",            "CARPENTER", 20, 25);
                insertGate(stmt, "Furniture_Ancient_Chest_Small",    "CARPENTER", 20, 25);
                insertGate(stmt, "Furniture_Ancient_Chest_Large",    "CARPENTER", 20, 30);
                insertGate(stmt, "Furniture_Ancient_Candle",         "CARPENTER", 20, 20);
                // Tier 4: Lumberjack set (IL 4, hardwood planks + leather)
                insertGate(stmt, "Furniture_Lumberjack_Bed",         "CARPENTER", 30, 35);
                insertGate(stmt, "Furniture_Lumberjack_Chest_Small", "CARPENTER", 30, 35);
                insertGate(stmt, "Furniture_Lumberjack_Chest_Large", "CARPENTER", 30, 40);
                insertGate(stmt, "Furniture_Lumberjack_Lamp",        "CARPENTER", 30, 30);
                insertGate(stmt, "Furniture_Lumberjack_Lantern",     "CARPENTER", 30, 30);
                // Tier 5: Feran set (IL 5, drywood + leather + bone)
                insertGate(stmt, "Furniture_Feran_Bed",              "CARPENTER", 40, 45);
                insertGate(stmt, "Furniture_Feran_Chest_Small",      "CARPENTER", 40, 45);
                insertGate(stmt, "Furniture_Feran_Chest_Large",      "CARPENTER", 40, 50);
                insertGate(stmt, "Furniture_Feran_Chandelier",       "CARPENTER", 40, 40);
                insertGate(stmt, "Furniture_Feran_Candle",           "CARPENTER", 40, 40);
                insertGate(stmt, "Furniture_Feran_Torch",            "CARPENTER", 40, 35);
                insertGate(stmt, "Furniture_Feran_Torch_Tall",       "CARPENTER", 40, 40);
                // Tier 6: Temple Dark set (IL 6, stone/mineral)
                insertGate(stmt, "Furniture_Temple_Dark_Bed",        "CARPENTER", 50, 55);
                insertGate(stmt, "Furniture_Temple_Dark_Chest_Small","CARPENTER", 50, 55);
                insertGate(stmt, "Furniture_Temple_Dark_Chest_Large","CARPENTER", 50, 60);
                insertGate(stmt, "Furniture_Temple_Dark_Brazier",    "CARPENTER", 50, 50);
                // Tier 7: Jungle set (IL 7, bamboo + multicolor cloth)
                insertGate(stmt, "Furniture_Jungle_Bed",             "CARPENTER", 60, 70);
                insertGate(stmt, "Furniture_Jungle_Chest_Small",     "CARPENTER", 60, 70);
                insertGate(stmt, "Furniture_Jungle_Chest_Large",     "CARPENTER", 60, 75);
                insertGate(stmt, "Furniture_Jungle_Candle",          "CARPENTER", 60, 60);
                insertGate(stmt, "Furniture_Jungle_Torch",           "CARPENTER", 60, 60);

                // ═══════════════════════════════════════════════════════
                // Profession bench crafting (at Workbench)
                // ═══════════════════════════════════════════════════════
                insertGate(stmt, "Bench_Weaponsmith_Forge",     "WEAPONSMITH", 1, 0);
                insertGate(stmt, "Bench_Armorsmith_Anvil",      "ARMORSMITH",  1, 0);

                LOGGER.at(Level.INFO).log("Seeded default recipe gate entries");
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
            INSERT INTO prof_recipe_gates (recipe_output_id, required_profession, required_level, profession_xp_granted)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (recipe_output_id) DO NOTHING
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            int before = 0;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM prof_recipe_gates")) {
                if (rs.next()) before = rs.getInt(1);
            }

            // ═══════════════════════════════════════════════════════
            // WEAPONSMITH — Components (Rivets, Fittings)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Rivets_Crude",      "WEAPONSMITH", 1,  8);
            insertGate(stmt, "Component_Rivets_Refined",    "WEAPONSMITH", 25, 20);
            insertGate(stmt, "Component_Rivets_Superior",   "WEAPONSMITH", 50, 40);
            insertGate(stmt, "Component_Rivets_Pristine",   "WEAPONSMITH", 75, 65);
            insertGate(stmt, "Component_Fittings_Crude",    "WEAPONSMITH", 1,  8);
            insertGate(stmt, "Component_Fittings_Refined",  "WEAPONSMITH", 25, 20);
            insertGate(stmt, "Component_Fittings_Superior", "WEAPONSMITH", 50, 40);
            insertGate(stmt, "Component_Fittings_Pristine", "WEAPONSMITH", 75, 65);

            // Weaponsmith — Consumables (Sharpening Stones)
            insertGate(stmt, "Consumable_Sharpening_Stone_Crude",    "WEAPONSMITH", 5,  10);
            insertGate(stmt, "Consumable_Sharpening_Stone_Refined",  "WEAPONSMITH", 30, 25);
            insertGate(stmt, "Consumable_Sharpening_Stone_Superior", "WEAPONSMITH", 55, 45);
            insertGate(stmt, "Consumable_Sharpening_Stone_Pristine", "WEAPONSMITH", 80, 70);

            // ═══════════════════════════════════════════════════════
            // ARMORSMITH — Components (Chainlinks, Plating)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Chainlinks_Crude",      "ARMORSMITH", 1,  8);
            insertGate(stmt, "Component_Chainlinks_Refined",    "ARMORSMITH", 25, 20);
            insertGate(stmt, "Component_Chainlinks_Superior",   "ARMORSMITH", 50, 40);
            insertGate(stmt, "Component_Chainlinks_Pristine",   "ARMORSMITH", 75, 65);
            insertGate(stmt, "Component_Plating_Crude",         "ARMORSMITH", 1,  8);
            insertGate(stmt, "Component_Plating_Refined",       "ARMORSMITH", 25, 20);
            insertGate(stmt, "Component_Plating_Superior",      "ARMORSMITH", 50, 40);
            insertGate(stmt, "Component_Plating_Pristine",      "ARMORSMITH", 75, 65);

            // Armorsmith — Consumables (Repair Kits)
            insertGate(stmt, "Consumable_Repair_Kit_Crude",    "ARMORSMITH", 5,  10);
            insertGate(stmt, "Consumable_Repair_Kit_Refined",  "ARMORSMITH", 30, 25);
            insertGate(stmt, "Consumable_Repair_Kit_Superior", "ARMORSMITH", 55, 45);
            insertGate(stmt, "Consumable_Repair_Kit_Pristine", "ARMORSMITH", 80, 70);

            // ═══════════════════════════════════════════════════════
            // LEATHERWORKER — Components (Straps, Grip Wraps)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Straps_Crude",      "LEATHERWORKER", 1,  8);
            insertGate(stmt, "Component_Straps_Refined",    "LEATHERWORKER", 25, 20);
            insertGate(stmt, "Component_Straps_Superior",   "LEATHERWORKER", 50, 40);
            insertGate(stmt, "Component_Straps_Pristine",   "LEATHERWORKER", 75, 65);
            insertGate(stmt, "Component_GripWrap_Crude",    "LEATHERWORKER", 1,  8);
            insertGate(stmt, "Component_GripWrap_Refined",  "LEATHERWORKER", 25, 20);
            insertGate(stmt, "Component_GripWrap_Superior", "LEATHERWORKER", 50, 40);
            insertGate(stmt, "Component_GripWrap_Pristine", "LEATHERWORKER", 75, 65);

            // ═══════════════════════════════════════════════════════
            // TAILOR — Components (Thread, Lining)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Thread_Crude",      "TAILOR", 1,  8);
            insertGate(stmt, "Component_Thread_Refined",    "TAILOR", 25, 20);
            insertGate(stmt, "Component_Thread_Superior",   "TAILOR", 50, 40);
            insertGate(stmt, "Component_Thread_Pristine",   "TAILOR", 75, 65);
            insertGate(stmt, "Component_Lining_Crude",      "TAILOR", 1,  8);
            insertGate(stmt, "Component_Lining_Refined",    "TAILOR", 25, 20);
            insertGate(stmt, "Component_Lining_Superior",   "TAILOR", 50, 40);
            insertGate(stmt, "Component_Lining_Pristine",   "TAILOR", 75, 65);

            // ═══════════════════════════════════════════════════════
            // ALCHEMIST — Components (Flux, Tanning Solution, Dyes, Oil)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Flux_Crude",                "ALCHEMIST", 1,  8);
            insertGate(stmt, "Component_Flux_Refined",              "ALCHEMIST", 25, 20);
            insertGate(stmt, "Component_Flux_Superior",             "ALCHEMIST", 50, 40);
            insertGate(stmt, "Component_Flux_Pristine",             "ALCHEMIST", 75, 65);
            insertGate(stmt, "Component_Tanning_Solution_Crude",    "ALCHEMIST", 1,  8);
            insertGate(stmt, "Component_Tanning_Solution_Refined",  "ALCHEMIST", 25, 20);
            insertGate(stmt, "Component_Tanning_Solution_Superior", "ALCHEMIST", 50, 40);
            insertGate(stmt, "Component_Tanning_Solution_Pristine", "ALCHEMIST", 75, 65);
            insertGate(stmt, "Component_Dye_Red",                   "ALCHEMIST", 5,  10);
            insertGate(stmt, "Component_Dye_Blue",                  "ALCHEMIST", 5,  10);
            insertGate(stmt, "Component_Dye_Yellow",                "ALCHEMIST", 5,  10);
            insertGate(stmt, "Component_Dye_Green",                 "ALCHEMIST", 10, 12);
            insertGate(stmt, "Component_Dye_Purple",                "ALCHEMIST", 30, 25);
            insertGate(stmt, "Component_Dye_Black",                 "ALCHEMIST", 55, 45);
            insertGate(stmt, "Component_Preserving_Oil",            "ALCHEMIST", 10, 12);

            // Alchemist — Consumables (Weapon Oils, Armor Polish)
            insertGate(stmt, "Consumable_Weapon_Oil_Crude",    "ALCHEMIST", 10, 12);
            insertGate(stmt, "Consumable_Weapon_Oil_Refined",  "ALCHEMIST", 30, 25);
            insertGate(stmt, "Consumable_Weapon_Oil_Superior", "ALCHEMIST", 55, 45);
            insertGate(stmt, "Consumable_Weapon_Oil_Pristine", "ALCHEMIST", 80, 70);
            insertGate(stmt, "Consumable_Armor_Polish_Crude",    "ALCHEMIST", 10, 12);
            insertGate(stmt, "Consumable_Armor_Polish_Refined",  "ALCHEMIST", 30, 25);
            insertGate(stmt, "Consumable_Armor_Polish_Superior", "ALCHEMIST", 55, 45);
            insertGate(stmt, "Consumable_Armor_Polish_Pristine", "ALCHEMIST", 80, 70);

            // ═══════════════════════════════════════════════════════
            // COOK — Components (Rendered Fat, Purified Water, Yeast)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Rendered_Fat",    "COOK", 1,  8);
            insertGate(stmt, "Component_Purified_Water",  "COOK", 1,  8);
            insertGate(stmt, "Component_Yeast_Culture",   "COOK", 10, 15);

            // Cook — Consumables (Feasts)
            insertGate(stmt, "Consumable_Feast_Crude",    "COOK", 15, 20);
            insertGate(stmt, "Consumable_Feast_Refined",  "COOK", 35, 40);
            insertGate(stmt, "Consumable_Feast_Superior", "COOK", 55, 60);
            insertGate(stmt, "Consumable_Feast_Pristine", "COOK", 80, 85);

            // ═══════════════════════════════════════════════════════
            // CARPENTER — Components (Handles, Vials, Runewood, Platter)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Handle_Crude",      "CARPENTER", 1,  8);
            insertGate(stmt, "Component_Handle_Refined",    "CARPENTER", 25, 20);
            insertGate(stmt, "Component_Handle_Superior",   "CARPENTER", 50, 40);
            insertGate(stmt, "Component_Handle_Pristine",   "CARPENTER", 75, 65);
            insertGate(stmt, "Component_Vial_Crude",        "CARPENTER", 1,  8);
            insertGate(stmt, "Component_Vial_Refined",      "CARPENTER", 25, 20);
            insertGate(stmt, "Component_Vial_Superior",     "CARPENTER", 50, 40);
            insertGate(stmt, "Component_Vial_Pristine",     "CARPENTER", 75, 65);
            insertGate(stmt, "Component_Runewood_Blank",    "CARPENTER", 40, 45);
            insertGate(stmt, "Component_Serving_Platter",   "CARPENTER", 20, 25);

            // Carpenter — Consumables (Traps)
            insertGate(stmt, "Consumable_Trap_Snare",     "CARPENTER", 20, 25);
            insertGate(stmt, "Consumable_Trap_Spike",     "CARPENTER", 30, 35);
            insertGate(stmt, "Consumable_Trap_Explosive", "CARPENTER", 50, 55);

            // ═══════════════════════════════════════════════════════
            // ENCHANTER — Components (Arcane Ink, Catalyst Crystal, Runes)
            // ═══════════════════════════════════════════════════════
            insertGate(stmt, "Component_Arcane_Ink_Crude",    "ENCHANTER", 1,  8);
            insertGate(stmt, "Component_Arcane_Ink_Refined",  "ENCHANTER", 25, 20);
            insertGate(stmt, "Component_Arcane_Ink_Superior", "ENCHANTER", 50, 40);
            insertGate(stmt, "Component_Arcane_Ink_Pristine", "ENCHANTER", 75, 65);
            insertGate(stmt, "Component_Catalyst_Crystal",    "ENCHANTER", 65, 70);
            insertGate(stmt, "Component_Rune_Sharpness",      "ENCHANTER", 70, 80);
            insertGate(stmt, "Component_Rune_Warding",        "ENCHANTER", 70, 80);
            insertGate(stmt, "Component_Rune_Vitality",       "ENCHANTER", 70, 80);
            insertGate(stmt, "Component_Rune_Power",          "ENCHANTER", 75, 90);

            // Enchanter — Consumables (Scrolls)
            insertGate(stmt, "Consumable_Scroll_Fireball",  "ENCHANTER", 35, 40);
            insertGate(stmt, "Consumable_Scroll_Frostbolt", "ENCHANTER", 35, 40);
            insertGate(stmt, "Consumable_Scroll_Lightning", "ENCHANTER", 40, 45);
            insertGate(stmt, "Consumable_Scroll_Heal",      "ENCHANTER", 40, 45);
            insertGate(stmt, "Consumable_Scroll_Haste",     "ENCHANTER", 60, 65);
            insertGate(stmt, "Consumable_Scroll_Shield",    "ENCHANTER", 60, 65);

            stmt.executeBatch();

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

    private void insertGate(PreparedStatement stmt, String outputId, String profession,
                            int level, int xp) throws SQLException {
        stmt.setString(1, outputId);
        stmt.setString(2, profession);
        stmt.setInt(3, level);
        stmt.setInt(4, xp);
        stmt.addBatch();
    }
}
