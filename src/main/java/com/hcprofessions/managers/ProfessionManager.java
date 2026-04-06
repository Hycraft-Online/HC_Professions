package com.hcprofessions.managers;

import com.hcprofessions.config.XPCurve;
import com.hcprofessions.database.ProfessionRepository;
import com.hcprofessions.models.PlayerProfessionData;
import com.hcprofessions.models.Profession;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import com.hcprofessions.config.GlobalXpMultiplier;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ProfessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Profession");

    private final ProfessionRepository repository;
    private final ConcurrentHashMap<UUID, PlayerProfessionData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> accountToChar = new ConcurrentHashMap<>();
    private AllProfessionManager allProfessionManager;

    public ProfessionManager(ProfessionRepository repository) {
        this.repository = repository;
    }

    public void setAllProfessionManager(AllProfessionManager allProfessionManager) {
        this.allProfessionManager = allProfessionManager;
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

    public PlayerProfessionData getPlayerData(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, uuid -> repository.loadOrCreate(dbKey(uuid)));
    }

    @Nullable
    public Profession getProfession(UUID playerUuid) {
        return getPlayerData(playerUuid).getProfession();
    }

    public int getLevel(UUID playerUuid) {
        return getPlayerData(playerUuid).getLevel();
    }

    public boolean chooseProfession(UUID playerUuid, Profession profession, PlayerRef playerRef, World world) {
        PlayerProfessionData data = getPlayerData(playerUuid);

        if (!profession.isEnabled()) {
            playerRef.sendMessage(Message.raw(profession.getDisplayName() +
                " is not available right now.").color(Color.RED));
            return false;
        }

        if (data.hasProfession()) {
            playerRef.sendMessage(Message.raw("You are already a " + data.getProfession().getDisplayName() +
                ". Use /profession respec to change.").color(Color.RED));
            return false;
        }

        data.setProfession(profession);
        data.setLevel(1);
        data.setCurrentXp(0);
        repository.save(data);

        // Sync AllProfessionManager to match (prevents level desync / ghost secondary)
        if (allProfessionManager != null) {
            allProfessionManager.syncOnChoose(playerUuid, profession);
        }

        Message msg = Message.raw("You are now a " + profession.getDisplayName() + "!")
            .color(profession.getColor());
        try {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
        } catch (Exception ignored) {}

        LOGGER.at(Level.INFO).log("%s chose profession: %s", playerRef.getUsername(), profession.getDisplayName());

        return true;
    }

    public boolean respec(UUID playerUuid, PlayerRef playerRef) {
        PlayerProfessionData data = getPlayerData(playerUuid);

        if (!data.hasProfession()) {
            playerRef.sendMessage(Message.raw("You don't have a profession to respec.").color(Color.RED));
            return false;
        }

        Profession oldProf = data.getProfession();
        String oldProfession = oldProf.getDisplayName();
        int oldLevel = data.getLevel();
        data.setProfession(null);

        // If above level 20, cap at 20 instead of full reset
        int newLevel;
        if (oldLevel > 20) {
            newLevel = 20;
            data.setLevel(20);
            data.setCurrentXp(0);
        } else {
            newLevel = 0;
            data.resetProgression();
        }
        data.incrementRespecCount();
        repository.save(data);

        // Reset AllProfessionManager to match (prevents ghost secondary profession)
        if (allProfessionManager != null) {
            allProfessionManager.resetOnRespec(playerUuid, oldProf, newLevel);
        }

        if (oldLevel > 20) {
            playerRef.sendMessage(Message.raw("Profession reset! You are no longer a " + oldProfession +
                ". Your level has been set to 20.").color(new Color(255, 165, 0)));
        } else {
            playerRef.sendMessage(Message.raw("Profession reset! You are no longer a " + oldProfession +
                ". All profession progress has been lost.").color(new Color(255, 165, 0)));
        }

        LOGGER.at(Level.INFO).log("%s respecced from %s (respec #%d)",
            playerRef.getUsername(), oldProfession, data.getRespecCount());
        return true;
    }

    // Configurable release cap — set via prof_xp_config.release_level_cap
    private volatile int releaseLevelCap = 20;

    public void setReleaseLevelCap(int cap) {
        this.releaseLevelCap = cap;
    }

    public int getReleaseLevelCap() {
        return releaseLevelCap;
    }

    public void grantXp(PlayerRef playerRef, int xpAmount) {
        xpAmount = GlobalXpMultiplier.apply(xpAmount);

        UUID uuid = playerRef.getUuid();
        PlayerProfessionData data = getPlayerData(uuid);
        if (!data.hasProfession()) return;

        int oldLevel = data.getLevel();
        if (oldLevel >= releaseLevelCap) return;
        if (oldLevel >= XPCurve.getMaxLevel()) return;

        data.addXp(xpAmount);

        // Show XP gain notification
        try {
            Profession prof = data.getProfession();
            Message xpMsg = Message.raw("+" + xpAmount + " " + prof.getDisplayName() + " XP")
                .color(prof.getColor());
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), xpMsg, NotificationStyle.Default);
        } catch (Exception ignored) {}

        int effectiveCap = Math.min(releaseLevelCap, XPCurve.getMaxLevel());

        boolean leveledUp = false;
        while (data.getLevel() < effectiveCap) {
            long needed = XPCurve.getXpToNextLevel(data.getLevel());
            if (data.getCurrentXp() >= needed) {
                data.setCurrentXp(data.getCurrentXp() - needed);
                data.setLevel(data.getLevel() + 1);
                leveledUp = true;
            } else {
                break;
            }
        }

        if (data.getLevel() >= effectiveCap) {
            data.setCurrentXp(0);
        }

        if (leveledUp) {
            // Persist immediately on level-up to prevent desync on relog (HYC-18)
            repository.save(data);

            int newLevel = data.getLevel();
            Profession prof = data.getProfession();
            Message msg = Message.raw(prof.getDisplayName() + " leveled up! Lv. " + newLevel)
                .color(prof.getColor());
            try {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
            } catch (Exception ignored) {}
            LOGGER.at(Level.INFO).log("%s %s level up: %d -> %d",
                playerRef.getUsername(), prof.getDisplayName(), oldLevel, newLevel);
        }
    }

    public void setLevel(UUID playerUuid, int level) {
        PlayerProfessionData data = getPlayerData(playerUuid);
        data.setLevel(Math.min(level, XPCurve.getMaxLevel()));
        data.setCurrentXp(0);
        // save() uses data.getPlayerUuid() which is the DB key from initial load
        repository.save(data);
    }

    public void savePlayer(UUID playerUuid) {
        PlayerProfessionData data = cache.get(playerUuid);
        if (data != null && data.isDirty()) {
            repository.save(data);
        }
    }

    public void saveAllPlayers() {
        for (PlayerProfessionData data : cache.values()) {
            if (data.isDirty()) {
                try {
                    repository.save(data);
                } catch (Exception e) {
                    LOGGER.at(Level.SEVERE).log("Failed to save profession data for %s on shutdown: %s",
                        data.getPlayerUuid(), e.getMessage());
                }
            }
        }
    }

    public void invalidateCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public int getEffectiveLevelCap() {
        return Math.min(releaseLevelCap, XPCurve.getMaxLevel());
    }

    public String getProgressString(UUID playerUuid) {
        PlayerProfessionData data = getPlayerData(playerUuid);
        if (!data.hasProfession()) return "No profession";
        if (data.getLevel() >= getEffectiveLevelCap()) return "MAX";
        long needed = XPCurve.getXpToNextLevel(data.getLevel());
        return String.format("%,d / %,d XP", data.getCurrentXp(), needed);
    }
}
