package com.hcprofessions.interaction;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.managers.AllProfessionManager;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Level-gated version of the vanilla LearnRecipe interaction.
 * Checks the player's profession level against the recipe gate before teaching.
 * If the player doesn't meet the required level, the scroll is NOT consumed.
 */
public class GatedLearnRecipeInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public static final KeyedCodec<String> ITEM_ID = new KeyedCodec<>("ItemId", Codec.STRING);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final BuilderCodec CODEC = ((BuilderCodec.Builder)((BuilderCodec.Builder)BuilderCodec.builder(
            GatedLearnRecipeInteraction.class,
            GatedLearnRecipeInteraction::new,
            (BuilderCodec) SimpleInstantInteraction.CODEC)
        .documentation("Learns a recipe if the player meets the profession level requirement."))
        .append(new KeyedCodec("ItemId", Codec.STRING),
            (data, o) -> ((GatedLearnRecipeInteraction) data).itemId = (String) o,
            data -> ((GatedLearnRecipeInteraction) data).itemId).add())
        .build();

    @Nullable
    protected String itemId;

    @Override
    @Nonnull
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref ref = context.getEntity();
        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            LOGGER.at(Level.INFO).log("GatedLearnRecipeInteraction requires a Player but was used for: %s", ref);
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Resolve item ID from held item metadata or from the interaction field
        String resolvedItemId = null;
        ItemStack itemInHand = context.getHeldItem();
        if (itemInHand != null) {
            resolvedItemId = itemInHand.getFromMetadataOrNull(ITEM_ID);
        }
        if (resolvedItemId == null) {
            if (this.itemId == null) {
                playerRefComponent.sendMessage(Message.translation("server.modules.learnrecipe.noIdSet"));
                context.getState().state = InteractionState.Failed;
                return;
            }
            resolvedItemId = this.itemId;
        }

        // Check profession level gate
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin != null) {
            CraftingGateManager gateManager = plugin.getCraftingGateManager();
            AllProfessionManager allProfManager = plugin.getAllProfessionManager();

            if (gateManager != null && allProfManager != null) {
                RecipeGate gate = gateManager.getGate(resolvedItemId);
                if (gate != null && gate.enabled()) {
                    UUID playerUuid = playerRefComponent.getUuid();
                    int playerLevel = allProfManager.getLevel(playerUuid, gate.requiredProfession());
                    if (playerLevel < gate.requiredLevel()) {
                        String profName = gate.requiredProfession().getDisplayName();
                        playerRefComponent.sendMessage(
                            Message.raw("Requires " + profName + " Lv. " + gate.requiredLevel()
                                + " to learn this recipe! (You are Lv. " + playerLevel + ")")
                                .color(new Color(255, 80, 80)));
                        context.getState().state = InteractionState.Failed;
                        return;
                    }
                }
            }
        }

        // Level check passed (or no gate exists) — learn the recipe
        Item item = Item.getAssetMap().getAsset(resolvedItemId);
        Message itemNameMessage = item != null ? Message.translation(item.getTranslationKey()) : Message.raw("?");

        if (CraftingPlugin.learnRecipe(ref, resolvedItemId, commandBuffer)) {
            playerRefComponent.sendMessage(
                Message.translation("server.modules.learnrecipe.success").param("name", itemNameMessage));
            return;
        }

        playerRefComponent.sendMessage(
            Message.translation("server.modules.learnrecipe.alreadyKnown").param("name", itemNameMessage));
        context.getState().state = InteractionState.Failed;
    }

    @Override
    @Nonnull
    public String toString() {
        return "GatedLearnRecipeInteraction{itemId=" + this.itemId + "} " + super.toString();
    }
}
