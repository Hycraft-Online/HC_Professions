package com.hcprofessions.systems;

import com.hcprofessions.models.ActionType;
import com.hcprofessions.services.ActionXpService;
import com.hcprofessions.services.ActionXpService.MatchedGrant;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Hooks into HC_Factions' ClaimPickupProtectionSystem via its static pickup listener API.
 * This avoids the Hytale engine limitation where only one EntityEventSystem processes
 * a given EcsEvent type per entity.
 *
 * FARMING grants require the player to be on their own claimed land (solo or guild).
 * HERBALISM grants are awarded anywhere the pickup is allowed.
 */
public class PickupXpListener {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Pickup");

    private final ActionXpService actionXpService;

    // Reflection handles for HC_Factions claim checking
    private boolean claimCheckAvailable = false;
    private Method getClaimMethod;          // ClaimManager.getClaim(String, int, int)
    private Method toChunkCoordMethod;      // ClaimManager.toChunkCoord(double)
    private Method isFactionClaimMethod;    // Claim.isFactionClaim()
    private Method isSoloPlayerClaimMethod; // Claim.isSoloPlayerClaim()
    private Method getPlayerOwnerIdMethod;  // Claim.getPlayerOwnerId()
    private Method getGuildIdMethod;        // Claim.getGuildId()
    private Method getInstanceMethod;       // HC_FactionsPlugin.getInstance()
    private Method getClaimManagerMethod;   // HC_FactionsPlugin.getClaimManager()
    private Method getPlayerDataRepoMethod; // HC_FactionsPlugin.getPlayerDataRepository()
    private Method getPlayerDataMethod;     // PlayerDataRepository.getPlayerData(UUID)
    private Method pdGetGuildIdMethod;      // PlayerData.getGuildId()

    public PickupXpListener(ActionXpService actionXpService) {
        this.actionXpService = actionXpService;
    }

    /**
     * Register this listener with HC_Factions' ClaimPickupProtectionSystem.
     * Uses reflection to avoid compile-time dependency.
     *
     * @return true if registration succeeded
     */
    public boolean register() {
        try {
            // Register as pickup listener
            Class<?> claimPickupClass = Class.forName(
                "com.hcfactions.systems.ClaimPickupProtectionSystem");
            Method addListener = claimPickupClass.getMethod(
                "addPickupListener", BiConsumer.class);
            addListener.invoke(null, (BiConsumer<PlayerRef, String>) this::onPickupAllowed);

            LOGGER.at(Level.INFO).log("Registered pickup listener with HC_Factions");

            // Resolve claim-checking reflection handles
            resolveClaimCheckHandles();

            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.at(Level.WARNING).log("HC_Factions not found - PickupXpListener disabled");
            return false;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to register PickupXpListener: " + e.getMessage());
            return false;
        }
    }

    private void resolveClaimCheckHandles() {
        try {
            // HC_FactionsPlugin
            Class<?> pluginClass = Class.forName("com.hcfactions.HC_FactionsPlugin");
            getInstanceMethod = pluginClass.getMethod("getInstance");
            getClaimManagerMethod = pluginClass.getMethod("getClaimManager");
            getPlayerDataRepoMethod = pluginClass.getMethod("getPlayerDataRepository");

            // ClaimManager
            Class<?> claimManagerClass = Class.forName("com.hcfactions.managers.ClaimManager");
            getClaimMethod = claimManagerClass.getMethod("getClaim", String.class, int.class, int.class);
            toChunkCoordMethod = claimManagerClass.getMethod("toChunkCoord", double.class);

            // Claim
            Class<?> claimClass = Class.forName("com.hcfactions.models.Claim");
            isFactionClaimMethod = claimClass.getMethod("isFactionClaim");
            isSoloPlayerClaimMethod = claimClass.getMethod("isSoloPlayerClaim");
            getPlayerOwnerIdMethod = claimClass.getMethod("getPlayerOwnerId");
            getGuildIdMethod = claimClass.getMethod("getGuildId");

            // PlayerDataRepository
            Class<?> playerDataRepoClass = Class.forName("com.hcfactions.database.repositories.PlayerDataRepository");
            getPlayerDataMethod = playerDataRepoClass.getMethod("getPlayerData", UUID.class);

            // PlayerData
            Class<?> playerDataClass = Class.forName("com.hcfactions.models.PlayerData");
            pdGetGuildIdMethod = playerDataClass.getMethod("getGuildId");

            claimCheckAvailable = true;
            LOGGER.at(Level.INFO).log("Claim check handles resolved - FARMING own-land check enabled");
        } catch (Exception e) {
            claimCheckAvailable = false;
            LOGGER.at(Level.WARNING).log("Could not resolve claim check handles (FARMING XP will be disabled): " + e.getMessage());
        }
    }

    private void onPickupAllowed(PlayerRef playerRef, String itemId) {
        if (actionXpService == null || playerRef == null || itemId == null) return;

        List<MatchedGrant> matches = actionXpService.findMatches(ActionType.PICKUP, itemId);
        if (matches.isEmpty()) return;

        // Filter: FARMING grants require own-land check
        List<MatchedGrant> allowed = new ArrayList<>();
        for (MatchedGrant grant : matches) {
            if ("FARMING".equals(grant.skillName())) {
                if (!isOnOwnClaim(playerRef)) continue;
            }
            allowed.add(grant);
        }

        if (!allowed.isEmpty()) {
            actionXpService.applyGrants(playerRef, allowed);
        }

        LOGGER.at(Level.FINE).log("%s picked up %s -> %d/%d grants applied",
            playerRef.getUsername(), itemId, allowed.size(), matches.size());
    }

    /**
     * Check if the player is standing on their own claimed land (solo or guild).
     * Uses reflection to access HC_Factions' ClaimManager.
     */
    private boolean isOnOwnClaim(PlayerRef playerRef) {
        if (!claimCheckAvailable) return false;

        try {
            // Get player entity ref and store
            var entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) return false;

            var store = entityRef.getStore();
            var externalData = store.getExternalData();
            if (externalData == null) return false;
            var world = externalData.getWorld();
            if (world == null) return false;

            TransformComponent transform = store.getComponent(entityRef,
                TransformComponent.getComponentType());
            if (transform == null) return false;

            // Convert to chunk coords
            double posX = transform.getPosition().getX();
            double posZ = transform.getPosition().getZ();
            int chunkX = (int) toChunkCoordMethod.invoke(null, posX);
            int chunkZ = (int) toChunkCoordMethod.invoke(null, posZ);

            // Get claim at position
            Object plugin = getInstanceMethod.invoke(null);
            Object claimManager = getClaimManagerMethod.invoke(plugin);
            String worldName = world.getName();
            Object claim = getClaimMethod.invoke(claimManager, worldName, chunkX, chunkZ);

            if (claim == null) return false; // Unclaimed land

            // Faction claims are not "own land"
            if ((boolean) isFactionClaimMethod.invoke(claim)) return false;

            UUID playerUuid = playerRef.getUuid();

            // Solo player claim - check if player is the owner
            if ((boolean) isSoloPlayerClaimMethod.invoke(claim)) {
                UUID ownerId = (UUID) getPlayerOwnerIdMethod.invoke(claim);
                return playerUuid.equals(ownerId);
            }

            // Guild claim - check if player is in the same guild
            UUID claimGuildId = (UUID) getGuildIdMethod.invoke(claim);
            if (claimGuildId == null) return false;

            Object playerDataRepo = getPlayerDataRepoMethod.invoke(plugin);
            Object playerData = getPlayerDataMethod.invoke(playerDataRepo, playerUuid);
            if (playerData == null) return false;

            UUID playerGuildId = (UUID) pdGetGuildIdMethod.invoke(playerData);
            return claimGuildId.equals(playerGuildId);

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Claim check failed for %s: %s",
                playerRef.getUsername(), e.getMessage());
            return false;
        }
    }
}
