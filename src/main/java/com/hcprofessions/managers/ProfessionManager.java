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

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ProfessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Profession");

    private final ProfessionRepository repository;
    private final ConcurrentHashMap<UUID, PlayerProfessionData> cache = new ConcurrentHashMap<>();

    public ProfessionManager(ProfessionRepository repository) {
        this.repository = repository;
    }

    public PlayerProfessionData getPlayerData(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, repository::loadOrCreate);
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

        Message msg = Message.raw("You are now a " + profession.getDisplayName() + "!")
            .color(profession.getColor());
        try {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
        } catch (Exception ignored) {}

        LOGGER.at(Level.INFO).log("%s chose profession: %s", playerRef.getUsername(), profession.getDisplayName());

        // Give profession bench item
        String benchItemId = switch (profession) {
            case WEAPONSMITH -> "Bench_Weaponsmith_Forge";
            case ARMORSMITH -> "Bench_Armorsmith_Anvil";
            case ALCHEMIST -> "Bench_Alchemy";
            case COOK -> "Bench_Cooking";
            case LEATHERWORKER -> "Bench_Tannery";
            case CARPENTER -> "Bench_Furniture";
            case TAILOR -> "Bench_Loom";
            case ENCHANTER -> "Bench_Arcane";
            case BUILDER -> null; // No bench — Builders use /prefabBuilder command
        };

        if (benchItemId != null && world != null) {
            world.execute(() -> {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) return;
                ItemStack benchItem = new ItemStack(benchItemId, 1);
                SimpleItemContainer.addOrDropItemStacks(store, ref,
                    playerComponent.getInventory().getCombinedArmorHotbarUtilityStorage(),
                    List.of(benchItem));
            });
        }

        return true;
    }

    public boolean respec(UUID playerUuid, PlayerRef playerRef) {
        PlayerProfessionData data = getPlayerData(playerUuid);

        if (!data.hasProfession()) {
            playerRef.sendMessage(Message.raw("You don't have a profession to respec.").color(Color.RED));
            return false;
        }

        String oldProfession = data.getProfession().getDisplayName();
        int oldLevel = data.getLevel();
        data.setProfession(null);

        // If above level 20, cap at 20 instead of full reset
        if (oldLevel > 20) {
            data.setLevel(20);
            data.setCurrentXp(0);
        } else {
            data.resetProgression();
        }
        data.incrementRespecCount();
        repository.save(data);

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
        repository.save(data);
    }

    public void savePlayer(UUID playerUuid) {
        PlayerProfessionData data = cache.get(playerUuid);
        if (data != null && data.isDirty()) {
            repository.save(data);
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
