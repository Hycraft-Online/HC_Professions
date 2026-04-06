package com.hcprofessions.database;

import com.hcprofessions.models.SkillDefinition;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DefinitionRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-DefRepo");
    private final DatabaseManager db;

    public DefinitionRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<SkillDefinition> loadByType(String type) {
        List<SkillDefinition> defs = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM prof_definitions WHERE type = ? AND enabled = true ORDER BY sort_order, id")) {
            stmt.setString(1, type);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    defs.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load definitions for type " + type + ": " + e.getMessage());
        }
        return defs;
    }

    public SkillDefinition getById(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM prof_definitions WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load definition " + id + ": " + e.getMessage());
        }
        return null;
    }

    public void seedDefaults() {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO prof_definitions (id, type, display_name, color_hex, description, enabled, sort_order) " +
                 "VALUES (?, ?, ?, ?, ?, true, ?) ON CONFLICT (id) DO NOTHING")) {

            Object[][] defaults = {
                // Tradeskills (gathering)
                {"MINING",       "tradeskill", "Mining",       "#B4783C", "Extract ores and minerals",                           0},
                {"WOODCUTTING",  "tradeskill", "Woodcutting",  "#64B43C", "Harvest wood and timber",                             1},
                {"FARMING",      "tradeskill", "Farming",      "#3CB43C", "Grow and harvest crops",                              2},
                {"HERBALISM",    "tradeskill", "Herbalism",    "#8B5CF6", "Gathering wild herbs, flowers, and potion ingredients", 3},
                {"SKINNING",     "tradeskill", "Skinning",     "#C8966E", "Harvest hides and leather from animals",              4},
                {"FISHING",      "tradeskill", "Fishing",      "#3C78C8", "Catch fish and aquatic life",                         5},
                // Professions (crafting)
                {"WEAPONSMITH",  "profession", "Bladesmith",   "#C83232", "Forge powerful weapons",                              0},
                {"ARMORSMITH",   "profession", "Platesmith",   "#3278C8", "Craft protective plate armor",                        1},
                {"ALCHEMIST",    "profession", "Alchemist",    "#8B5CF6", "Brew potions and bombs from herbs",                   2},
                {"COOK",         "profession", "Cook",         "#E89040", "Prepare food and restorative meals",                  3},
                {"LEATHERWORKER","profession", "Leatherworker","#8B6914", "Craft leather armor and accessories",                 4},
                {"CARPENTER",    "profession", "Carpenter",    "#B47832", "Build furniture and wooden structures",               5},
                {"TAILOR",       "profession", "Tailor",       "#C864C8", "Weave cloth armor and garments",                      6},
                {"ENCHANTER",    "profession", "Enchanter",    "#6432C8", "Imbue items with arcane power",                       7},
                {"BUILDER",      "profession", "Builder",      "#4A9E5C", "Construct buildings and structures",                  8},
            };

            for (Object[] row : defaults) {
                stmt.setString(1, (String) row[0]);
                stmt.setString(2, (String) row[1]);
                stmt.setString(3, (String) row[2]);
                stmt.setString(4, (String) row[3]);
                stmt.setString(5, (String) row[4]);
                stmt.setInt(6, (int) row[5]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            LOGGER.at(Level.INFO).log("Seeded definition defaults");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed definitions: " + e.getMessage());
        }
    }

    /**
     * Disables all definitions of the given type except those in the keepEnabled list.
     * Used to temporarily restrict available professions for release.
     */
    public void disableAllExcept(String type, List<String> keepEnabled) {
        try (Connection conn = db.getConnection()) {
            // Disable all of this type
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE prof_definitions SET enabled = false WHERE type = ?")) {
                stmt.setString(1, type);
                stmt.executeUpdate();
            }
            // Re-enable the ones we want in a single batch query
            if (!keepEnabled.isEmpty()) {
                String[] ids = keepEnabled.toArray(new String[0]);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE prof_definitions SET enabled = true WHERE type = ? AND id = ANY(?)")) {
                    stmt.setString(1, type);
                    stmt.setArray(2, conn.createArrayOf("text", ids));
                    stmt.executeUpdate();
                }
            }
            LOGGER.at(Level.INFO).log("Disabled all %s definitions except: %s", type, keepEnabled);
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to update enabled status: " + e.getMessage());
        }
    }

    private SkillDefinition mapRow(ResultSet rs) throws SQLException {
        return new SkillDefinition(
            rs.getString("id"),
            rs.getString("type"),
            rs.getString("display_name"),
            rs.getString("color_hex"),
            rs.getString("description"),
            rs.getBoolean("enabled"),
            rs.getInt("sort_order")
        );
    }
}
