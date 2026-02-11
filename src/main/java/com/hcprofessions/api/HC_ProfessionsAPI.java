package com.hcprofessions.api;

import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.models.PlayerTradeskillData;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.Tradeskill;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public class HC_ProfessionsAPI {

    private HC_ProfessionsAPI() {}

    public static boolean isAvailable() {
        return HC_ProfessionsPlugin.getInstance() != null;
    }

    // ═══════════════════════════════════════════════════════
    // TRADESKILL API
    // ═══════════════════════════════════════════════════════

    public static int getTradeskillLevel(UUID playerUuid, Tradeskill tradeskill) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getTradeskillManager() == null) return 0;
        return plugin.getTradeskillManager().getLevel(playerUuid, tradeskill);
    }

    @Nullable
    public static Map<Tradeskill, PlayerTradeskillData> getAllTradeskills(UUID playerUuid) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getTradeskillManager() == null) return null;
        return plugin.getTradeskillManager().getPlayerData(playerUuid);
    }

    // ═══════════════════════════════════════════════════════
    // PROFESSION API
    // ═══════════════════════════════════════════════════════

    @Nullable
    public static Profession getProfession(UUID playerUuid) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getProfessionManager() == null) return null;
        return plugin.getProfessionManager().getProfession(playerUuid);
    }

    public static int getProfessionLevel(UUID playerUuid) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getProfessionManager() == null) return 0;
        return plugin.getProfessionManager().getLevel(playerUuid);
    }

    public static boolean hasProfession(UUID playerUuid) {
        return getProfession(playerUuid) != null;
    }

    // ═══════════════════════════════════════════════════════
    // CRAFTING GATE API
    // ═══════════════════════════════════════════════════════

    public static boolean isRecipeGated(String outputItemId) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getCraftingGateManager() == null) return false;
        return plugin.getCraftingGateManager().isGated(outputItemId);
    }

    public static boolean canCraft(UUID playerUuid, String outputItemId) {
        HC_ProfessionsPlugin plugin = HC_ProfessionsPlugin.getInstance();
        if (plugin == null || plugin.getCraftingGateManager() == null) return true;
        return plugin.getCraftingGateManager().checkPermission(playerUuid, outputItemId).isAllowed();
    }
}
