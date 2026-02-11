package com.hcprofessions.commands;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.config.XPCurve;
import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.PlayerTradeskillData;
import com.hcprofessions.models.Tradeskill;
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
import java.util.Map;
import java.util.UUID;

public class TradeskillCommand extends AbstractPlayerCommand {

    private final HC_ProfessionsPlugin plugin;

    public TradeskillCommand(HC_ProfessionsPlugin plugin) {
        super("tradeskill", "View tradeskill levels");
        this.addAliases("ts", "skills");
        this.setAllowsExtraArguments(true);
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        UUID uuid = playerRef.getUuid();
        TradeskillManager tsManager = plugin.getTradeskillManager();
        Map<Tradeskill, PlayerTradeskillData> allData = tsManager.getPlayerData(uuid);

        ctx.sendMessage(Message.raw("=== Tradeskills ===").color(Color.YELLOW));

        for (Tradeskill skill : Tradeskill.values()) {
            PlayerTradeskillData data = allData.get(skill);
            int level = data != null ? data.getLevel() : 0;

            String progress;
            if (level >= XPCurve.getMaxLevel()) {
                progress = "MAX";
            } else {
                long current = data != null ? data.getCurrentXp() : 0;
                long needed = XPCurve.getXpToNextLevel(level);
                progress = String.format("%,d / %,d XP", current, needed);
            }

            ctx.sendMessage(Message.raw(
                String.format("  %s: Lv. %d (%s)", skill.getDisplayName(), level, progress)
            ).color(skill.getColor()));
        }
    }
}
