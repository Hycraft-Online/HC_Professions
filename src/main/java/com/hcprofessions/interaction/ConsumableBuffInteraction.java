package com.hcprofessions.interaction;

import com.hcattributes.api.HC_BuffsAPI;
import com.hcattributes.buffs.BuffInstance;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Custom interaction for consumable items that apply HC_Attributes buffs.
 * Used as part of the consumption chain (Root_Secondary_Consume_Potion/Food).
 * The item is consumed by the chain itself - this interaction just applies the buff.
 *
 * Usage in item JSON InteractionVars.Effect.Interactions:
 * {
 *   "Type": "ConsumableBuff",
 *   "BuffId": "elixir_of_might"
 * }
 */
public class ConsumableBuffInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final BuilderCodec CODEC = ((BuilderCodec.Builder)((BuilderCodec.Builder)BuilderCodec.builder(
            ConsumableBuffInteraction.class,
            ConsumableBuffInteraction::new,
            (BuilderCodec) SimpleInstantInteraction.CODEC)
        .documentation("Applies an HC_Attributes buff when a consumable item is used."))
        .append(new KeyedCodec("BuffId", Codec.STRING),
            (data, o) -> ((ConsumableBuffInteraction) data).buffId = (String) o,
            data -> ((ConsumableBuffInteraction) data).buffId).add())
        .build();

    protected String buffId;

    @Override
    protected void firstRun(@Nonnull InteractionType interactionType, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        CommandBuffer buffer = context.getCommandBuffer();
        if (buffer == null) {
            return;
        }

        Player player = (Player) buffer.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        UUID playerUuid = player.getUuid();

        if (buffId == null || buffId.isEmpty()) {
            LOGGER.at(Level.WARNING).log("[ConsumableBuff] BuffId is null or empty");
            return;
        }

        // Derive buff icon from the consumed item's icon.
        // Item icons are copied to Common/UI/Custom/Icons/Buffs/ (same dir as BuffIndicatorHud.ui icons).
        // Extract the filename from the item's icon path and use Icons/Buffs/ prefix.
        String iconOverride = null;
        Item itemType = context.getOriginalItemType();
        if (itemType != null) {
            String itemIcon = itemType.getIcon();
            if (itemIcon != null && !itemIcon.isEmpty()) {
                String filename = itemIcon.substring(itemIcon.lastIndexOf('/') + 1);
                iconOverride = "Icons/Buffs/" + filename;
            }
        }

        BuffInstance applied = HC_BuffsAPI.applyBuff(buffId, playerUuid, playerUuid, iconOverride);
        if (applied != null) {
            LOGGER.at(Level.FINE).log("[ConsumableBuff] Applied buff '%s' to %s", buffId, player.getDisplayName());
        } else {
            LOGGER.at(Level.WARNING).log("[ConsumableBuff] Failed to apply buff '%s' - not found in registry", buffId);
        }
    }
}
