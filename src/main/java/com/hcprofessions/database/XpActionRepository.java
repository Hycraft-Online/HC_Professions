package com.hcprofessions.database;

import com.hcprofessions.models.ActionType;
import com.hcprofessions.models.SkillTarget;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class XpActionRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-XpAction");

    private final DatabaseManager databaseManager;

    public XpActionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<XpRewardRow> loadAll() {
        String sql = "SELECT event, identifier, is_pattern, skill_type, skill_name, xp_amount, min_level FROM skill_xp_rewards WHERE enabled = true";
        List<XpRewardRow> rows = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                ActionType event = ActionType.fromString(rs.getString("event"));
                SkillTarget skillType = SkillTarget.fromString(rs.getString("skill_type"));
                if (event == null || skillType == null) continue;

                rows.add(new XpRewardRow(
                    event,
                    rs.getString("identifier"),
                    rs.getBoolean("is_pattern"),
                    skillType,
                    rs.getString("skill_name"),
                    rs.getInt("xp_amount"),
                    rs.getInt("min_level")
                ));
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load skill_xp_rewards: " + e.getMessage());
        }

        LOGGER.at(Level.INFO).log("Loaded " + rows.size() + " XP action reward entries");
        return rows;
    }

    public void seedDefaults() {
        try (Connection conn = databaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM skill_xp_rewards")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    LOGGER.at(Level.INFO).log("skill_xp_rewards already has data, skipping seed");
                    return;
                }
            }

            String insertSql = """
                INSERT INTO skill_xp_rewards (event, identifier, is_pattern, skill_type, skill_name, xp_amount, min_level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                // Tempering: wildcard match for all items
                insertRow(stmt, "TEMPER", "*", true, "PROFESSION", null, 25, 0);

                // Builder profession — XP from block placement during construction
                insertRow(stmt, "PLACE", "*", true, "PROFESSION", null, 2, 0);
                insertRow(stmt, "PLACE", "construction_complete", false, "PROFESSION", null, 100, 0);

                // Mob kills -> profession XP (player's current profession)
                insertRow(stmt, "KILL", "%skeleton%", true, "PROFESSION", null, 15, 0);
                insertRow(stmt, "KILL", "%zombie%", true, "PROFESSION", null, 15, 0);
                insertRow(stmt, "KILL", "%spider%", true, "PROFESSION", null, 10, 0);
                insertRow(stmt, "KILL", "%ghoul%", true, "PROFESSION", null, 25, 0);
                insertRow(stmt, "KILL", "%trork%", true, "PROFESSION", null, 20, 0);
                insertRow(stmt, "KILL", "%golem%", true, "PROFESSION", null, 40, 0);
                insertRow(stmt, "KILL", "%wolf%", true, "PROFESSION", null, 10, 0);
                insertRow(stmt, "KILL", "%boar%", true, "PROFESSION", null, 8, 0);

                // Skinning — animal kills -> SKINNING tradeskill XP
                insertRow(stmt, "KILL", "%boar%",       true, "TRADESKILL", "SKINNING", 8, 0);
                insertRow(stmt, "KILL", "%wolf%",       true, "TRADESKILL", "SKINNING", 10, 0);
                insertRow(stmt, "KILL", "%deer%",       true, "TRADESKILL", "SKINNING", 8, 0);
                insertRow(stmt, "KILL", "%bear%",       true, "TRADESKILL", "SKINNING", 18, 0);
                insertRow(stmt, "KILL", "%cow%",        true, "TRADESKILL", "SKINNING", 8, 0);
                insertRow(stmt, "KILL", "%sheep%",      true, "TRADESKILL", "SKINNING", 8, 0);
                insertRow(stmt, "KILL", "%ram%",        true, "TRADESKILL", "SKINNING", 10, 0);
                insertRow(stmt, "KILL", "%raptor%",     true, "TRADESKILL", "SKINNING", 15, 0);
                insertRow(stmt, "KILL", "%spider%",     true, "TRADESKILL", "SKINNING", 10, 0);
                insertRow(stmt, "KILL", "%scorpion%",   true, "TRADESKILL", "SKINNING", 12, 0);
                insertRow(stmt, "KILL", "%sabertooth%", true, "TRADESKILL", "SKINNING", 25, 0);
                insertRow(stmt, "KILL", "%hyena%",      true, "TRADESKILL", "SKINNING", 12, 0);
                insertRow(stmt, "KILL", "%lion%",       true, "TRADESKILL", "SKINNING", 20, 0);
                insertRow(stmt, "KILL", "%crocodile%",  true, "TRADESKILL", "SKINNING", 18, 0);
                insertRow(stmt, "KILL", "%yeti%",       true, "TRADESKILL", "SKINNING", 30, 0);
                insertRow(stmt, "KILL", "%mammoth%",    true, "TRADESKILL", "SKINNING", 35, 0);

                // Pickups -> tradeskill XP (specific tradeskill)
                // FISHING — tiered by species rarity (from fishing trap drop tables)
                // Common fish (drop weight 100)
                insertRow(stmt, "PICKUP", "Fish_Minnow_Item",   false, "TRADESKILL", "FISHING", 10, 0);
                insertRow(stmt, "PICKUP", "Fish_Bluegill_Item",  false, "TRADESKILL", "FISHING", 10, 0);
                insertRow(stmt, "PICKUP", "Fish_Catfish_Item",   false, "TRADESKILL", "FISHING", 10, 0);
                // Uncommon fish (drop weight 50)
                insertRow(stmt, "PICKUP", "Fish_Tang_Blue_Item",       false, "TRADESKILL", "FISHING", 20, 0);
                insertRow(stmt, "PICKUP", "Fish_Tang_Lemon_Peel_Item", false, "TRADESKILL", "FISHING", 20, 0);
                insertRow(stmt, "PICKUP", "Fish_Tang_Sailfin_Item",    false, "TRADESKILL", "FISHING", 20, 0);
                insertRow(stmt, "PICKUP", "Fish_Tang_Chevron_Item",    false, "TRADESKILL", "FISHING", 20, 0);
                // Rare fish (drop weight 10)
                insertRow(stmt, "PICKUP", "Fish_Trout_Rainbow_Item", false, "TRADESKILL", "FISHING", 35, 0);
                insertRow(stmt, "PICKUP", "Fish_Salmon_Item",         false, "TRADESKILL", "FISHING", 35, 0);
                insertRow(stmt, "PICKUP", "Fish_Clownfish_Item",      false, "TRADESKILL", "FISHING", 35, 0);
                insertRow(stmt, "PICKUP", "Fish_Pufferfish_Item",     false, "TRADESKILL", "FISHING", 35, 0);
                // Epic fish (drop weight 5)
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Red_Item",    false, "TRADESKILL", "FISHING", 50, 0);
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Blue_Item",   false, "TRADESKILL", "FISHING", 50, 0);
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Cyan_Item",   false, "TRADESKILL", "FISHING", 50, 0);
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Yellow_Item", false, "TRADESKILL", "FISHING", 50, 0);
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Green_Item",  false, "TRADESKILL", "FISHING", 50, 0);
                // Monster fish (drop weight 0.01-0.1)
                insertRow(stmt, "PICKUP", "Fish_Shark_Hammerhead_Item",    false, "TRADESKILL", "FISHING", 100, 0);
                insertRow(stmt, "PICKUP", "Fish_Whale_Humpback_Item",      false, "TRADESKILL", "FISHING", 100, 0);
                insertRow(stmt, "PICKUP", "Fish_Jellyfish_Man_Of_War_Item",false, "TRADESKILL", "FISHING", 100, 0);
                insertRow(stmt, "PICKUP", "Fish_Snapjaw_Item",             false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Frostgill_Item",           false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Lobster_Item",             false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Crab_Item",                false, "TRADESKILL", "FISHING", 60, 0);
                insertRow(stmt, "PICKUP", "Fish_Eel_Moray_Item",           false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Pike_Item",                false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Piranha_Item",             false, "TRADESKILL", "FISHING", 60, 0);
                insertRow(stmt, "PICKUP", "Fish_Piranha_Black_Item",       false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Trilobite_Item",           false, "TRADESKILL", "FISHING", 60, 0);
                insertRow(stmt, "PICKUP", "Fish_Trilobite_Black_Item",     false, "TRADESKILL", "FISHING", 75, 0);
                insertRow(stmt, "PICKUP", "Fish_Shellfish_Lava_Item",      false, "TRADESKILL", "FISHING", 75, 0);

                // Gathering -> tradeskill XP (block breaking)
                // Mining ores
                insertRow(stmt, "GATHER", "ore_copper%", true, "TRADESKILL", "MINING", 7, 0);
                insertRow(stmt, "GATHER", "ore_iron%", true, "TRADESKILL", "MINING", 13, 0);
                insertRow(stmt, "GATHER", "ore_silver%", true, "TRADESKILL", "MINING", 24, 0);
                insertRow(stmt, "GATHER", "ore_gold%", true, "TRADESKILL", "MINING", 32, 0);
                insertRow(stmt, "GATHER", "ore_cobalt%", true, "TRADESKILL", "MINING", 37, 0);
                insertRow(stmt, "GATHER", "ore_thorium%", true, "TRADESKILL", "MINING", 37, 0);
                insertRow(stmt, "GATHER", "ore_adamantite%", true, "TRADESKILL", "MINING", 50, 0);
                insertRow(stmt, "GATHER", "ore_mithril%", true, "TRADESKILL", "MINING", 60, 0);
                insertRow(stmt, "GATHER", "ore_onyxium%", true, "TRADESKILL", "MINING", 60, 0);
                insertRow(stmt, "GATHER", "ore_prisma%", true, "TRADESKILL", "MINING", 60, 0);
                // Woodcutting — Zone 1 (starter: grasslands) trees
                insertRow(stmt, "GATHER", "wood_oak_trunk%", true, "TRADESKILL", "WOODCUTTING", 10, 0);
                insertRow(stmt, "GATHER", "wood_birch_trunk%", true, "TRADESKILL", "WOODCUTTING", 10, 0);
                insertRow(stmt, "GATHER", "wood_beech_trunk%", true, "TRADESKILL", "WOODCUTTING", 10, 0);
                // Woodcutting — Zone 2 (mid: desert/savanna) trees
                insertRow(stmt, "GATHER", "wood_gumboab_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                insertRow(stmt, "GATHER", "wood_dry_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                insertRow(stmt, "GATHER", "wood_sallow_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                insertRow(stmt, "GATHER", "wood_palm_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                insertRow(stmt, "GATHER", "wood_palo_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                insertRow(stmt, "GATHER", "wood_bottletree_trunk%", true, "TRADESKILL", "WOODCUTTING", 18, 0);
                // Woodcutting — Zone 3 (mid-late: frozen/mountain) trees
                insertRow(stmt, "GATHER", "wood_fir_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                insertRow(stmt, "GATHER", "wood_redwood_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                insertRow(stmt, "GATHER", "wood_cedar_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                insertRow(stmt, "GATHER", "wood_ash_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                insertRow(stmt, "GATHER", "wood_maple_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                insertRow(stmt, "GATHER", "wood_aspen_trunk%", true, "TRADESKILL", "WOODCUTTING", 25, 0);
                // Woodcutting — Zone 4 (late: volcanic/corrupted) trees
                insertRow(stmt, "GATHER", "wood_petrified_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_poisoned_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_fire_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_crystal_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_burnt_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_ice_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_stormbark_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                insertRow(stmt, "GATHER", "wood_amber_trunk%", true, "TRADESKILL", "WOODCUTTING", 35, 0);
                // Woodcutting — Exotic/jungle trees
                insertRow(stmt, "GATHER", "wood_jungle_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_bamboo_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_banyan_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_camphor_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_windwillow_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_wisteria%_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_spiral_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_azure_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                insertRow(stmt, "GATHER", "wood_fig%_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);
                // Woodcutting — Fern trunks (jungle/swamp)
                insertRow(stmt, "GATHER", "plant_fern_%_trunk%", true, "TRADESKILL", "WOODCUTTING", 22, 0);

                // Farming — crops (harvested as item pickup, own land only)
                insertRow(stmt, "PICKUP", "plant_crop_%", true, "TRADESKILL", "FARMING", 20, 0);

                // Herbalism — mushrooms (gathered via block break)
                insertRow(stmt, "GATHER", "%mushroom_common%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "GATHER", "%mushroom_cap%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "GATHER", "%mushroom_flatcap%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "GATHER", "%mushroom_shelve%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "GATHER", "%mushroom_block%", true, "TRADESKILL", "HERBALISM", 15, 0);
                insertRow(stmt, "GATHER", "%mushroom_glowing%", true, "TRADESKILL", "HERBALISM", 25, 0);
                insertRow(stmt, "GATHER", "%mushroom_boomshroom%", true, "TRADESKILL", "HERBALISM", 30, 0);
                insertRow(stmt, "GATHER", "%fungus%", true, "TRADESKILL", "HERBALISM", 15, 0);

                // Herbalism — flowers via item pickup
                insertRow(stmt, "PICKUP", "plant_flower_common%", true, "TRADESKILL", "HERBALISM", 8, 0);
                insertRow(stmt, "PICKUP", "plant_flower_bushy%", true, "TRADESKILL", "HERBALISM", 10, 0);
                insertRow(stmt, "PICKUP", "plant_flower_tall%", true, "TRADESKILL", "HERBALISM", 10, 0);
                insertRow(stmt, "PICKUP", "plant_flower_flax%", true, "TRADESKILL", "HERBALISM", 15, 0);
                insertRow(stmt, "PICKUP", "plant_flower_orchid%", true, "TRADESKILL", "HERBALISM", 18, 0);
                insertRow(stmt, "PICKUP", "plant_flower_water%", true, "TRADESKILL", "HERBALISM", 15, 0);
                insertRow(stmt, "PICKUP", "plant_flower_poisoned%", true, "TRADESKILL", "HERBALISM", 25, 0);
                insertRow(stmt, "PICKUP", "plant_flower_hemlock%", true, "TRADESKILL", "HERBALISM", 25, 0);
                insertRow(stmt, "PICKUP", "plant_sunflower%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "PICKUP", "plant_lavender%", true, "TRADESKILL", "HERBALISM", 12, 0);
                insertRow(stmt, "PICKUP", "plant_cactus_flower%", true, "TRADESKILL", "HERBALISM", 18, 0);

                // Herbalism — berries via item pickup
                insertRow(stmt, "PICKUP", "%berries%", true, "TRADESKILL", "HERBALISM", 20, 0);
                insertRow(stmt, "PICKUP", "%pinkberry%", true, "TRADESKILL", "HERBALISM", 30, 0);

                // Herbalism — health/mana/stamina plants (potion ingredients, also match plant_crop_% for farming)
                insertRow(stmt, "PICKUP", "plant_crop_health%", true, "TRADESKILL", "HERBALISM", 25, 0);
                insertRow(stmt, "PICKUP", "plant_crop_mana%", true, "TRADESKILL", "HERBALISM", 25, 0);
                insertRow(stmt, "PICKUP", "plant_crop_stamina%", true, "TRADESKILL", "HERBALISM", 25, 0);

                // Herbalism — wild fruit
                insertRow(stmt, "PICKUP", "plant_fruit_apple%", true, "TRADESKILL", "HERBALISM", 15, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_coconut%", true, "TRADESKILL", "HERBALISM", 18, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_mango%", true, "TRADESKILL", "HERBALISM", 18, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_azure%", true, "TRADESKILL", "HERBALISM", 22, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_spiral%", true, "TRADESKILL", "HERBALISM", 22, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_windwillow%", true, "TRADESKILL", "HERBALISM", 22, 0);
                insertRow(stmt, "PICKUP", "plant_fruit_poison%", true, "TRADESKILL", "HERBALISM", 25, 0);

                stmt.executeBatch();
                LOGGER.at(Level.INFO).log("Seeded default skill_xp_rewards entries");
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed skill_xp_rewards defaults: " + e.getMessage());
        }
    }

    private void insertRow(PreparedStatement stmt, String event, String identifier, boolean isPattern,
                           String skillType, String skillName, int xpAmount, int minLevel) throws SQLException {
        stmt.setString(1, event);
        stmt.setString(2, identifier);
        stmt.setBoolean(3, isPattern);
        stmt.setString(4, skillType);
        if (skillName != null) {
            stmt.setString(5, skillName);
        } else {
            stmt.setNull(5, java.sql.Types.VARCHAR);
        }
        stmt.setInt(6, xpAmount);
        stmt.setInt(7, minLevel);
        stmt.addBatch();
    }

    /**
     * Merges crafting XP rewards for new cross-profession components and consumables.
     * Uses ON CONFLICT DO NOTHING so it's safe to call repeatedly.
     */
    public void seedCraftingXpActions() {
        String insertSql = """
            INSERT INTO skill_xp_rewards (event, identifier, is_pattern, skill_type, skill_name, xp_amount, min_level)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event, identifier, skill_type, COALESCE(skill_name, ''))
            DO NOTHING
            """;

        int before = 0;
        int after = 0;

        try (Connection conn = databaseManager.getConnection()) {
            // Count existing
            try (Statement countStmt = conn.createStatement();
                 ResultSet rs = countStmt.executeQuery("SELECT COUNT(*) FROM skill_xp_rewards WHERE event = 'CRAFT'")) {
                if (rs.next()) before = rs.getInt(1);
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                // ═══════════════════════════════════════════════
                // WEAPONSMITH components — CRAFT -> PROFESSION XP
                // ═══════════════════════════════════════════════
                // Rivets (Crude=10, Refined=20, Superior=35, Pristine=55)
                insertRow(stmt, "CRAFT", "Component_Rivets_Crude",     false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Rivets_Refined",   false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Rivets_Superior",  false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Rivets_Pristine",  false, "PROFESSION", null, 55, 0);
                // Fittings
                insertRow(stmt, "CRAFT", "Component_Fittings_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Fittings_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Fittings_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Fittings_Pristine", false, "PROFESSION", null, 55, 0);

                // ═══════════════════════════════════════════════
                // ARMORSMITH components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Chainlinks_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Chainlinks_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Chainlinks_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Chainlinks_Pristine", false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Plating_Crude",       false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Plating_Refined",     false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Plating_Superior",    false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Plating_Pristine",    false, "PROFESSION", null, 55, 0);

                // ═══════════════════════════════════════════════
                // LEATHERWORKER components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Straps_Crude",       false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Straps_Refined",     false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Straps_Superior",    false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Straps_Pristine",    false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_GripWrap_Crude",     false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_GripWrap_Refined",   false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_GripWrap_Superior",  false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_GripWrap_Pristine",  false, "PROFESSION", null, 55, 0);

                // ═══════════════════════════════════════════════
                // TAILOR components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Thread_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Thread_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Thread_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Thread_Pristine", false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Lining_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Lining_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Lining_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Lining_Pristine", false, "PROFESSION", null, 55, 0);

                // ═══════════════════════════════════════════════
                // ALCHEMIST components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Flux_Crude",                   false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Flux_Refined",                 false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Flux_Superior",                false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Flux_Pristine",                false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Tanning_Solution_Crude",       false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Tanning_Solution_Refined",     false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Tanning_Solution_Superior",    false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Tanning_Solution_Pristine",    false, "PROFESSION", null, 55, 0);
                // Dyes (flat XP — no tiers)
                insertRow(stmt, "CRAFT", "Component_Dye_Red",    false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Dye_Blue",   false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Dye_Yellow", false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Dye_Green",  false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Dye_Purple", false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Dye_Black",  false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Component_Preserving_Oil", false, "PROFESSION", null, 20, 0);

                // ═══════════════════════════════════════════════
                // COOK components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Rendered_Fat",   false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Purified_Water", false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Yeast_Culture",  false, "PROFESSION", null, 15, 0);

                // ═══════════════════════════════════════════════
                // CARPENTER components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Handle_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Handle_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Handle_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Handle_Pristine", false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Vial_Crude",      false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Vial_Refined",    false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Vial_Superior",   false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Vial_Pristine",   false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Runewood_Blank",  false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Component_Serving_Platter", false, "PROFESSION", null, 25, 0);

                // ═══════════════════════════════════════════════
                // ENCHANTER components
                // ═══════════════════════════════════════════════
                insertRow(stmt, "CRAFT", "Component_Arcane_Ink_Crude",    false, "PROFESSION", null, 10, 0);
                insertRow(stmt, "CRAFT", "Component_Arcane_Ink_Refined",  false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Component_Arcane_Ink_Superior", false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Component_Arcane_Ink_Pristine", false, "PROFESSION", null, 55, 0);
                insertRow(stmt, "CRAFT", "Component_Catalyst_Crystal",    false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Component_Rune_Sharpness",      false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Component_Rune_Warding",        false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Component_Rune_Vitality",       false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Component_Rune_Power",          false, "PROFESSION", null, 40, 0);

                // ═══════════════════════════════════════════════
                // CONSUMABLES — crafted by various professions
                // ═══════════════════════════════════════════════
                // Weapon Oils (Alchemist)
                insertRow(stmt, "CRAFT", "Consumable_Weapon_Oil_Crude",    false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Consumable_Weapon_Oil_Refined",  false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Consumable_Weapon_Oil_Superior", false, "PROFESSION", null, 50, 0);
                insertRow(stmt, "CRAFT", "Consumable_Weapon_Oil_Pristine", false, "PROFESSION", null, 75, 0);
                // Armor Polish (Alchemist)
                insertRow(stmt, "CRAFT", "Consumable_Armor_Polish_Crude",    false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Consumable_Armor_Polish_Refined",  false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Consumable_Armor_Polish_Superior", false, "PROFESSION", null, 50, 0);
                insertRow(stmt, "CRAFT", "Consumable_Armor_Polish_Pristine", false, "PROFESSION", null, 75, 0);
                // Sharpening Stones (Weaponsmith)
                insertRow(stmt, "CRAFT", "Consumable_Sharpening_Stone_Crude",    false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Consumable_Sharpening_Stone_Refined",  false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Consumable_Sharpening_Stone_Superior", false, "PROFESSION", null, 50, 0);
                insertRow(stmt, "CRAFT", "Consumable_Sharpening_Stone_Pristine", false, "PROFESSION", null, 75, 0);
                // Repair Kits (Armorsmith)
                insertRow(stmt, "CRAFT", "Consumable_Repair_Kit_Crude",    false, "PROFESSION", null, 15, 0);
                insertRow(stmt, "CRAFT", "Consumable_Repair_Kit_Refined",  false, "PROFESSION", null, 30, 0);
                insertRow(stmt, "CRAFT", "Consumable_Repair_Kit_Superior", false, "PROFESSION", null, 50, 0);
                insertRow(stmt, "CRAFT", "Consumable_Repair_Kit_Pristine", false, "PROFESSION", null, 75, 0);
                // Scrolls (Enchanter)
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Fireball",  false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Frostbolt", false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Lightning", false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Heal",      false, "PROFESSION", null, 45, 0);
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Haste",     false, "PROFESSION", null, 45, 0);
                insertRow(stmt, "CRAFT", "Consumable_Scroll_Shield",    false, "PROFESSION", null, 45, 0);
                // Feasts (Cook)
                insertRow(stmt, "CRAFT", "Consumable_Feast_Crude",    false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Consumable_Feast_Refined",  false, "PROFESSION", null, 40, 0);
                insertRow(stmt, "CRAFT", "Consumable_Feast_Superior", false, "PROFESSION", null, 65, 0);
                insertRow(stmt, "CRAFT", "Consumable_Feast_Pristine", false, "PROFESSION", null, 90, 0);
                // Traps (Leatherworker)
                insertRow(stmt, "CRAFT", "Consumable_Trap_Snare",     false, "PROFESSION", null, 20, 0);
                insertRow(stmt, "CRAFT", "Consumable_Trap_Spike",     false, "PROFESSION", null, 35, 0);
                insertRow(stmt, "CRAFT", "Consumable_Trap_Explosive", false, "PROFESSION", null, 55, 0);

                // ═══════════════════════════════════════════════
                // GATHERING INGREDIENTS — pickup XP for new materials
                // ═══════════════════════════════════════════════
                // Ore Dust drops from mining -> Mining tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Ore_Dust", false, "TRADESKILL", "MINING", 5, 0);
                // Resin from woodcutting -> Woodcutting tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Resin", false, "TRADESKILL", "WOODCUTTING", 5, 0);
                // Sand from mining (desert) -> Mining tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Sand", false, "TRADESKILL", "MINING", 5, 0);
                // Raw Meat from skinning -> Skinning tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Raw_Meat", false, "TRADESKILL", "SKINNING", 5, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Raw_Meat_Prime", false, "TRADESKILL", "SKINNING", 12, 0);
                // Fish Oil from fishing -> Fishing tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Fish_Oil", false, "TRADESKILL", "FISHING", 8, 0);
                // Herbs from herbalism -> Herbalism tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Herb_Common",   false, "TRADESKILL", "HERBALISM", 5, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Herb_Uncommon", false, "TRADESKILL", "HERBALISM", 10, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Herb_Rare",     false, "TRADESKILL", "HERBALISM", 18, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Herb_Epic",     false, "TRADESKILL", "HERBALISM", 30, 0);
                // Pigments from herbalism -> Herbalism tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Pigment_Red",    false, "TRADESKILL", "HERBALISM", 8, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Pigment_Blue",   false, "TRADESKILL", "HERBALISM", 8, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Pigment_Yellow", false, "TRADESKILL", "HERBALISM", 8, 0);
                insertRow(stmt, "PICKUP", "Ingredient_Pigment_Green",  false, "TRADESKILL", "HERBALISM", 8, 0);
                // Arcane Dust from mining (crystal nodes) -> Mining tradeskill XP
                insertRow(stmt, "PICKUP", "Ingredient_Arcane_Dust", false, "TRADESKILL", "MINING", 15, 0);

                stmt.executeBatch();
            }

            // Count after
            try (Statement countStmt = conn.createStatement();
                 ResultSet rs = countStmt.executeQuery("SELECT COUNT(*) FROM skill_xp_rewards WHERE event = 'CRAFT'")) {
                if (rs.next()) after = rs.getInt(1);
            }

            int added = after - before;
            if (added > 0) {
                LOGGER.at(Level.INFO).log("Seeded %d new crafting/gathering XP action entries (total CRAFT: %d)", added, after);
            } else {
                LOGGER.at(Level.INFO).log("Crafting XP actions already up to date (%d CRAFT entries)", after);
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed crafting XP actions: " + e.getMessage());
        }
    }

    public record XpRewardRow(
        ActionType event,
        String identifier,
        boolean isPattern,
        SkillTarget skillType,
        String skillName,
        int xpAmount,
        int minLevel
    ) {}
}
