package com.hcprofessions.managers;

import com.hcprofessions.database.RecipeGateRepository;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CraftingGateManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-CraftGate");

    private final RecipeGateRepository repository;
    private final ProfessionManager professionManager;
    private volatile Map<String, RecipeGate> gateCache;

    public CraftingGateManager(RecipeGateRepository repository, ProfessionManager professionManager) {
        this.repository = repository;
        this.professionManager = professionManager;
    }

    public void loadCache() {
        this.gateCache = repository.loadAll();
        LOGGER.at(Level.INFO).log("Loaded %d recipe gates into cache", gateCache.size());
    }

    public void reloadCache() {
        loadCache();
    }

    @Nullable
    public RecipeGate getGate(String outputItemId) {
        if (gateCache == null) return null;
        return gateCache.get(outputItemId.toLowerCase());
    }

    public boolean isGated(String outputItemId) {
        return getGate(outputItemId) != null;
    }

    public enum GateCheckResult {
        ALLOWED,
        NO_PROFESSION,
        WRONG_PROFESSION,
        TOO_LOW_LEVEL
    }

    public record GateCheck(GateCheckResult result, @Nullable RecipeGate gate) {
        public boolean isAllowed() { return result == GateCheckResult.ALLOWED; }
    }

    public GateCheck checkPermission(UUID playerUuid, String outputItemId) {
        RecipeGate gate = getGate(outputItemId);
        if (gate == null) {
            return new GateCheck(GateCheckResult.ALLOWED, null);
        }

        Profession playerProf = professionManager.getProfession(playerUuid);
        if (playerProf == null) {
            return new GateCheck(GateCheckResult.NO_PROFESSION, gate);
        }

        if (playerProf != gate.requiredProfession()) {
            return new GateCheck(GateCheckResult.WRONG_PROFESSION, gate);
        }

        int playerLevel = professionManager.getLevel(playerUuid);
        if (playerLevel < gate.requiredLevel()) {
            return new GateCheck(GateCheckResult.TOO_LOW_LEVEL, gate);
        }

        return new GateCheck(GateCheckResult.ALLOWED, gate);
    }

    public Collection<RecipeGate> getAllGates() {
        return gateCache != null ? gateCache.values() : Collections.emptyList();
    }

    public int getGateCount() {
        return gateCache != null ? gateCache.size() : 0;
    }
}
