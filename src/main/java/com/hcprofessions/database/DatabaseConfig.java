package com.hcprofessions.database;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;

public class DatabaseConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Config");

    private String url = "jdbc:postgresql://postgres:5432/factionwars";
    private String username = "factionwars";
    private String password = "factionwars_secret";
    private int poolSize = 5;

    public static DatabaseConfig load(File modFolder) {
        File configFile = new File(modFolder, "db-config.properties");
        DatabaseConfig config = new DatabaseConfig();

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Properties props = new Properties();
                props.load(fis);

                config.url = props.getProperty("database.url", config.url);
                config.username = props.getProperty("database.username", config.username);
                config.password = props.getProperty("database.password", config.password);
                config.poolSize = Integer.parseInt(props.getProperty("database.pool_size", String.valueOf(config.poolSize)));

                LOGGER.at(Level.INFO).log("Loaded database config from " + configFile.getPath());
            } catch (IOException e) {
                LOGGER.at(Level.WARNING).log("Failed to load database config: " + e.getMessage());
            }
        } else {
            if (!modFolder.exists()) {
                modFolder.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                Properties props = new Properties();
                props.setProperty("database.url", config.url);
                props.setProperty("database.username", config.username);
                props.setProperty("database.password", config.password);
                props.setProperty("database.pool_size", String.valueOf(config.poolSize));
                props.store(fos, "HC_Professions Database Configuration");

                LOGGER.at(Level.INFO).log("Created default database config at " + configFile.getPath());
            } catch (IOException e) {
                LOGGER.at(Level.WARNING).log("Failed to create default database config: " + e.getMessage());
            }
        }

        return config;
    }

    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getPoolSize() { return poolSize; }
}
