package com.hcprofessions.interaction;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.models.Profession;
import com.hcprofessions.pages.TemperingBenchPage;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.state.BenchState;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.UUID;

public class ProfessionBenchInteraction extends SimpleBlockInteraction {

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

        if ("Tempering_Bench".equals(benchId)) {
            // Open tempering bench page (requires a profession)
            PlayerRef playerRef = player.getPlayerRef();
            HC_ProfessionsPlugin profPlugin = HC_ProfessionsPlugin.getInstance();
            ProfessionManager profManager = profPlugin.getProfessionManager();
            Profession profession = profManager.getProfession(playerRef.getUuid());

            if (profession == null) {
                playerRef.sendMessage(Message.raw("Choose a profession first! Use /profession choose").color(Color.ORANGE));
                return;
            }

            TemperingBenchPage page = new TemperingBenchPage(playerRef, profession, profPlugin);
            player.getPageManager().openCustomPage(ref, store, page);
        } else {
            // Vanilla fallback - same as OpenBenchPageInteraction
            CraftingWindow window = new SimpleCraftingWindow(benchState);
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
