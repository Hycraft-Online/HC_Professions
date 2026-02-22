package com.hcprofessions.database;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-DB");

    private final HikariDataSource dataSource;

    public DatabaseManager(String jdbcUrl, String username, String password, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setDriverClassName("org.postgresql.Driver");
        config.setConnectionTimeout(10000);
        config.setPoolName("HC_Professions-DB-Pool");

        this.dataSource = new HikariDataSource(config);
        initializeSchema();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.at(Level.INFO).log("Database connection pool closed");
        }
    }

    private void initializeSchema() {
        String createTradeskillsTable = """
            CREATE TABLE IF NOT EXISTS prof_tradeskills (
                player_uuid UUID NOT NULL,
                tradeskill VARCHAR(32) NOT NULL,
                level INT DEFAULT 0,
                current_xp BIGINT DEFAULT 0,
                total_xp_earned BIGINT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, tradeskill)
            )
            """;

        String createProfessionsTable = """
            CREATE TABLE IF NOT EXISTS prof_professions (
                player_uuid UUID PRIMARY KEY,
                profession VARCHAR(32),
                level INT DEFAULT 0,
                current_xp BIGINT DEFAULT 0,
                total_xp_earned BIGINT DEFAULT 0,
                total_items_crafted INT DEFAULT 0,
                respec_count INT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String createRecipeGatesTable = """
            CREATE TABLE IF NOT EXISTS prof_recipe_gates (
                recipe_output_id VARCHAR(256) PRIMARY KEY,
                required_profession VARCHAR(32) NOT NULL,
                required_level INT DEFAULT 1,
                profession_xp_granted INT DEFAULT 0,
                enabled BOOLEAN DEFAULT true
            )
            """;

        String createTradeskillSourcesTable = """
            CREATE TABLE IF NOT EXISTS prof_tradeskill_sources (
                id SERIAL PRIMARY KEY,
                pattern VARCHAR(128) NOT NULL,
                match_type VARCHAR(16) DEFAULT 'exact',
                tradeskill VARCHAR(32) NOT NULL,
                xp_amount INT NOT NULL,
                min_level INT DEFAULT 0,
                enabled BOOLEAN DEFAULT true,
                UNIQUE(pattern, tradeskill)
            )
            """;

        String createXpConfigTable = """
            CREATE TABLE IF NOT EXISTS prof_xp_config (
                key VARCHAR(64) PRIMARY KEY,
                value VARCHAR(256) NOT NULL
            )
            """;

        String createQualityTiersTable = """
            CREATE TABLE IF NOT EXISTS prof_quality_tiers (
                id SERIAL PRIMARY KEY,
                name VARCHAR(64) NOT NULL UNIQUE,
                min_level INT NOT NULL,
                max_level INT NOT NULL,
                max_rarity VARCHAR(32) NOT NULL,
                min_affixes INT DEFAULT 0,
                max_affixes INT DEFAULT 0,
                bonus_affix_chance DOUBLE PRECISION DEFAULT 0.0,
                ilvl_variance INT DEFAULT 0,
                sort_order INT DEFAULT 0
            )
            """;

        String createDefinitionsTable = """
            CREATE TABLE IF NOT EXISTS prof_definitions (
                id VARCHAR(64) PRIMARY KEY,
                type VARCHAR(16) NOT NULL,
                display_name VARCHAR(64) NOT NULL,
                color_hex VARCHAR(7) NOT NULL,
                description TEXT DEFAULT '',
                enabled BOOLEAN DEFAULT true,
                sort_order INT DEFAULT 0
            )
            """;

        String createTemperMaterialRequirementsTable = """
            CREATE TABLE IF NOT EXISTS prof_temper_material_requirements (
                material VARCHAR(64) PRIMARY KEY,
                required_level INT NOT NULL DEFAULT 1,
                enabled BOOLEAN DEFAULT true,
                sort_order INT DEFAULT 0
            )
            """;

        String createSkillXpRewardsTable = """
            CREATE TABLE IF NOT EXISTS skill_xp_rewards (
                id SERIAL PRIMARY KEY,
                event VARCHAR(32) NOT NULL,
                identifier VARCHAR(128) NOT NULL,
                is_pattern BOOLEAN DEFAULT false,
                skill_type VARCHAR(16) NOT NULL,
                skill_name VARCHAR(32),
                xp_amount INT NOT NULL,
                min_level INT DEFAULT 0,
                enabled BOOLEAN DEFAULT true
            )
            """;

        String createSkillXpRewardsIndex = """
            CREATE UNIQUE INDEX IF NOT EXISTS skill_xp_rewards_unique
            ON skill_xp_rewards (event, identifier, skill_type, COALESCE(skill_name, ''))
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTradeskillsTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_tradeskills table");

            stmt.execute(createProfessionsTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_professions table");

            stmt.execute(createRecipeGatesTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_recipe_gates table");

            stmt.execute(createTradeskillSourcesTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_tradeskill_sources table");

            stmt.execute(createXpConfigTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_xp_config table");

            stmt.execute(createQualityTiersTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_quality_tiers table");

            stmt.execute(createDefinitionsTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_definitions table");

            stmt.execute(createTemperMaterialRequirementsTable);
            LOGGER.at(Level.INFO).log("Created/verified prof_temper_material_requirements table");

            stmt.execute(createSkillXpRewardsTable);
            stmt.execute(createSkillXpRewardsIndex);
            LOGGER.at(Level.INFO).log("Created/verified skill_xp_rewards table");

            // Migration: rename stat_bonus -> ilvl_variance (idempotent)
            stmt.execute("""
                DO $$ BEGIN
                    IF EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_name='prof_quality_tiers' AND column_name='stat_bonus') THEN
                        ALTER TABLE prof_quality_tiers RENAME COLUMN stat_bonus TO ilvl_variance;
                        ALTER TABLE prof_quality_tiers ALTER COLUMN ilvl_variance TYPE INT USING 0;
                    END IF;
                END $$
                """);
            LOGGER.at(Level.INFO).log("Migration: stat_bonus -> ilvl_variance (idempotent)");

            // Migration: add min_level to tradeskill sources
            stmt.execute("ALTER TABLE prof_tradeskill_sources ADD COLUMN IF NOT EXISTS min_level INT DEFAULT 0");
            LOGGER.at(Level.INFO).log("Migration: added min_level to prof_tradeskill_sources (idempotent)");

            // Migration: add ingredients and time_seconds to recipe gates
            stmt.execute("ALTER TABLE prof_recipe_gates ADD COLUMN IF NOT EXISTS ingredients JSONB DEFAULT '[]'::jsonb");
            stmt.execute("ALTER TABLE prof_recipe_gates ADD COLUMN IF NOT EXISTS time_seconds INT DEFAULT 0");
            LOGGER.at(Level.INFO).log("Migration: added ingredients/time_seconds to prof_recipe_gates (idempotent)");

            // Migration: add learn_cost to recipe gates (cost in Coin_Copper to learn from vendor)
            stmt.execute("ALTER TABLE prof_recipe_gates ADD COLUMN IF NOT EXISTS learn_cost INT DEFAULT 0");
            stmt.execute("UPDATE prof_recipe_gates SET learn_cost = required_level * 5 WHERE learn_cost = 0");
            LOGGER.at(Level.INFO).log("Migration: added learn_cost to prof_recipe_gates (idempotent)");

            // Migration: add bench_category for per-recipe bench tab override (e.g. Prepared, Baked for COOK)
            stmt.execute("ALTER TABLE prof_recipe_gates ADD COLUMN IF NOT EXISTS bench_category VARCHAR(64) DEFAULT NULL");
            LOGGER.at(Level.INFO).log("Migration: added bench_category to prof_recipe_gates (idempotent)");

            // Migration: rename Weaponsmith -> Bladesmith, Armorsmith -> Platesmith display names
            stmt.execute("UPDATE prof_definitions SET display_name = 'Bladesmith', description = 'Forge powerful weapons' WHERE id = 'WEAPONSMITH' AND display_name = 'Weaponsmith'");
            stmt.execute("UPDATE prof_definitions SET display_name = 'Platesmith', description = 'Craft protective plate armor' WHERE id = 'ARMORSMITH' AND display_name = 'Armorsmith'");
            LOGGER.at(Level.INFO).log("Migration: renamed Weaponsmith->Bladesmith, Armorsmith->Platesmith (idempotent)");

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize database schema: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
