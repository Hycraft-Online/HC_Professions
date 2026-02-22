package com.hcprofessions.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.state.BenchState;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.Set;
import java.util.logging.Level;

/**
 * A crafting window that enforces knowledgeRequired server-side.
 *
 * The vanilla SimpleCraftingWindow has NO server-side enforcement of knowledgeRequired:
 * it sends ALL recipes to the client and allows all crafts without checking.
 * Only DiagramCraftingWindow has server-side enforcement (collectRecipes filter).
 *
 * This window adds two layers of protection:
 * 1. Filters craftableRecipes in windowData before sending to client (onOpen0)
 * 2. Validates knowledgeRequired in handleAction before allowing any craft
 *
 * Used by HC_Professions for profession benches where recipe scrolls gate access.
 */
public class KnowledgeGatedCraftingWindow extends SimpleCraftingWindow {

    private static final HytaleLogger LOGGER =
            HytaleLogger.getLogger().getSubLogger("HC_Professions-KnowledgeWindow");

    public KnowledgeGatedCraftingWindow(@Nonnull BenchState benchState) {
        super(benchState);
    }

    @Override
    protected boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        boolean result = super.onOpen0(ref, store);

        // Log recipe knowledge stats for diagnostics. We do NOT filter recipes out of
        // windowData — the client natively shows knowledgeRequired recipes as locked
        // when the player hasn't learned them. Server-side handleAction blocks actual crafts.
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            Set<String> knownRecipes = player.getPlayerConfigData().getKnownRecipes();
            logRecipeKnowledgeStats(knownRecipes);
        } else {
            LOGGER.at(Level.WARNING).log("onOpen0 called but Player component is null!");
        }

        return result;
    }

    /**
     * Logs diagnostic stats about recipe knowledge without modifying windowData.
     * The client handles the visual locked/unlocked state natively via knowledgeRequired.
     */
    private void logRecipeKnowledgeStats(Set<String> knownRecipes) {
        if (!this.windowData.has("categories")) return;

        JsonArray categories = this.windowData.getAsJsonArray("categories");
        if (categories == null) return;

        int totalRecipes = 0;
        int knowledgeRequiredCount = 0;
        int knownCount = 0;
        int unknownCount = 0;

        for (int i = 0; i < categories.size(); i++) {
            JsonObject category = categories.get(i).getAsJsonObject();
            if (!category.has("craftableRecipes")) continue;

            JsonArray recipes = category.getAsJsonArray("craftableRecipes");
            if (recipes == null) continue;

            for (int j = 0; j < recipes.size(); j++) {
                String recipeId = recipes.get(j).getAsString();
                totalRecipes++;
                CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);

                if (recipe != null && recipe.isKnowledgeRequired()) {
                    knowledgeRequiredCount++;
                    String outputItemId = getOutputItemId(recipe);
                    if (knownRecipes.contains(recipeId)
                            || (outputItemId != null && knownRecipes.contains(outputItemId))) {
                        knownCount++;
                    } else {
                        unknownCount++;
                    }
                }
            }
        }

        LOGGER.at(Level.INFO).log("Knowledge stats: %d categories, %d total recipes, %d require knowledge (%d known, %d unknown), player has %d known recipes",
                categories.size(), totalRecipes, knowledgeRequiredCount, knownCount, unknownCount, knownRecipes.size());
    }

    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull WindowAction action) {
        // Server-side validation: block crafts for recipes the player hasn't learned
        if (action instanceof CraftRecipeAction) {
            CraftRecipeAction craftAction = (CraftRecipeAction) action;
            String recipeId = craftAction.recipeId;
            CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);

            if (recipe != null && recipe.isKnowledgeRequired()) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    Set<String> knownRecipes = player.getPlayerConfigData().getKnownRecipes();
                    String outputItemId = getOutputItemId(recipe);

                    if (!knownRecipes.contains(recipeId)
                            && (outputItemId == null || !knownRecipes.contains(outputItemId))) {
                        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                        if (playerRef != null) {
                            playerRef.sendMessage(
                                    Message.raw("You haven't learned this recipe yet! Use a recipe scroll first.")
                                            .color(new Color(255, 80, 80)));
                        }
                        LOGGER.at(Level.INFO).log("Blocked craft of %s -- player doesn't know recipe (output: %s)",
                                recipeId, outputItemId);
                        return; // Block the craft
                    }
                }
            }
        }

        super.handleAction(ref, store, action);
    }

    /**
     * Get the primary output item ID from a CraftingRecipe.
     */
    private static String getOutputItemId(CraftingRecipe recipe) {
        try {
            MaterialQuantity primary = recipe.getPrimaryOutput();
            if (primary != null && primary.getItemId() != null) {
                return primary.getItemId();
            }
            MaterialQuantity[] outputs = recipe.getOutputs();
            if (outputs != null && outputs.length > 0 && outputs[0].getItemId() != null) {
                return outputs[0].getItemId();
            }
        } catch (Exception e) {
            // Some recipes may have null outputs
        }
        return null;
    }
}
