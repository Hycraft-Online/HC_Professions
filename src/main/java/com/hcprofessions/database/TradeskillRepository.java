package com.hcprofessions.database;

import com.hcprofessions.models.PlayerTradeskillData;
import com.hcprofessions.models.Tradeskill;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TradeskillRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-TradeskillRepo");

    private final DatabaseManager databaseManager;

    public TradeskillRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Map<Tradeskill, PlayerTradeskillData> loadAll(UUID playerUuid) {
        Map<Tradeskill, PlayerTradeskillData> result = new EnumMap<>(Tradeskill.class);

        String sql = "SELECT tradeskill, level, current_xp, total_xp_earned FROM prof_tradeskills WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Tradeskill skill = Tradeskill.fromString(rs.getString("tradeskill"));
                    if (skill != null) {
                        result.put(skill, new PlayerTradeskillData(
                            playerUuid, skill,
                            rs.getInt("level"),
                            rs.getLong("current_xp"),
                            rs.getLong("total_xp_earned")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load tradeskills for " + playerUuid + ": " + e.getMessage());
        }

        // Ensure all tradeskills have entries
        for (Tradeskill skill : Tradeskill.values()) {
            result.computeIfAbsent(skill, s -> new PlayerTradeskillData(playerUuid, s, 0, 0, 0));
        }

        return result;
    }

    public void save(PlayerTradeskillData data) {
        String sql = """
            INSERT INTO prof_tradeskills (player_uuid, tradeskill, level, current_xp, total_xp_earned, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (player_uuid, tradeskill) DO UPDATE SET
                level = EXCLUDED.level,
                current_xp = EXCLUDED.current_xp,
                total_xp_earned = EXCLUDED.total_xp_earned,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, data.getPlayerUuid());
            stmt.setString(2, data.getTradeskill().name());
            stmt.setInt(3, data.getLevel());
            stmt.setLong(4, data.getCurrentXp());
            stmt.setLong(5, data.getTotalXpEarned());
            stmt.executeUpdate();
            data.markClean();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save tradeskill for " + data.getPlayerUuid() + ": " + e.getMessage());
        }
    }

    public void saveAll(Map<Tradeskill, PlayerTradeskillData> allData) {
        for (PlayerTradeskillData data : allData.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }

    public void setLevel(UUID playerUuid, Tradeskill tradeskill, int level) {
        String sql = """
            INSERT INTO prof_tradeskills (player_uuid, tradeskill, level, current_xp, total_xp_earned, updated_at)
            VALUES (?, ?, ?, 0, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (player_uuid, tradeskill) DO UPDATE SET
                level = EXCLUDED.level,
                current_xp = 0,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setString(2, tradeskill.name());
            stmt.setInt(3, level);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to set tradeskill level: " + e.getMessage());
        }
    }
}
