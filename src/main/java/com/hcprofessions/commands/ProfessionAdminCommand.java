package com.hcprofessions.commands;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.Tradeskill;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class ProfessionAdminCommand extends AbstractAsyncCommand {

    private final HC_ProfessionsPlugin plugin;

    public ProfessionAdminCommand(HC_ProfessionsPlugin plugin) {
        super("profadmin", "Profession admin commands");
        this.setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
        String input = ctx.getInputString();
        String[] parts = input.split("\\s+");

        if (parts.length < 2) {
            showUsage(ctx);
            return CompletableFuture.completedFuture(null);
        }

        String subcommand = parts[1].toLowerCase();
        return this.runAsync(ctx, () -> {
            switch (subcommand) {
                case "setlevel" -> handleSetLevel(ctx, parts);
                case "grant" -> handleGrant(ctx, parts);
                case "reload" -> handleReload(ctx);
                default -> showUsage(ctx);
            }
        }, ForkJoinPool.commonPool());
    }

    private void handleSetLevel(CommandContext ctx, String[] parts) {
        // /profadmin setlevel <player> <profession|tradeskill> <level>
        if (parts.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /profadmin setlevel <player> <profession|tradeskill> <level>").color(Color.RED));
            return;
        }

        String playerName = parts[2];
        String skillName = parts[3];
        int level;
        try {
            level = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid level: " + parts[4]).color(Color.RED));
            return;
        }

        PlayerRef targetRef = findPlayer(playerName);
        if (targetRef == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color(Color.RED));
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        // Check if it's a profession
        if (skillName.equalsIgnoreCase("profession") || skillName.equalsIgnoreCase("prof")) {
            plugin.getProfessionManager().setLevel(targetUuid, level);
            ctx.sendMessage(Message.raw("Set " + playerName + "'s profession level to " + level).color(Color.GREEN));
            return;
        }

        // Check if it's a tradeskill
        Tradeskill tradeskill = Tradeskill.fromString(skillName);
        if (tradeskill != null) {
            plugin.getTradeskillManager().setLevel(targetUuid, tradeskill, level);
            ctx.sendMessage(Message.raw("Set " + playerName + "'s " + tradeskill.getDisplayName() + " to level " + level).color(Color.GREEN));
            return;
        }

        ctx.sendMessage(Message.raw("Unknown skill: " + skillName + ". Use profession/mining/woodcutting/farming/fishing").color(Color.RED));
    }

    private void handleGrant(CommandContext ctx, String[] parts) {
        // /profadmin grant <player> <profession|tradeskill> <xp>
        if (parts.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /profadmin grant <player> <profession|tradeskill> <xp>").color(Color.RED));
            return;
        }

        String playerName = parts[2];
        String skillName = parts[3];
        int xp;
        try {
            xp = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid XP amount: " + parts[4]).color(Color.RED));
            return;
        }

        PlayerRef targetRef = findPlayer(playerName);
        if (targetRef == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color(Color.RED));
            return;
        }

        if (skillName.equalsIgnoreCase("profession") || skillName.equalsIgnoreCase("prof")) {
            plugin.getProfessionManager().grantXp(targetRef, xp);
            ctx.sendMessage(Message.raw("Granted " + xp + " profession XP to " + playerName).color(Color.GREEN));
            return;
        }

        Tradeskill tradeskill = Tradeskill.fromString(skillName);
        if (tradeskill != null) {
            plugin.getTradeskillManager().grantXp(targetRef, tradeskill, xp);
            ctx.sendMessage(Message.raw("Granted " + xp + " " + tradeskill.getDisplayName() + " XP to " + playerName).color(Color.GREEN));
            return;
        }

        ctx.sendMessage(Message.raw("Unknown skill: " + skillName).color(Color.RED));
    }

    private void handleReload(CommandContext ctx) {
        plugin.reloadAll();
        ctx.sendMessage(Message.raw("Reloaded all profession config from database.").color(Color.GREEN));
    }

    private PlayerRef findPlayer(String name) {
        for (World world : Universe.get().getWorlds().values()) {
            for (Player player : world.getPlayers()) {
                PlayerRef playerRef = player.getPlayerRef();
                if (playerRef != null && playerRef.getUsername().equalsIgnoreCase(name)) {
                    return playerRef;
                }
            }
        }
        return null;
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== ProfAdmin Commands ===").color(Color.YELLOW));
        ctx.sendMessage(Message.raw("/profadmin setlevel <player> <profession|tradeskill> <level>").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin grant <player> <profession|tradeskill> <xp>").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin reload - Reload config from DB").color(Color.WHITE));
    }
}
