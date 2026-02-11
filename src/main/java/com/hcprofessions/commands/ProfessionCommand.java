package com.hcprofessions.commands;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.config.XPCurve;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.models.PlayerProfessionData;
import com.hcprofessions.models.Profession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;

public class ProfessionCommand extends AbstractPlayerCommand {

    private final HC_ProfessionsPlugin plugin;

    public ProfessionCommand(HC_ProfessionsPlugin plugin) {
        super("profession", "Profession commands: choose, info, respec");
        this.addAliases("prof");
        this.setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = ctx.getInputString();
        String[] parts = input.split("\\s+");

        if (parts.length < 2) {
            showUsage(ctx);
            return;
        }

        String subcommand = parts[1].toLowerCase();
        switch (subcommand) {
            case "choose" -> handleChoose(ctx, playerRef, world, parts);
            case "info" -> handleInfo(ctx, playerRef);
            case "respec" -> handleRespec(ctx, playerRef);
            default -> showUsage(ctx);
        }
    }

    private void handleChoose(CommandContext ctx, PlayerRef playerRef, World world, String[] parts) {
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Available professions:").color(Color.YELLOW));
            for (Profession prof : Profession.values()) {
                ctx.sendMessage(Message.raw("  - " + prof.getDisplayName()).color(prof.getColor()));
            }
            ctx.sendMessage(Message.raw("Usage: /profession choose <name>").color(Color.GRAY));
            return;
        }

        String profName = parts[2];
        Profession profession = Profession.fromString(profName);
        if (profession == null) {
            ctx.sendMessage(Message.raw("Unknown profession: " + profName).color(Color.RED));
            StringBuilder available = new StringBuilder("Available: ");
            for (Profession p : Profession.values()) {
                available.append(p.getDisplayName()).append(", ");
            }
            ctx.sendMessage(Message.raw(available.substring(0, available.length() - 2)).color(Color.GRAY));
            return;
        }

        plugin.getProfessionManager().chooseProfession(playerRef.getUuid(), profession, playerRef, world);
    }

    private void handleInfo(CommandContext ctx, PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        ProfessionManager profManager = plugin.getProfessionManager();
        PlayerProfessionData data = profManager.getPlayerData(uuid);

        ctx.sendMessage(Message.raw("=== Profession Info ===").color(Color.YELLOW));

        if (!data.hasProfession()) {
            ctx.sendMessage(Message.raw("No profession chosen. Use /profession choose <name>").color(Color.GRAY));
            return;
        }

        Profession prof = data.getProfession();
        ctx.sendMessage(Message.raw("Profession: " + prof.getDisplayName()).color(prof.getColor()));
        ctx.sendMessage(Message.raw("Level: " + data.getLevel() + " / " + XPCurve.getMaxLevel()).color(Color.WHITE));

        if (data.getLevel() < XPCurve.getMaxLevel()) {
            long needed = XPCurve.getXpToNextLevel(data.getLevel());
            ctx.sendMessage(Message.raw("XP: " + String.format("%,d / %,d", data.getCurrentXp(), needed)).color(Color.WHITE));
        } else {
            ctx.sendMessage(Message.raw("XP: MAX LEVEL").color(new Color(255, 215, 0)));
        }

        ctx.sendMessage(Message.raw("Items Crafted: " + data.getTotalItemsCrafted()).color(Color.WHITE));
        ctx.sendMessage(Message.raw("Total XP Earned: " + String.format("%,d", data.getTotalXpEarned())).color(Color.GRAY));
        if (data.getRespecCount() > 0) {
            ctx.sendMessage(Message.raw("Respecs: " + data.getRespecCount()).color(Color.GRAY));
        }
    }

    private void handleRespec(CommandContext ctx, PlayerRef playerRef) {
        plugin.getProfessionManager().respec(playerRef.getUuid(), playerRef);
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Profession Commands ===").color(Color.YELLOW));
        ctx.sendMessage(Message.raw("/profession choose <name> - Choose a profession").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profession info - View your profession info").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profession respec - Reset your profession").color(Color.WHITE));
    }
}
