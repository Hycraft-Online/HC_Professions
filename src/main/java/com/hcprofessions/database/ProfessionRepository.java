package com.hcprofessions.database;

import com.hcprofessions.models.PlayerProfessionData;
import com.hcprofessions.models.Profession;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class ProfessionRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ProfRepo");

    private final DatabaseManager databaseManager;

    public ProfessionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Nullable
    public PlayerProfessionData load(UUID playerUuid) {
        String sql = """
            SELECT profession, level, current_xp, total_xp_earned, total_items_crafted, respec_count
            FROM prof_professions WHERE player_uuid = ?
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerProfessionData(
                        playerUuid,
                        Profession.fromString(rs.getString("profession")),
                        rs.getInt("level"),
                        rs.getLong("current_xp"),
                        rs.getLong("total_xp_earned"),
                        rs.getInt("total_items_crafted"),
                        rs.getInt("respec_count")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load profession for " + playerUuid + ": " + e.getMessage());
        }

        return null;
    }

    public PlayerProfessionData loadOrCreate(UUID playerUuid) {
        PlayerProfessionData data = load(playerUuid);
        if (data != null) return data;
        return new PlayerProfessionData(playerUuid, null, 0, 0, 0, 0, 0);
    }

    public void save(PlayerProfessionData data) {
        String sql = """
            INSERT INTO prof_professions (player_uuid, profession, level, current_xp, total_xp_earned,
                total_items_crafted, respec_count, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (player_uuid) DO UPDATE SET
                profession = EXCLUDED.profession,
                level = EXCLUDED.level,
                current_xp = EXCLUDED.current_xp,
                total_xp_earned = EXCLUDED.total_xp_earned,
                total_items_crafted = EXCLUDED.total_items_crafted,
                respec_count = EXCLUDED.respec_count,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, data.getPlayerUuid());
            stmt.setString(2, data.hasProfession() ? data.getProfession().name() : null);
            stmt.setInt(3, data.getLevel());
            stmt.setLong(4, data.getCurrentXp());
            stmt.setLong(5, data.getTotalXpEarned());
            stmt.setInt(6, data.getTotalItemsCrafted());
            stmt.setInt(7, data.getRespecCount());
            stmt.executeUpdate();
            data.markClean();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save profession for " + data.getPlayerUuid() + ": " + e.getMessage());
        }
    }

    public void setLevel(UUID playerUuid, int level) {
        String sql = """
            UPDATE prof_professions SET level = ?, current_xp = 0, updated_at = CURRENT_TIMESTAMP
            WHERE player_uuid = ?
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, level);
            stmt.setObject(2, playerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to set profession level: " + e.getMessage());
        }
    }
}
