package com.hcprofessions.database;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ConfigRepo");
    private final DatabaseManager db;

    public ConfigRepository(DatabaseManager db) {
        this.db = db;
    }

    public Map<String, String> loadAll() {
        Map<String, String> config = new LinkedHashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT key, value FROM prof_xp_config ORDER BY key")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                config.put(rs.getString("key"), rs.getString("value"));
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load XP config: " + e.getMessage());
        }
        return config;
    }

    public void set(String key, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO prof_xp_config (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to set config key " + key + ": " + e.getMessage());
        }
    }

    public void seedDefaults() {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO prof_xp_config (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING")) {

            String[][] defaults = {
                {"max_level", "100"},
                {"xp_base", "100"},
                {"xp_exponent", "1.5"}
            };

            for (String[] entry : defaults) {
                stmt.setString(1, entry[0]);
                stmt.setString(2, entry[1]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            LOGGER.at(Level.INFO).log("Seeded XP config defaults");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to seed XP config: " + e.getMessage());
        }
    }
}
