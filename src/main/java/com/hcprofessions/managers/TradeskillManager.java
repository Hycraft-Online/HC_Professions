package com.hcprofessions.managers;

import com.hcprofessions.config.XPCurve;
import com.hcprofessions.database.TradeskillRepository;
import com.hcprofessions.models.PlayerTradeskillData;
import com.hcprofessions.models.Tradeskill;
import com.hcprofessions.config.GlobalXpMultiplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TradeskillManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Tradeskill");

    private final TradeskillRepository repository;
    private final ConcurrentHashMap<UUID, Map<Tradeskill, PlayerTradeskillData>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> accountToChar = new ConcurrentHashMap<>();

    public TradeskillManager(TradeskillRepository repository) {
        this.repository = repository;
    }

    public void registerCharMapping(UUID accountUuid, UUID charUuid) {
        accountToChar.put(accountUuid, charUuid);
    }

    public void unregisterCharMapping(UUID accountUuid) {
        accountToChar.remove(accountUuid);
    }

    /** Translate account UUID to character UUID for DB operations. */
    UUID dbKey(UUID uuid) {
        return accountToChar.getOrDefault(uuid, uuid);
    }

    public Map<Tradeskill, PlayerTradeskillData> getPlayerData(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, uuid -> repository.loadAll(dbKey(uuid)));
    }

    public PlayerTradeskillData getTradeskillData(UUID playerUuid, Tradeskill tradeskill) {
        return getPlayerData(playerUuid).get(tradeskill);
    }

    public int getLevel(UUID playerUuid, Tradeskill tradeskill) {
        PlayerTradeskillData data = getTradeskillData(playerUuid, tradeskill);
        return data != null ? data.getLevel() : 0;
    }

    public void grantXp(PlayerRef playerRef, Tradeskill tradeskill, int xpAmount) {
        xpAmount = GlobalXpMultiplier.apply(xpAmount);

        UUID uuid = playerRef.getUuid();
        PlayerTradeskillData data = getTradeskillData(uuid, tradeskill);
        if (data == null) return;

        int oldLevel = data.getLevel();
        if (oldLevel >= XPCurve.getMaxLevel()) return;

        data.addXp(xpAmount);

        // Show XP gain notification
        try {
            Message xpMsg = Message.raw("+" + xpAmount + " " + tradeskill.getDisplayName() + " XP")
                .color(new Color(180, 220, 140));
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), xpMsg, NotificationStyle.Default);
        } catch (Exception ignored) {}

        // Process level ups
        boolean leveledUp = false;
        while (data.getLevel() < XPCurve.getMaxLevel()) {
            long needed = XPCurve.getXpToNextLevel(data.getLevel());
            if (data.getCurrentXp() >= needed) {
                data.setCurrentXp(data.getCurrentXp() - needed);
                data.setLevel(data.getLevel() + 1);
                leveledUp = true;
            } else {
                break;
            }
        }

        // Cap XP at max level
        if (data.getLevel() >= XPCurve.getMaxLevel()) {
            data.setCurrentXp(0);
        }

        if (leveledUp) {
            // Persist immediately on level-up to prevent desync on relog (HYC-18)
            repository.save(data);

            int newLevel = data.getLevel();
            Message msg = Message.raw(tradeskill.getDisplayName() + " leveled up! Lv. " + newLevel)
                .color(new Color(0, 200, 100));
            try {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
            } catch (Exception e) {
                // Player may have disconnected
            }
            LOGGER.at(Level.INFO).log("%s %s level up: %d -> %d",
                playerRef.getUsername(), tradeskill.getDisplayName(), oldLevel, newLevel);
        }
    }

    public void setLevel(UUID playerUuid, Tradeskill tradeskill, int level) {
        PlayerTradeskillData data = getTradeskillData(playerUuid, tradeskill);
        if (data == null) return;
        data.setLevel(Math.min(level, XPCurve.getMaxLevel()));
        data.setCurrentXp(0);
        // save() uses data.getPlayerUuid() which is the DB key from initial load
        repository.save(data);
    }

    public void savePlayer(UUID playerUuid) {
        Map<Tradeskill, PlayerTradeskillData> data = cache.get(playerUuid);
        if (data != null) {
            repository.saveAll(data);
        }
    }

    public void saveAllPlayers() {
        for (Map<Tradeskill, PlayerTradeskillData> allData : cache.values()) {
            repository.saveAll(allData);
        }
    }

    public void invalidateCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public String getProgressString(UUID playerUuid, Tradeskill tradeskill) {
        PlayerTradeskillData data = getTradeskillData(playerUuid, tradeskill);
        if (data == null) return "N/A";
        if (data.getLevel() >= XPCurve.getMaxLevel()) return "MAX";
        long needed = XPCurve.getXpToNextLevel(data.getLevel());
        return String.format("%,d / %,d XP", data.getCurrentXp(), needed);
    }
}
