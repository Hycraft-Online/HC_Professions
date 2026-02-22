package com.hcprofessions.systems;

import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.ActionType;
import com.hcprofessions.models.Tradeskill;
import com.hcprofessions.services.ActionXpService;
import com.hcprofessions.services.ActionXpService.MatchedGrant;
import com.hcprofessions.models.SkillTarget;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

public class GatheringTradeskillXPSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-Gathering");

    private ActionXpService actionXpService;
    private TradeskillManager tradeskillManager;
    private final Random random = new Random();

    public GatheringTradeskillXPSystem() {
        super(BreakBlockEvent.class);
    }

    public void initialize(ActionXpService actionXpService, TradeskillManager tradeskillManager) {
        this.actionXpService = actionXpService;
        this.tradeskillManager = tradeskillManager;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

        if (actionXpService == null || tradeskillManager == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        String blockName = event.getBlockType().getId().toLowerCase();

        // Anti-exploit: ore blocks must be ore_{material} or ore_{material}_{rocktype}
        // only (max 3 segments). Blocks like ore_adamantite_magma_cracked (4 segments)
        // are decorative/cracked variants that should not grant tradeskill XP.
        if (blockName.startsWith("ore_") && blockName.chars().filter(c -> c == '_').count() > 2) {
            return;
        }

        List<MatchedGrant> matches = actionXpService.findMatches(ActionType.GATHER, blockName);

        if (matches.isEmpty()) return;

        // Check min level gating — if any match requires a level the player doesn't have, cancel the break
        for (MatchedGrant grant : matches) {
            if (grant.minLevel() > 0 && grant.skillType() == SkillTarget.TRADESKILL && grant.skillName() != null) {
                Tradeskill tradeskill = Tradeskill.fromString(grant.skillName());
                if (tradeskill != null) {
                    int playerLevel = tradeskillManager.getLevel(playerRef.getUuid(), tradeskill);
                    if (playerLevel < grant.minLevel()) {
                        event.setCancelled(true);
                        Message msg = Message.raw("Requires " + tradeskill.getDisplayName() + " Lv. " + grant.minLevel())
                            .color(new Color(255, 80, 80));
                        try {
                            NotificationUtil.sendNotification(playerRef.getPacketHandler(), msg, NotificationStyle.Default);
                        } catch (Exception ignored) {}
                        return;
                    }
                }
            }
        }

        // Grant XP via the unified service
        actionXpService.applyGrants(playerRef, matches);

        // Yield bonus: chance for double drops based on tradeskill level
        // Use first tradeskill match for bonus drop calculation
        for (MatchedGrant grant : matches) {
            if (grant.skillType() == SkillTarget.TRADESKILL && grant.skillName() != null) {
                Tradeskill tradeskill = Tradeskill.fromString(grant.skillName());
                if (tradeskill != null) {
                    int playerLevel = tradeskillManager.getLevel(playerRef.getUuid(), tradeskill);
                    if (playerLevel > 0 && random.nextDouble() < (playerLevel / 100.0)) {
                        spawnBonusDrops(event, store);
                    }
                    break;
                }
            }
        }
    }

    private void spawnBonusDrops(BreakBlockEvent event, Store<EntityStore> store) {
        try {
            BlockType blockType = event.getBlockType();
            BlockGathering gathering = blockType.getGathering();
            if (gathering == null) return;

            String itemId = null;
            String dropListId = null;
            int quantity = 1;

            // Try breaking drops first, then soft block drops
            if (gathering.getBreaking() != null) {
                itemId = gathering.getBreaking().getItemId();
                dropListId = gathering.getBreaking().getDropListId();
                quantity = gathering.getBreaking().getQuantity();
            } else if (gathering.getSoft() != null) {
                itemId = gathering.getSoft().getItemId();
                dropListId = gathering.getSoft().getDropListId();
            }

            if (itemId == null && dropListId == null) return;

            List<ItemStack> drops = BlockHarvestUtils.getDrops(blockType, quantity, itemId, dropListId);
            if (drops == null || drops.isEmpty()) return;

            // Calculate drop position at block center
            Vector3i blockPos = event.getTargetBlock();
            Vector3d pos = new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);

            // Schedule spawn outside archetype iteration
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                Store<EntityStore> s = world.getEntityStore().getStore();
                Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(s, drops, pos, Vector3f.ZERO);
                s.addEntities(holders, AddReason.SPAWN);
            });
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to spawn bonus drops: " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @NonNullDecl
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
