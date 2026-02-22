package com.hcprofessions.interaction;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.pages.TemperingBenchPage;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.state.BenchState;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

public class ProfessionBenchInteraction extends SimpleBlockInteraction {

    private static final HytaleLogger LOGGER =
            HytaleLogger.getLogger().getSubLogger("HC_Professions-BenchInteraction");

    public ProfessionBenchInteraction(@Nonnull String id) {
        super(id);
    }

    @Override
    protected void interactWithBlock(@Nonnull World world,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull InteractionType type,
                                     @Nonnull InteractionContext context,
                                     @Nullable ItemStack itemInHand,
                                     @Nonnull Vector3i targetBlock,
                                     @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        CraftingManager cm = commandBuffer.getComponent(ref, CraftingManager.getComponentType());
        if (cm == null || cm.hasBenchSet()) return;

        BlockState blockState = world.getState(targetBlock.x, targetBlock.y, targetBlock.z, true);
        if (!(blockState instanceof BenchState benchState)) return;

        String benchId = benchState.getBench().getId();
        LOGGER.at(Level.INFO).log("ProfessionBenchInteraction triggered for bench: %s", benchId);

        if ("Tempering_Bench".equals(benchId)) {
            PlayerRef playerRef = player.getPlayerRef();
            HC_ProfessionsPlugin profPlugin = HC_ProfessionsPlugin.getInstance();

            TemperingBenchPage page = new TemperingBenchPage(playerRef, profPlugin);
            player.getPageManager().openCustomPage(ref, store, page);
        } else {
            // Knowledge-gated crafting window — filters recipes by knowledgeRequired server-side
            CraftingWindow window = new KnowledgeGatedCraftingWindow(benchState);
            UUIDComponent uuidComp = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            UUID uuid = uuidComp.getUuid();
            if (benchState.getWindows().putIfAbsent(uuid, window) == null) {
                window.registerCloseEvent(e -> benchState.getWindows().remove(uuid, window));
            }
            player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type,
                                             @Nonnull InteractionContext context,
                                             @Nullable ItemStack itemInHand,
                                             @Nonnull World world,
                                             @Nonnull Vector3i targetBlock) {
        // No client simulation needed
    }
}
