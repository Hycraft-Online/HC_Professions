package com.hcprofessions.database;

import com.hcprofessions.models.QualityTierDefinition;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class QualityTierRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-TierRepo");
    private final DatabaseManager db;

    public QualityTierRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<QualityTierDefinition> loadAll() {
        List<QualityTierDefinition> tiers = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM prof_quality_tiers ORDER BY sort_order, min_level")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tiers.add(new QualityTierDefinition(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("min_level"),
                    rs.getInt("max_level"),
                    rs.getString("max_rarity"),
                    rs.getInt("min_affixes"),
                    rs.getInt("max_affixes"),
                    rs.getDouble("bonus_affix_chance"),
                    rs.getInt("ilvl_variance"),
                    rs.getInt("sort_order")
                ));
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load quality tiers: " + e.getMessage());
        }
        return tiers;
    }

    public void seedDefaults() {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO prof_quality_tiers (name, min_level, max_level, max_rarity, min_affixes, max_affixes, bonus_affix_chance, ilvl_variance, sort_order) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (name) DO NOTHING")) {

            Object[][] defaults = {
                {"Novice",      1,   14,  "Common",    0, 0, 0.0,  0,  0},
                {"Apprentice",  15,  29,  "Uncommon",  0, 1, 0.10, 2,  1},
                {"Journeyman",  30,  49,  "Rare",      0, 1, 0.30, 3,  2},
                {"Expert",      50,  69,  "Rare",      1, 1, 1.00, 5,  3},
                {"Artisan",     70,  84,  "Epic",      1, 2, 0.25, 8,  4},
                {"Master",      85,  99,  "Epic",      1, 2, 0.50, 10, 5},
                {"Grandmaster", 100, 100, "Legendary", 2, 2, 0.05, 15, 6},
            };

            for (Object[] row : defaults) {
                stmt.setString(1, (String) row[0]);
                stmt.setInt(2, (int) row[1]);
                stmt.setInt(3, (int) row[2]);
                stmt.setString(4, (String) row[3]);
                stmt.setInt(5, (int) row[4]);
                stmt.setInt(6, (int) row[5]);
                stmt.setDouble(7, (double) row[6]);
                stmt.setInt(8, (int) row[7]);
                stmt.setInt(9, (int) row[8]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            LOGGER.at(Level.INFO).log("Seeded quality tier defaults");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed quality tiers: " + e.getMessage());
        }
    }
}
