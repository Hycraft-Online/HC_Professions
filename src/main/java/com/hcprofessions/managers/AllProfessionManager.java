package com.hcprofessions.managers;

import com.hcprofessions.config.XPCurve;
import com.hcprofessions.database.AllProfessionRepository;
import com.hcprofessions.models.PlayerAllProfessionData;
import com.hcprofessions.models.Profession;
import com.hcprofessions.config.GlobalXpMultiplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AllProfessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-AllProf");

    private final AllProfessionRepository repository;
    private final ProfessionManager professionManager;
    private final ConcurrentHashMap<UUID, Map<Profession, PlayerAllProfessionData>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> accountToChar = new ConcurrentHashMap<>();

    /** Non-native profession level cap (set from config) */
    private volatile int nonNativeLevelCap = 10;

    public AllProfessionManager(AllProfessionRepository repository, ProfessionManager professionManager) {
        this.repository = repository;
        this.professionManager = professionManager;
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

    public void setNonNativeLevelCap(int cap) {
        this.nonNativeLevelCap = cap;
    }

    public int getNonNativeLevelCap() {
        return nonNativeLevelCap;
    }

    public Map<Profession, PlayerAllProfessionData> getPlayerData(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, uuid -> repository.loadAll(dbKey(uuid)));
    }

    public int getLevel(UUID playerUuid, Profession profession) {
        PlayerAllProfessionData data = getPlayerData(playerUuid).get(profession);
        return data != null ? data.getLevel() : 0;
    }

    /**
     * Grant XP to a specific profession for a player.
     * If the profession is NOT the player's main profession, cap at nonNativeLevelCap.
     */
    public void grantXp(PlayerRef playerRef, Profession targetProfession, int xpAmount) {
        xpAmount = GlobalXpMultiplier.apply(xpAmount);

        UUID uuid = playerRef.getUuid();
        Map<Profession, PlayerAllProfessionData> allData = getPlayerData(uuid);
        PlayerAllProfessionData data = allData.get(targetProfession);
        if (data == null) return;

        // Determine if this is the player's main profession
        Profession mainProfession = professionManager.getProfession(uuid);
        boolean isMain = targetProfession == mainProfession;

        int maxLevel = isMain ? XPCurve.getMaxLevel() : nonNativeLevelCap;

        int oldLevel = data.getLevel();
        if (oldLevel >= maxLevel) return;

        data.addXp(xpAmount);

        // Process level ups
        boolean leveledUp = false;
        while (data.getLevel() < maxLevel) {
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
        if (data.getLevel() >= maxLevel) {
            data.setCurrentXp(0);
        }

        if (leveledUp) {
            // Persist immediately on level-up to prevent desync on relog (HYC-18)
            repository.save(data);

            int newLevel = data.getLevel();
            String suffix = isMain ? "" : " (secondary)";
            Message msg = Message.raw(targetProfession.getDisplayName() + suffix + " leveled up! Lv. " + newLevel)
                .color(targetProfession.getColor());
            try {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
            } catch (Exception ignored) {}
            LOGGER.at(Level.INFO).log("%s %s%s level up: %d -> %d",
                playerRef.getUsername(), targetProfession.getDisplayName(), suffix, oldLevel, newLevel);
        }
    }

    /**
     * Called when a player chooses a profession. Resets AllProfessionManager data
     * for that profession to level 1 / 0 XP so both managers stay in sync.
     */
    public void syncOnChoose(UUID playerUuid, Profession profession) {
        Map<Profession, PlayerAllProfessionData> allData = getPlayerData(playerUuid);
        PlayerAllProfessionData data = allData.get(profession);
        if (data != null) {
            data.setLevel(1);
            data.setCurrentXp(0);
            repository.save(data);
            LOGGER.at(Level.INFO).log("Synced AllProfession %s to Lv.1 for %s on choose", profession.name(), playerUuid);
        }
    }

    /**
     * Called when a player respecs. Resets AllProfessionManager data for the old
     * profession to the post-respec level so it doesn't appear as a ghost secondary.
     */
    public void resetOnRespec(UUID playerUuid, Profession oldProfession, int newLevel) {
        Map<Profession, PlayerAllProfessionData> allData = getPlayerData(playerUuid);
        PlayerAllProfessionData data = allData.get(oldProfession);
        if (data != null) {
            data.setLevel(newLevel);
            data.setCurrentXp(0);
            repository.save(data);
            LOGGER.at(Level.INFO).log("Reset AllProfession %s to Lv.%d for %s on respec", oldProfession.name(), newLevel, playerUuid);
        }
    }

    public void savePlayer(UUID playerUuid) {
        Map<Profession, PlayerAllProfessionData> data = cache.get(playerUuid);
        if (data != null) {
            repository.saveAll(data);
        }
    }

    public void saveAllPlayers() {
        for (Map<Profession, PlayerAllProfessionData> allData : cache.values()) {
            repository.saveAll(allData);
        }
    }

    public void invalidateCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }
}
