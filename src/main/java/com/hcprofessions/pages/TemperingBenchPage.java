package com.hcprofessions.pages;

import com.hcequipment.api.HC_EquipmentAPI;
import com.hcequipment.models.ItemRarity;
import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.models.CraftQualityTier;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.QualityTierDefinition;

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
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public class TemperingBenchPage extends InteractiveCustomUIPage<TemperingBenchPage.TemperEventData> {

    private final HC_ProfessionsPlugin plugin;
    private final Profession profession;
    private int selectedIndex = -1;
    private final Random random = new Random();

    // Cached temperable items
    private final List<TemperableEntry> temperableItems = new ArrayList<>();

    // Material -> (temper stone item ID, bar item ID, bar quantity)
    private static final Map<String, TemperCost> MATERIAL_COSTS = Map.ofEntries(
        Map.entry("Crude",      new TemperCost("TemperStone_Rough",    "Ingredient_Bar_Copper", 2)),
        Map.entry("Copper",     new TemperCost("TemperStone_Rough",    "Ingredient_Bar_Copper", 3)),
        Map.entry("Bronze",     new TemperCost("TemperStone_Rough",    "Ingredient_Bar_Copper", 4)),
        Map.entry("Iron",       new TemperCost("TemperStone_Polished", "Ingredient_Bar_Iron",   3)),
        Map.entry("Steel",      new TemperCost("TemperStone_Polished", "Ingredient_Bar_Iron",   5)),
        Map.entry("Thorium",    new TemperCost("TemperStone_Pristine", "Ingredient_Bar_Thorium",    3)),
        Map.entry("Cobalt",     new TemperCost("TemperStone_Pristine", "Ingredient_Bar_Cobalt",     4)),
        Map.entry("Mithril",    new TemperCost("TemperStone_Pristine", "Ingredient_Bar_Mithril",    5)),
        Map.entry("Adamantite", new TemperCost("TemperStone_Pristine", "Ingredient_Bar_Adamantite", 6))
    );

    // Display names for temper stones
    private static final Map<String, String> STONE_NAMES = Map.of(
        "TemperStone_Rough",    "Rough Temper Stone",
        "TemperStone_Polished", "Polished Temper Stone",
        "TemperStone_Pristine", "Pristine Temper Stone"
    );

    // Display names for bar items
    private static final Map<String, String> BAR_NAMES = Map.of(
        "Ingredient_Bar_Copper",     "Copper Bar",
        "Ingredient_Bar_Iron",       "Iron Bar",
        "Ingredient_Bar_Thorium",    "Thorium Bar",
        "Ingredient_Bar_Cobalt",     "Cobalt Bar",
        "Ingredient_Bar_Mithril",    "Mithril Bar",
        "Ingredient_Bar_Adamantite", "Adamantite Bar"
    );

    public TemperingBenchPage(@Nonnull PlayerRef playerRef, @Nonnull Profession profession,
                              @Nonnull HC_ProfessionsPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, TemperEventData.CODEC);
        this.plugin = plugin;
        this.profession = profession;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                     @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/TemperingBench.ui");

        // Set title based on profession
        String title = profession == Profession.WEAPONSMITH
            ? "Weaponsmith's Forge" : "Armorsmith's Anvil";
        cmd.set("#TitleText.Text", title);

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

        for (int i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack((short) i);
            if (stack == null || stack.isEmpty()) continue;

            String itemId = stack.getItemId();

            // Skip already-tempered items (have RPG metadata or HC_ prefix)
            if (HC_EquipmentAPI.isTempered(stack)) continue;

            // Check if this is a known vanilla equipment item
            if (!HC_EquipmentAPI.isVanillaEquipment(itemId)) continue;

            // Filter by profession type
            String equipType = HC_EquipmentAPI.getVanillaEquipmentType(itemId);
            if (profession == Profession.WEAPONSMITH && !"WEAPON".equals(equipType)) continue;
            if (profession == Profession.ARMORSMITH && !"ARMOR".equals(equipType)) continue;

            String material = HC_EquipmentAPI.getVanillaMaterial(itemId);
            String displayName = HC_EquipmentAPI.getVanillaDisplayName(itemId);
            if (material == null || displayName == null) continue;

            // Check gate (level requirement)
            CraftingGateManager.GateCheck gateCheck = gateManager.checkPermission(playerUuid, itemId);

            temperableItems.add(new TemperableEntry(i, stack, itemId, displayName, material, gateCheck));
        }
    }

    private void buildItemList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.clear("#ItemList");

        if (temperableItems.isEmpty()) {
            cmd.set("#EmptyState.Visible", true);
            cmd.set("#EmptyState.Text", profession == Profession.WEAPONSMITH
                ? "No untempered weapons found"
                : "No untempered armor found");
            return;
        }

        for (int i = 0; i < temperableItems.size(); i++) {
            TemperableEntry entry = temperableItems.get(i);
            String selector = "#ItemList[" + i + "]";
            cmd.append("#ItemList", "Pages/TemperableItem.ui");

            cmd.set(selector + " #ItemSlot.ItemId", entry.vanillaId);
            cmd.set(selector + " #ItemRowName.TextSpans", Message.raw(entry.displayName));

            // Show material + level requirement
            String subtitle = entry.material;
            if (!entry.gateCheck.isAllowed() && entry.gateCheck.gate() != null) {
                subtitle += " - Requires Lv. " + entry.gateCheck.gate().requiredLevel();
                cmd.set(selector + " #ItemRowSubtitle.TextSpans",
                    Message.raw(subtitle).color(java.awt.Color.RED));
            } else {
                cmd.set(selector + " #ItemRowSubtitle.TextSpans",
                    Message.raw(subtitle).color(java.awt.Color.LIGHT_GRAY));
            }

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
        cmd.set("#ItemSubtitle.TextSpans", Message.raw(entry.material + " (Untempered)").color(java.awt.Color.LIGHT_GRAY));

        // Quality preview from profession level
        UUID playerUuid = playerRef.getUuid();
        ProfessionManager profManager = plugin.getProfessionManager();
        int profLevel = profManager.getLevel(playerUuid);
        QualityTierDefinition tier = CraftQualityTier.fromLevel(profLevel);

        StringBuilder qualityText = new StringBuilder();
        if (tier != null) {
            qualityText.append(tier.name()).append(" Quality");
            qualityText.append("  |  Up to ").append(tier.maxRarity()).append(" rarity");
            if (tier.maxAffixes() > 0) {
                qualityText.append("  |  ").append(tier.minAffixes()).append("-").append(tier.maxAffixes()).append(" affixes");
            }
        } else {
            qualityText.append("Prof Lv. ").append(profLevel);
        }
        cmd.set("#StatsTitle.TextSpans", Message.raw(qualityText.toString()));

        // Cost section
        TemperCost cost = MATERIAL_COSTS.get(entry.material);
        if (cost != null) {
            String stoneName = STONE_NAMES.getOrDefault(cost.stoneItemId, cost.stoneItemId);
            String barName = BAR_NAMES.getOrDefault(cost.barItemId, cost.barItemId);

            cmd.set("#CostSlot.ItemId", cost.stoneItemId);
            cmd.set("#CostText.TextSpans",
                Message.raw("1x " + stoneName + " + " + cost.barQuantity + "x " + barName));
        } else {
            cmd.set("#CostText.TextSpans", Message.raw("Unknown material cost").color(java.awt.Color.RED));
        }

        // Gate check message
        if (!entry.gateCheck.isAllowed() && entry.gateCheck.gate() != null) {
            String gateMsg = switch (entry.gateCheck.result()) {
                case NO_PROFESSION -> "Choose a profession first! Use /profession choose";
                case WRONG_PROFESSION -> "Requires " + entry.gateCheck.gate().requiredProfession().getDisplayName();
                case TOO_LOW_LEVEL -> "Requires " + entry.gateCheck.gate().requiredProfession().getDisplayName()
                    + " Lv. " + entry.gateCheck.gate().requiredLevel();
                default -> "";
            };
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

        // Check gate permission
        UUID playerUuid = playerRef.getUuid();
        CraftingGateManager.GateCheck gateCheck = plugin.getCraftingGateManager().checkPermission(playerUuid, entry.vanillaId);
        if (!gateCheck.isAllowed()) {
            String msg = switch (gateCheck.result()) {
                case NO_PROFESSION -> "Choose a profession first!";
                case WRONG_PROFESSION -> "Requires " + gateCheck.gate().requiredProfession().getDisplayName();
                case TOO_LOW_LEVEL -> "Requires " + gateCheck.gate().requiredProfession().getDisplayName()
                    + " Lv. " + gateCheck.gate().requiredLevel();
                default -> "Cannot temper this item";
            };
            showMessage(msg, java.awt.Color.RED);
            return;
        }

        // Check material cost
        TemperCost cost = MATERIAL_COSTS.get(entry.material);
        if (cost == null) {
            showMessage("Unknown material: " + entry.material, java.awt.Color.RED);
            return;
        }

        String stoneName = STONE_NAMES.getOrDefault(cost.stoneItemId, cost.stoneItemId);
        String barName = BAR_NAMES.getOrDefault(cost.barItemId, cost.barItemId);

        if (!hasMaterials(player, cost.stoneItemId, 1)) {
            showMessage("Need 1x " + stoneName + "!", java.awt.Color.RED);
            return;
        }
        if (!hasMaterials(player, cost.barItemId, cost.barQuantity)) {
            showMessage("Need " + cost.barQuantity + "x " + barName + "!", java.awt.Color.RED);
            return;
        }

        // Roll quality based on profession level
        ProfessionManager profManager = plugin.getProfessionManager();
        int profLevel = profManager.getLevel(playerUuid);
        QualityTierDefinition tier = CraftQualityTier.fromLevel(profLevel);
        if (tier == null) {
            showMessage("Cannot determine quality tier!", java.awt.Color.RED);
            return;
        }

        int itemLevel = tier.rollItemLevel(profLevel, random);
        int affixCount = tier.rollAffixCount(random);

        // Resolve rarity
        String rarityName = tier.maxRarity().toUpperCase();
        if (CraftQualityTier.isGrandmaster(tier) && random.nextDouble() > 0.05) {
            rarityName = "EPIC";
        }
        ItemRarity rarity = ItemRarity.fromString(rarityName);

        // Generate tempered item (keeps vanilla item ID, adds RPG metadata)
        ItemStack result = HC_EquipmentAPI.generateItem(entry.vanillaId, entry.displayName, itemLevel, rarity, affixCount);
        if (result == null) {
            showMessage("Failed to temper item!", java.awt.Color.RED);
            return;
        }

        // Deduct materials
        removeMaterials(player, cost.stoneItemId, 1);
        removeMaterials(player, cost.barItemId, cost.barQuantity);

        // Replace item in slot
        container.setItemStackForSlot((short) entry.slotIndex, result, false);
        player.sendInventory();

        // Grant profession XP
        plugin.getActionXpService().onAction(playerRef, com.hcprofessions.models.ActionType.TEMPER, entry.vanillaId);

        // Success message
        String resultMsg = "Tempered " + entry.displayName + " (" + rarity.getDisplayName() + ", iLvl " + itemLevel + ")";
        showMessage(resultMsg, new java.awt.Color(85, 255, 85));

        plugin.getLogger().at(Level.INFO).log("%s tempered %s -> %s quality (prof level %d, iLvl %d)",
            playerRef.getUsername(), entry.vanillaId, rarity.getDisplayName(), profLevel, itemLevel);

        // Rescan inventory for updated state
        selectedIndex = -1;
        scanInventory(store);
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
                                   CraftingGateManager.GateCheck gateCheck) {}

    private record TemperCost(String stoneItemId, String barItemId, int barQuantity) {}

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
