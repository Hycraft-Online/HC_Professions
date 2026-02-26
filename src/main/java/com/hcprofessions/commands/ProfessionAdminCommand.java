package com.hcprofessions.commands;

import com.hcequipment.api.HC_EquipmentAPI;
import com.hcequipment.generation.BaseItemResolver;
import com.hcequipment.models.ItemRarity;
import com.hcprofessions.HC_ProfessionsPlugin;
import com.hcprofessions.models.Tradeskill;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class ProfessionAdminCommand extends AbstractAsyncCommand {

    private final HC_ProfessionsPlugin plugin;

    public ProfessionAdminCommand(HC_ProfessionsPlugin plugin) {
        super("profadmin", "Profession admin commands");
        this.setAllowsExtraArguments(true);
        this.requirePermission("*");
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
        String input = ctx.getInputString();
        String[] parts = input.split("\\s+");

        if (ctx.sender() instanceof Player player && !player.hasPermission("*")) {
            ctx.sendMessage(Message.raw("You must be an operator to use this command.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

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
                case "wiperecipes" -> handleWipeRecipes(ctx, parts);
                case "learnall" -> handleLearnAll(ctx, parts);
                case "temper" -> handleTemper(ctx, parts);
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

    private void handleWipeRecipes(CommandContext ctx, String[] parts) {
        // /profadmin wiperecipes [player]
        PlayerRef targetRef;
        if (parts.length >= 3) {
            targetRef = findPlayer(parts[2]);
            if (targetRef == null) {
                ctx.sendMessage(Message.raw("Player not found: " + parts[2]).color(Color.RED));
                return;
            }
        } else if (ctx.sender() instanceof Player senderPlayer) {
            targetRef = senderPlayer.getPlayerRef();
        } else {
            ctx.sendMessage(Message.raw("Usage: /profadmin wiperecipes <player>").color(Color.RED));
            return;
        }

        String targetName = targetRef.getUsername();
        Ref<EntityStore> ref = targetRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player entity not found for " + targetName).color(Color.RED));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> freshRef = targetRef.getReference();
            if (freshRef == null || !freshRef.isValid()) {
                ctx.sendMessage(Message.raw("Player entity not found for " + targetName).color(Color.RED));
                return;
            }

            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            Player player = worldStore.getComponent(freshRef, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Player component not found for " + targetName).color(Color.RED));
                return;
            }

            Set<String> knownRecipes = player.getPlayerConfigData().getKnownRecipes();
            int count = knownRecipes.size();
            player.getPlayerConfigData().setKnownRecipes(new HashSet<>());
            CraftingPlugin.sendKnownRecipes(freshRef, worldStore);

            ctx.sendMessage(Message.raw("Cleared " + count + " known recipes for " + targetName).color(Color.GREEN));
        });
    }

    private void handleLearnAll(CommandContext ctx, String[] parts) {
        // /profadmin learnall [player]
        PlayerRef targetRef;
        if (parts.length >= 3) {
            targetRef = findPlayer(parts[2]);
            if (targetRef == null) {
                ctx.sendMessage(Message.raw("Player not found: " + parts[2]).color(Color.RED));
                return;
            }
        } else if (ctx.sender() instanceof Player senderPlayer) {
            targetRef = senderPlayer.getPlayerRef();
        } else {
            ctx.sendMessage(Message.raw("Usage: /profadmin learnall <player>").color(Color.RED));
            return;
        }

        String targetName = targetRef.getUsername();
        Ref<EntityStore> ref = targetRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player entity not found for " + targetName).color(Color.RED));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> freshRef = targetRef.getReference();
            if (freshRef == null || !freshRef.isValid()) {
                ctx.sendMessage(Message.raw("Player entity not found for " + targetName).color(Color.RED));
                return;
            }

            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            // Find the recipeToGenerate field once
            java.lang.reflect.Field recipeField = null;
            for (Class<?> clazz = Item.class; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                try { recipeField = clazz.getDeclaredField("recipeToGenerate"); break; }
                catch (NoSuchFieldException e) { continue; }
            }
            if (recipeField == null) {
                ctx.sendMessage(Message.raw("Could not find recipeToGenerate field").color(Color.RED));
                return;
            }
            recipeField.setAccessible(true);

            int learned = 0;
            for (Item item : Item.getAssetMap().getAssetMap().values()) {
                try {
                    CraftingRecipe recipe = (CraftingRecipe) recipeField.get(item);
                    if (recipe != null && recipe.isKnowledgeRequired()) {
                        if (CraftingPlugin.learnRecipe(freshRef, item.getId(), worldStore)) {
                            learned++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            ctx.sendMessage(Message.raw("Learned " + learned + " recipes for " + targetName).color(Color.GREEN));
        });
    }

    private void handleTemper(CommandContext ctx, String[] parts) {
        // /profadmin temper <profLevel>
        if (!(ctx.sender() instanceof Player senderPlayer)) {
            ctx.sendMessage(Message.raw("Must be run as a player.").color(Color.RED));
            return;
        }

        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /profadmin temper <profLevel>").color(Color.RED));
            return;
        }

        int fakeProfLevel;
        try {
            fakeProfLevel = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid level: " + parts[2]).color(Color.RED));
            return;
        }

        PlayerRef senderRef = senderPlayer.getPlayerRef();
        Ref<EntityStore> ref = senderRef.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player entity not available.").color(Color.RED));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> freshRef = senderRef.getReference();
            if (freshRef == null || !freshRef.isValid()) return;

            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            Player player = worldStore.getComponent(freshRef, Player.getComponentType());
            if (player == null || player.getInventory() == null) {
                ctx.sendMessage(Message.raw("Cannot access inventory.").color(Color.RED));
                return;
            }

            Inventory inventory = player.getInventory();
            ItemContainer hotbar = inventory.getHotbar();
            short activeSlot = inventory.getActiveHotbarSlot();
            ItemStack heldItem = hotbar.getItemStack(activeSlot);

            if (heldItem == null || heldItem.isEmpty()) {
                ctx.sendMessage(Message.raw("Not holding any item.").color(Color.RED));
                return;
            }

            String itemId = heldItem.getItemId();

            if (HC_EquipmentAPI.isTempered(heldItem)) {
                ctx.sendMessage(Message.raw("Item is already tempered.").color(Color.RED));
                return;
            }

            if (!HC_EquipmentAPI.isVanillaEquipment(itemId)) {
                ctx.sendMessage(Message.raw("Not a temperable equipment item: " + itemId).color(Color.RED));
                return;
            }

            String displayName = HC_EquipmentAPI.getVanillaDisplayName(itemId);
            if (displayName == null) displayName = itemId;

            // Roll quality using ratio-based system (fakeProfLevel / baseItemLevel)
            int baseItemLevel = BaseItemResolver.getItemLevelForItemId(itemId);
            if (baseItemLevel <= 0) baseItemLevel = 1;
            double ratio = (double) fakeProfLevel / baseItemLevel;

            Random random = new Random();

            // Item level: scales from base at 100% to base+10 at 200% mastery
            int itemLevel;
            double progress = Math.min(Math.max(ratio - 1.0, 0.0), 1.0);
            int effectiveRange = (int) (progress * 10);
            itemLevel = effectiveRange <= 0 ? baseItemLevel : baseItemLevel + random.nextInt(effectiveRange + 1);

            // Rarity: scales with mastery ratio
            double epicChance = ratio >= 2.0 ? 0.25 : 0.0;
            double rareChance = ratio >= 1.5 ? Math.min(0.25, (ratio - 1.5) / 0.5 * 0.25) : 0.0;
            double uncommonChance = Math.min(0.30, Math.max(0.0, (ratio - 1.0) / 1.0 * 0.30));
            double roll = random.nextDouble();
            ItemRarity rarity;
            if (roll < epicChance) rarity = ItemRarity.EPIC;
            else if (roll < epicChance + rareChance) rarity = ItemRarity.RARE;
            else if (roll < epicChance + rareChance + uncommonChance) rarity = ItemRarity.UNCOMMON;
            else rarity = ItemRarity.COMMON;

            int affixCount = switch (rarity) {
                case EPIC -> 3;
                case RARE -> 2;
                case UNCOMMON -> 1;
                default -> 0;
            };

            ItemStack result = HC_EquipmentAPI.generateItem(itemId, displayName, itemLevel, rarity, affixCount);
            if (result == null) {
                ctx.sendMessage(Message.raw("Failed to generate tempered item.").color(Color.RED));
                return;
            }

            hotbar.setItemStackForSlot(activeSlot, result, false);
            player.sendInventory();

            int mastery = (int) (ratio * 100);
            ctx.sendMessage(Message.raw("Tempered " + displayName + " at fake prof Lv. " + fakeProfLevel
                + " (" + mastery + "% mastery) -> " + rarity.getDisplayName() + " iLvl " + itemLevel
                + " (" + affixCount + " affixes)").color(Color.GREEN));
        });
    }

    private PlayerRef findPlayer(String name) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && playerRef.getUsername().equalsIgnoreCase(name)) {
                return playerRef;
            }
        }
        return null;
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== ProfAdmin Commands ===").color(Color.YELLOW));
        ctx.sendMessage(Message.raw("/profadmin setlevel <player> <profession|tradeskill> <level>").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin grant <player> <profession|tradeskill> <xp>").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin reload - Reload config from DB").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin wiperecipes [player] - Clear all known recipes").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin learnall [player] - Learn all knowledge-gated recipes").color(Color.WHITE));
        ctx.sendMessage(Message.raw("/profadmin temper <profLevel> - Temper held item at fake prof level").color(Color.WHITE));
    }
}
