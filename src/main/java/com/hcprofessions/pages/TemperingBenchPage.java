package com.hcprofessions.pages;

import com.hcequipment.api.HC_EquipmentAPI;
import com.hcequipment.models.ArmorType;
import com.hcequipment.models.ItemRarity;
import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.managers.AllProfessionManager;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.models.Profession;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public class TemperingBenchPage extends InteractiveCustomUIPage<TemperingBenchPage.TemperEventData> {

    private final HC_ProfessionsPlugin plugin;
    private int selectedIndex = -1;
    private final Random random = new Random();

    // Cached temperable items
    private final List<TemperableEntry> temperableItems = new ArrayList<>();

    private static final String[] ROMAN_NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final int ITEM_LEVEL_RANGE = 10;

    /**
     * Derives temper stone ID from item level.
     * Every 5 item levels maps to one tier: I (1-5), II (6-10), ... X (46-50).
     */
    private static String getTemperStoneForLevel(int itemLevel) {
        int tier = Math.clamp((itemLevel - 1) / 5, 0, 9);
        return "TemperStone_" + ROMAN_NUMERALS[tier];
    }

    /** "TemperStone_IV" -> "IV" */
    private static String getStoneTierName(String stoneId) {
        return stoneId.replace("TemperStone_", "");
    }

    /** "TemperStone_IV" -> "Temper Stone IV" */
    private static String getStoneName(String stoneId) {
        return "Temper Stone " + getStoneTierName(stoneId);
    }

    /**
     * Determine which profession is relevant for tempering this item.
     * Weapons -> WEAPONSMITH (Bladesmith), Plate -> ARMORSMITH (Platesmith),
     * Leather -> LEATHERWORKER, Cloth -> TAILOR.
     */
    private static Profession getRelevantProfession(String itemId, String equipType) {
        if ("WEAPON".equals(equipType)) return Profession.WEAPONSMITH;
        // ARMOR -> check armor_type via HC_EquipmentAPI
        ArmorType armorType = HC_EquipmentAPI.getArmorType(itemId);
        if (armorType == null) return Profession.ARMORSMITH; // fallback to plate
        return switch (armorType) {
            case PLATE -> Profession.ARMORSMITH;
            case LEATHER -> Profession.LEATHERWORKER;
            case CLOTH -> Profession.TAILOR;
        };
    }

    public TemperingBenchPage(@Nonnull PlayerRef playerRef, @Nonnull HC_ProfessionsPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, TemperEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                     @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/TemperingBench.ui");

        cmd.set("#TitleText.Text", "Tempering Bench");

        // Scan inventory for temperable items
        scanInventory(store);

        // Build item list
        buildItemList(cmd, events);

        // Build details panel
        buildDetailsPanel(cmd, events);

        // Close button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "Close"), false);
    }

    private void scanInventory(@Nonnull Store<EntityStore> store) {
        temperableItems.clear();
        Player player = getPlayer(store);
        if (player == null || player.getInventory() == null) return;

        CombinedItemContainer container = player.getInventory().getCombinedEverything();
        int capacity = container.getCapacity();
        UUID playerUuid = playerRef.getUuid();
        CraftingGateManager gateManager = plugin.getCraftingGateManager();
        AllProfessionManager allProfManager = plugin.getAllProfessionManager();

        for (int i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack((short) i);
            if (stack == null || stack.isEmpty()) continue;

            String itemId = stack.getItemId();

            // Skip already-tempered items (have RPG metadata or HC_ prefix)
            if (HC_EquipmentAPI.isTempered(stack)) continue;

            // Check if this is a known vanilla equipment item
            if (!HC_EquipmentAPI.isVanillaEquipment(itemId)) continue;

            // No profession filter -- all equipment types shown
            String equipType = HC_EquipmentAPI.getVanillaEquipmentType(itemId);

            String material = HC_EquipmentAPI.getVanillaMaterial(itemId);
            String displayName = HC_EquipmentAPI.getVanillaDisplayName(itemId);
            if (material == null || displayName == null) continue;

            // Derive temper stone from item level (not material name)
            int baseItemLevel = HC_EquipmentAPI.getVanillaItemLevel(itemId);
            String stoneId = getTemperStoneForLevel(baseItemLevel);
            String stoneTier = getStoneTierName(stoneId);

            // Determine which profession is relevant for this item
            Profession relevantProf = getRelevantProfession(itemId, equipType);
            int profLevel = allProfManager.getLevel(playerUuid, relevantProf);

            // Require profession level >= base item level to temper
            int requiredProfLevel = baseItemLevel;
            boolean tierGated = profLevel < requiredProfLevel;

            // Check gate using AllProfessionManager (level in relevant profession)
            CraftingGateManager.GateCheck gateCheck = gateManager.checkTemperPermission(
                playerUuid, itemId, allProfManager);

            temperableItems.add(new TemperableEntry(i, stack, itemId, displayName, material,
                baseItemLevel, stoneId, stoneTier,
                equipType, relevantProf, profLevel, requiredProfLevel, tierGated, gateCheck));
        }
    }

    private void buildItemList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.clear("#ItemList");

        if (temperableItems.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#EmptyState.Text", "No untempered equipment found");
            return;
        }

        for (int i = 0; i < temperableItems.size(); i++) {
            TemperableEntry entry = temperableItems.get(i);
            String selector = "#ItemList[" + i + "]";
            cmd.append("#ItemList", "Pages/TemperableItem.ui");

            cmd.set(selector + " #ItemSlot.ItemId", entry.vanillaId);
            cmd.set(selector + " #ItemRowName.TextSpans", Message.raw(entry.displayName));

            // Show tier info + relevant profession + level
            String profName = entry.relevantProfession.getDisplayName();
            // Show tier name when it differs from material (themed items)
            String tierLabel = entry.stoneTier.equalsIgnoreCase(entry.material)
                ? entry.material
                : entry.material + " [" + entry.stoneTier + " Tier]";
            String subtitle;
            java.awt.Color subtitleColor;
            if (entry.tierGated) {
                subtitle = tierLabel + " - Requires " + profName + " Lv. " + entry.requiredProfLevel;
                subtitleColor = java.awt.Color.RED;
            } else if (!entry.gateCheck.isAllowed() && entry.gateCheck.gate() != null) {
                subtitle = tierLabel + " - Requires " + profName + " Lv. " + entry.gateCheck.gate().requiredLevel();
                subtitleColor = java.awt.Color.RED;
            } else {
                subtitle = tierLabel + " (" + profName + " Lv. " + entry.profLevel + ")";
                subtitleColor = java.awt.Color.LIGHT_GRAY;
            }
            cmd.set(selector + " #ItemRowSubtitle.TextSpans",
                Message.raw(subtitle).color(subtitleColor));

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #ItemRow",
                EventData.of("Action", "SelectItem").append("Index", String.valueOf(i)), false);
        }
    }

    private void buildDetailsPanel(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        if (selectedIndex < 0 || selectedIndex >= temperableItems.size()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#EmptyState.Text", "Select an item to temper");
            cmd.set("#PreviewSection.Visible", false);
            return;
        }

        cmd.set("#EmptyState.Visible", false);
        cmd.set("#PreviewSection.Visible", true);

        TemperableEntry entry = temperableItems.get(selectedIndex);

        // Item header
        cmd.set("#PreviewSlot.ItemId", entry.vanillaId);
        cmd.set("#ItemName.TextSpans", Message.raw(entry.displayName));
        String subtitleText = entry.stoneTier.equalsIgnoreCase(entry.material)
            ? entry.material + " (Untempered)"
            : entry.material + " [" + entry.stoneTier + " Tier] (Untempered)";
        cmd.set("#ItemSubtitle.TextSpans", Message.raw(subtitleText).color(java.awt.Color.LIGHT_GRAY));

        // Quality preview based on mastery ratio
        int profLevel = entry.profLevel;
        int baseILvl = entry.baseItemLevel;
        double ratio = baseILvl > 0 ? (double) profLevel / baseILvl : 1.0;
        int mastery = (int) (ratio * 100);

        String maxRarity;
        if (ratio >= 2.0) maxRarity = "Epic";
        else if (ratio >= 1.5) maxRarity = "Rare";
        else if (ratio >= 1.25) maxRarity = "Uncommon";
        else maxRarity = "Common";

        int maxILvl = baseILvl + (int) (Math.min(Math.max(ratio - 1.0, 0.0), 1.0) * ITEM_LEVEL_RANGE);

        StringBuilder qualityText = new StringBuilder();
        qualityText.append(entry.relevantProfession.getDisplayName()).append(" Lv. ").append(profLevel);
        qualityText.append(" (").append(mastery).append("% mastery)");
        qualityText.append("  |  Up to ").append(maxRarity);
        qualityText.append("  |  iLvl ").append(baseILvl);
        if (maxILvl > baseILvl) {
            qualityText.append("-").append(maxILvl);
        }
        cmd.set("#StatsTitle.TextSpans", Message.raw(qualityText.toString()));

        // Cost section - one temper stone derived from item level
        cmd.set("#CostSlot.ItemId", entry.stoneId);
        cmd.set("#CostText.TextSpans", Message.raw("1x " + getStoneName(entry.stoneId)));

        // Hide component section (no longer used)
        cmd.set("#ComponentSection.Visible", false);

        // Gate check messages (stone tier gate takes priority)
        if (entry.tierGated) {
            String gateMsg = "Requires " + entry.relevantProfession.getDisplayName()
                + " Lv. " + entry.requiredProfLevel + " to temper this item";
            cmd.set("#ResultText.TextSpans", Message.raw(gateMsg).color(java.awt.Color.RED));
        } else if (!entry.gateCheck.isAllowed() && entry.gateCheck.gate() != null) {
            String gateMsg = "Requires " + entry.gateCheck.gate().requiredProfession().getDisplayName()
                + " Lv. " + entry.gateCheck.gate().requiredLevel();
            cmd.set("#ResultText.TextSpans", Message.raw(gateMsg).color(java.awt.Color.RED));
        } else {
            cmd.set("#ResultText.TextSpans",
                Message.raw("Tempering adds affixes and stats to this item.").color(java.awt.Color.GRAY));
        }

        // Temper button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TemperButton",
            EventData.of("Action", "Temper"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull TemperEventData data) {
        if (data.action == null) return;

        switch (data.action) {
            case "SelectItem" -> {
                if (data.index != null) {
                    try {
                        int idx = Integer.parseInt(data.index);
                        if (idx >= 0 && idx < temperableItems.size()) {
                            selectedIndex = idx;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                rebuild();
            }
            case "Temper" -> {
                handleTemper(ref, store);
                rebuild();
            }
            case "Close" -> {
                close();
                return;
            }
        }
    }

    private void handleTemper(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (selectedIndex < 0 || selectedIndex >= temperableItems.size()) return;

        Player player = getPlayer(store);
        if (player == null || player.getInventory() == null) {
            showMessage("Error: Could not access inventory!", java.awt.Color.RED);
            return;
        }

        TemperableEntry entry = temperableItems.get(selectedIndex);
        CombinedItemContainer container = player.getInventory().getCombinedEverything();

        // Verify item is still in the slot
        ItemStack current = container.getItemStack((short) entry.slotIndex);
        if (current == null || current.isEmpty() || !current.getItemId().equals(entry.vanillaId)) {
            showMessage("Item no longer in that slot!", java.awt.Color.RED);
            selectedIndex = -1;
            scanInventory(store);
            return;
        }

        // Check profession level requirement (must meet base item level)
        UUID playerUuid = playerRef.getUuid();
        AllProfessionManager allProfManager = plugin.getAllProfessionManager();
        Profession relevantProf = getRelevantProfession(entry.vanillaId, entry.equipType);
        int currentLevel = allProfManager.getLevel(playerUuid, relevantProf);
        if (currentLevel < entry.baseItemLevel) {
            showMessage("Requires " + relevantProf.getDisplayName() + " Lv. " + entry.baseItemLevel
                + " to temper this item!", java.awt.Color.RED);
            return;
        }

        // Check gate permission using AllProfessionManager
        CraftingGateManager.GateCheck gateCheck = plugin.getCraftingGateManager()
            .checkTemperPermission(playerUuid, entry.vanillaId, allProfManager);
        if (!gateCheck.isAllowed()) {
            String msg = "Requires " + gateCheck.gate().requiredProfession().getDisplayName()
                + " Lv. " + gateCheck.gate().requiredLevel();
            showMessage(msg, java.awt.Color.RED);
            return;
        }

        // Check temper stone cost (derived from item level)
        String stoneId = entry.stoneId;
        String stoneName = getStoneName(stoneId);

        if (!hasMaterials(player, stoneId, 1)) {
            showMessage("Need 1x " + stoneName + "!", java.awt.Color.RED);
            return;
        }

        // Roll quality based on mastery ratio (profLevel / baseItemLevel)
        int profLevel = entry.profLevel;
        int baseILvl = entry.baseItemLevel;
        double ratio = baseILvl > 0 ? (double) profLevel / baseILvl : 1.0;

        int itemLevel = rollTemperItemLevel(baseILvl, ratio);
        ItemRarity rarity = rollTemperRarity(ratio);
        int affixCount = getAffixCountForRarity(rarity);

        // Generate tempered item (keeps vanilla item ID, adds RPG metadata)
        ItemStack result = HC_EquipmentAPI.generateItem(entry.vanillaId, entry.displayName, itemLevel, rarity, affixCount, playerRef.getUsername());
        if (result == null) {
            showMessage("Failed to temper item!", java.awt.Color.RED);
            return;
        }

        // Deduct temper stone
        removeMaterials(player, stoneId, 1);

        // Replace item in slot
        container.setItemStackForSlot((short) entry.slotIndex, result, false);
        player.sendInventory();

        // Grant XP to the relevant profession via AllProfessionManager
        int temperXp = getTemperXp(entry.stoneTier);
        allProfManager.grantXp(playerRef, entry.relevantProfession, temperXp);

        // Grant base XP via HC_Leveling
        try {
            com.hcleveling.api.HC_LevelingAPI.grantXp(playerRef, temperXp, "Tempering");
        } catch (NoClassDefFoundError ignored) {
            // HC_Leveling not loaded
        }

        // Success message
        String resultMsg = "Tempered " + entry.displayName + " (" + rarity.getDisplayName() + ", iLvl " + itemLevel + ")";
        showMessage(resultMsg, new java.awt.Color(85, 255, 85));

        plugin.getLogger().at(Level.INFO).log("%s tempered %s -> %s quality (%s level %d, iLvl %d)",
            playerRef.getUsername(), entry.vanillaId, rarity.getDisplayName(),
            entry.relevantProfession.getDisplayName(), profLevel, itemLevel);

        // Rescan inventory for updated state
        selectedIndex = -1;
        scanInventory(store);
    }

    private int getTemperXp(String stoneTier) {
        return switch (stoneTier) {
            case "I"    -> 5;
            case "II"   -> 8;
            case "III"  -> 12;
            case "IV"   -> 18;
            case "V"    -> 25;
            case "VI"   -> 35;
            case "VII"  -> 50;
            case "VIII" -> 70;
            case "IX"   -> 100;
            case "X"    -> 150;
            default     -> 10;
        };
    }

    private ItemRarity rollTemperRarity(double ratio) {
        double epicChance = ratio >= 2.0 ? 0.25 : 0.0;
        double rareChance = ratio >= 1.5 ? Math.min(0.25, (ratio - 1.5) / 0.5 * 0.25) : 0.0;
        double uncommonChance = Math.min(0.30, Math.max(0.0, (ratio - 1.0) / 1.0 * 0.30));

        double roll = random.nextDouble();
        if (roll < epicChance) return ItemRarity.EPIC;
        if (roll < epicChance + rareChance) return ItemRarity.RARE;
        if (roll < epicChance + rareChance + uncommonChance) return ItemRarity.UNCOMMON;
        return ItemRarity.COMMON;
    }

    private int rollTemperItemLevel(int baseItemLevel, double ratio) {
        if (ratio <= 1.0) return baseItemLevel;
        double progress = Math.min(ratio - 1.0, 1.0);
        int effectiveRange = (int) (progress * ITEM_LEVEL_RANGE);
        if (effectiveRange <= 0) return baseItemLevel;
        return baseItemLevel + random.nextInt(effectiveRange + 1);
    }

    private int getAffixCountForRarity(ItemRarity rarity) {
        return switch (rarity) {
            case EPIC -> 3;
            case RARE -> 2;
            case UNCOMMON -> 1;
            default -> 0;
        };
    }

    private boolean hasMaterials(Player player, String itemId, int quantity) {
        CombinedItemContainer container = player.getInventory().getCombinedEverything();
        int count = 0;
        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack((short) i);
            if (stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) {
                count += stack.getQuantity();
                if (count >= quantity) return true;
            }
        }
        return false;
    }

    private void removeMaterials(Player player, String itemId, int quantity) {
        player.getInventory().getStorage().removeItemStack(new ItemStack(itemId, quantity));
    }

    private Player getPlayer(Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return null;
            return store.getComponent(ref, Player.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    private void showMessage(String message, java.awt.Color color) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        cmd.set("#ResultText.TextSpans", Message.raw(message).color(color));
        sendUpdate(cmd, events, false);
    }

    private record TemperableEntry(int slotIndex, ItemStack stack, String vanillaId,
                                   String displayName, String material,
                                   int baseItemLevel, String stoneId, String stoneTier,
                                   String equipType, Profession relevantProfession,
                                   int profLevel, int requiredProfLevel,
                                   boolean tierGated,
                                   CraftingGateManager.GateCheck gateCheck) {}

    public static class TemperEventData {
        public String action;
        public String index;

        public static final BuilderCodec<TemperEventData> CODEC = BuilderCodec.builder(
                TemperEventData.class, TemperEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("Index", Codec.STRING),
                (d, v) -> d.index = v, d -> d.index)
            .build();
    }
}
