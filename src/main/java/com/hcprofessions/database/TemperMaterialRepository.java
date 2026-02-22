package com.hcprofessions.database;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Repository for material-tier level requirements used by the tempering bench.
 * Each material tier maps to a minimum profession level required to temper items of that tier.
 */
public class TemperMaterialRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-TemperMat");

    private final DatabaseManager databaseManager;

    public TemperMaterialRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Load all enabled material requirements as a map of material -> required level.
     */
    public Map<String, Integer> loadAll() {
        Map<String, Integer> requirements = new HashMap<>();

        String sql = "SELECT material, required_level FROM prof_temper_material_requirements WHERE enabled = true";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                requirements.put(rs.getString("material"), rs.getInt("required_level"));
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load temper material requirements: " + e.getMessage());
        }

        return requirements;
    }

    private static final Object[][] DEFAULTS = {
        {"I",     1,  0},   // iLvl 1-5
        {"II",    1,  1},   // iLvl 6-10
        {"III",   5,  2},   // iLvl 11-15
        {"IV",   10,  3},   // iLvl 16-20
        {"V",    20,  4},   // iLvl 21-25
        {"VI",   30,  5},   // iLvl 26-30
        {"VII",  40,  6},   // iLvl 31-35
        {"VIII", 55,  7},   // iLvl 36-40
        {"IX",   70,  8},   // iLvl 41-45
        {"X",    85,  9},   // iLvl 46-50
    };

    /**
     * Seed default material requirements. Migrates old named tiers (Crude, Iron, etc.)
     * to Roman numeral tiers (I-X) if needed.
     */
    public void seedDefaults() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if old named tiers exist and need migration
            ResultSet oldCheck = stmt.executeQuery(
                "SELECT count(*) FROM prof_temper_material_requirements WHERE material = 'Crude'");
            oldCheck.next();
            boolean hasOldTiers = oldCheck.getInt(1) > 0;

            if (hasOldTiers) {
                // Migrate: delete old named tiers, insert new Roman numeral tiers
                stmt.executeUpdate("DELETE FROM prof_temper_material_requirements " +
                    "WHERE material IN ('Crude','Copper','Bronze','Iron','Steel','Cobalt'," +
                    "'Thorium','Adamantite','Mithril','Onyxium','Prisma','Unique')");
                LOGGER.at(Level.INFO).log("Migrated old named temper tiers to Roman numeral tiers");
            }

            // Insert new defaults (ON CONFLICT skips if already present)
            String insertSql = """
                INSERT INTO prof_temper_material_requirements (material, required_level, sort_order)
                VALUES (?, ?, ?)
                ON CONFLICT (material) DO NOTHING
                """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Object[] row : DEFAULTS) {
                    ps.setString(1, (String) row[0]);
                    ps.setInt(2, (int) row[1]);
                    ps.setInt(3, (int) row[2]);
                    ps.addBatch();
                }

                int[] results = ps.executeBatch();
                int inserted = 0;
                for (int r : results) if (r > 0) inserted++;
                if (inserted > 0) {
                    LOGGER.at(Level.INFO).log("Seeded %d temper material requirements", inserted);
                } else {
                    LOGGER.at(Level.INFO).log("Temper material requirements already up to date");
                }
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed temper material requirements: " + e.getMessage());
        }
    }
}
