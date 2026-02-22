package com.hcprofessions.pages;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.models.Profession;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;

public class ProfessionSelectionPage extends InteractiveCustomUIPage<ProfessionSelectionPage.SelectionEventData> {

    private final HC_ProfessionsPlugin plugin;

    public ProfessionSelectionPage(@NonNullDecl HC_ProfessionsPlugin plugin, @NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, SelectionEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/ProfessionSelection.ui");

        // Set dynamic text for names and descriptions, hide disabled professions
        for (Profession prof : Profession.values()) {
            String enumName = prof.name();
            String idSuffix = enumName.charAt(0) + enumName.substring(1).toLowerCase();

            if (!prof.isEnabled()) {
                cmd.set("#Card" + idSuffix + ".Visible", false);
                continue;
            }

            cmd.set("#Name" + idSuffix + ".TextSpans", Message.raw(prof.getDisplayName()));
            cmd.set("#Desc" + idSuffix + ".TextSpans", Message.raw(prof.getDescription()));

            // Bind the button click
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Btn" + idSuffix,
                EventData.of("Profession", prof.name()),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl SelectionEventData data) {
        super.handleDataEvent(ref, store, data);

        if (data.profession == null) return;

        Profession profession = Profession.fromString(data.profession);
        if (profession == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        World world = store.getExternalData().getWorld();
        boolean success = plugin.getProfessionManager().chooseProfession(
            playerRef.getUuid(), profession, playerRef, world
        );

        if (success) {
            // Show title banner
            EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw(profession.getDisplayName()).color(profession.getColor()),
                Message.raw("Profession Selected").color(Color.WHITE),
                true,
                null,
                3.0f,
                0.5f,
                1.0f
            );

            // Play sound
            int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Discovery_Z1_Medium");
            if (soundIndex != Integer.MIN_VALUE) {
                SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
            }
        }

        this.close();
    }

    public static class SelectionEventData {
        public static final BuilderCodec<SelectionEventData> CODEC = BuilderCodec.<SelectionEventData>builder(
                SelectionEventData.class, SelectionEventData::new)
            .addField(new KeyedCodec<>("Profession", Codec.STRING),
                (d, s) -> d.profession = s, d -> d.profession)
            .build();

        private String profession;
    }
}
