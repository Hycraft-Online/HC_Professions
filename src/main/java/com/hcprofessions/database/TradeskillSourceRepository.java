package com.hcprofessions.database;

import com.hcprofessions.config.TradeskillSourceConfig;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class TradeskillSourceRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-TradeskillSrc");

    private final DatabaseManager databaseManager;

    public TradeskillSourceRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public TradeskillSourceConfig loadCache() {
        String sql = "SELECT pattern, match_type, tradeskill, xp_amount, min_level FROM prof_tradeskill_sources WHERE enabled = true";

        TradeskillSourceConfig.Builder builder = new TradeskillSourceConfig.Builder();
        int count = 0;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                builder.addEntry(
                    rs.getString("pattern"),
                    rs.getString("match_type"),
                    rs.getString("tradeskill"),
                    rs.getInt("xp_amount"),
                    rs.getInt("min_level")
                );
                count++;
            }

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load tradeskill source config: " + e.getMessage());
        }

        LOGGER.at(Level.INFO).log("Loaded " + count + " tradeskill source config entries");
        return builder.build();
    }

    public void seedDefaults() {
        try (Connection conn = databaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM prof_tradeskill_sources")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    LOGGER.at(Level.INFO).log("prof_tradeskill_sources already has data, skipping seed");
                    return;
                }
            }

            String insertSql = """
                INSERT INTO prof_tradeskill_sources (pattern, match_type, tradeskill, xp_amount, min_level)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                // Mining: ores by tier
                insertRow(stmt, "ore_copper",     "prefix", "MINING", 7,  0);
                insertRow(stmt, "ore_iron",       "prefix", "MINING", 13, 0);
                insertRow(stmt, "ore_silver",     "prefix", "MINING", 24, 0);
                insertRow(stmt, "ore_gold",       "prefix", "MINING", 32, 0);
                insertRow(stmt, "ore_cobalt",     "prefix", "MINING", 37, 0);
                insertRow(stmt, "ore_thorium",    "prefix", "MINING", 37, 0);
                insertRow(stmt, "ore_mithril",    "prefix", "MINING", 60, 0);
                insertRow(stmt, "ore_adamantite", "prefix", "MINING", 50, 0);
                insertRow(stmt, "ore_onyxium",    "prefix", "MINING", 60, 0);
                insertRow(stmt, "ore_prisma",     "prefix", "MINING", 60, 0);

                // Woodcutting: trees
                insertRow(stmt, "_trunk", "contains", "WOODCUTTING", 15, 0);

                // Farming: crops
                insertRow(stmt, "plant_crop_", "prefix", "FARMING", 20, 0);
                insertRow(stmt, "berry",       "contains", "FARMING", 25, 0);

                LOGGER.at(Level.INFO).log("Seeded default tradeskill source entries");
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed tradeskill source defaults: " + e.getMessage());
        }
    }

    private void insertRow(PreparedStatement stmt, String pattern, String matchType,
                           String tradeskill, int xp, int minLevel) throws SQLException {
        stmt.setString(1, pattern);
        stmt.setString(2, matchType);
        stmt.setString(3, tradeskill);
        stmt.setInt(4, xp);
        stmt.setInt(5, minLevel);
        stmt.addBatch();
        stmt.executeBatch();
    }
}
