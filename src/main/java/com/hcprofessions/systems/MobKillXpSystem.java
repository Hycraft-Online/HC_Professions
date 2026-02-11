package com.hcprofessions.systems;

import com.hcprofessions.models.ActionType;
import com.hcprofessions.services.ActionXpService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.logging.Level;

public class MobKillXpSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-MobKill");

    private ActionXpService actionXpService;

    public void initialize(ActionXpService actionXpService) {
        this.actionXpService = actionXpService;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return DeathComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (actionXpService == null) return;

        // Get death info
        Damage deathInfo = component.getDeathInfo();
        if (deathInfo == null) return;

        // Check if killed by a player
        if (!(deathInfo.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> killerRef = entitySource.getRef();
        if (killerRef == null || !killerRef.isValid()) return;

        PlayerRef killerPlayerRef = commandBuffer.getComponent(killerRef, PlayerRef.getComponentType());
        if (killerPlayerRef == null) return;

        // Skip player kills and self-kills
        PlayerRef victimPlayerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (victimPlayerRef != null) return;

        // Get the NPC role name as the identifier
        NPCEntity victimNPC = store.getComponent(ref, NPCEntity.getComponentType());
        if (victimNPC == null) return;

        String roleName = victimNPC.getRoleName();
        if (roleName == null || roleName.isEmpty()) return;

        actionXpService.onAction(killerPlayerRef, ActionType.KILL, roleName);

        LOGGER.at(Level.FINE).log("%s killed %s -> checking action XP",
            killerPlayerRef.getUsername(), roleName);
    }
}
