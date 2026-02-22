package com.hcprofessions.database;

import com.hcprofessions.models.PlayerAllProfessionData;
import com.hcprofessions.models.Profession;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AllProfessionRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-AllProfRepo");

    private final DatabaseManager databaseManager;

    public AllProfessionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initializeTable();
    }

    private void initializeTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS prof_all_professions (
                player_uuid UUID NOT NULL,
                profession VARCHAR(32) NOT NULL,
                level INT DEFAULT 0,
                current_xp BIGINT DEFAULT 0,
                total_xp_earned BIGINT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, profession)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(sql);
            LOGGER.at(Level.INFO).log("Created/verified prof_all_professions table");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create prof_all_professions table: " + e.getMessage());
        }
    }

    public Map<Profession, PlayerAllProfessionData> loadAll(UUID playerUuid) {
        Map<Profession, PlayerAllProfessionData> result = new EnumMap<>(Profession.class);

        String sql = "SELECT profession, level, current_xp, total_xp_earned FROM prof_all_professions WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Profession prof = Profession.fromString(rs.getString("profession"));
                    if (prof != null) {
                        result.put(prof, new PlayerAllProfessionData(
                            playerUuid, prof,
                            rs.getInt("level"),
                            rs.getLong("current_xp"),
                            rs.getLong("total_xp_earned")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load all professions for " + playerUuid + ": " + e.getMessage());
        }

        // Ensure all professions have entries
        for (Profession prof : Profession.values()) {
            result.computeIfAbsent(prof, p -> new PlayerAllProfessionData(playerUuid, p, 0, 0, 0));
        }

        return result;
    }

    public void save(PlayerAllProfessionData data) {
        String sql = """
            INSERT INTO prof_all_professions (player_uuid, profession, level, current_xp, total_xp_earned, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (player_uuid, profession) DO UPDATE SET
                level = EXCLUDED.level,
                current_xp = EXCLUDED.current_xp,
                total_xp_earned = EXCLUDED.total_xp_earned,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, data.getPlayerUuid());
            stmt.setString(2, data.getProfession().name());
            stmt.setInt(3, data.getLevel());
            stmt.setLong(4, data.getCurrentXp());
            stmt.setLong(5, data.getTotalXpEarned());
            stmt.executeUpdate();
            data.markClean();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save all-profession data for " + data.getPlayerUuid() + ": " + e.getMessage());
        }
    }

    public void saveAll(Map<Profession, PlayerAllProfessionData> allData) {
        for (PlayerAllProfessionData data : allData.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }
}
