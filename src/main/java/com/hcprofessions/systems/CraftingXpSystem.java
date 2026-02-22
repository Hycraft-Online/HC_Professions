package com.hcprofessions.systems;

import com.hcprofessions.managers.AllProfessionManager;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Listens for CraftRecipeEvent.Post and awards profession XP based on
 * the recipe gate's {@code profession_xp_granted} value.
 *
 * If no gate exists, falls back to bench-type mapping: any craft at a
 * profession bench (e.g. Furniture_Bench -> Carpenter) grants a default
 * amount of XP. This covers ungated recipes like Village furniture.
 *
 * Note: CraftRecipeEvent.Post only fires for instant crafts (TimeSeconds == 0).
 * Timed bench crafts are patched by HC_CraftingEventFix mixin.
 */
public class CraftingXpSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-CraftXP");

    /** Default XP granted for ungated recipes at a recognized profession bench. */
    private static final int DEFAULT_UNGATED_XP = 10;

    /**
     * Maps bench IDs (from recipe BenchRequirement) to professions.
     * Only includes benches with a 1:1 profession mapping.
     * Armor_Bench is excluded because it's shared by Platesmith, Leatherworker, and Tailor.
     */
    private static final Map<String, Profession> BENCH_PROFESSION_MAP = Map.ofEntries(
        Map.entry("Furniture_Bench", Profession.CARPENTER),
        Map.entry("Alchemybench", Profession.ALCHEMIST),
        Map.entry("Cookingbench", Profession.COOK),
        Map.entry("Tannery", Profession.LEATHERWORKER),
        Map.entry("Loombench", Profession.TAILOR),
        Map.entry("Arcanebench", Profession.ENCHANTER),
        Map.entry("Weapon_Bench", Profession.WEAPONSMITH)
    );

    private CraftingGateManager craftingGateManager;
    private ProfessionManager professionManager;
    private AllProfessionManager allProfessionManager;

    public CraftingXpSystem() {
        super(CraftRecipeEvent.Post.class);
    }

    public void initialize(CraftingGateManager craftingGateManager,
                           ProfessionManager professionManager,
                           AllProfessionManager allProfessionManager) {
        this.craftingGateManager = craftingGateManager;
        this.professionManager = professionManager;
        this.allProfessionManager = allProfessionManager;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Post event) {
        if (craftingGateManager == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) return;

        String recipeId = recipe.getId();
        if (recipeId == null || recipeId.isEmpty()) return;

        // Strip _Recipe_Generated_N suffix — embedded item recipes auto-generate IDs like
        // "Weapon_Sword_Iron_Recipe_Generated_0" but our XP patterns match the item name
        int genIdx = recipeId.indexOf("_Recipe_Generated_");
        if (genIdx > 0) {
            recipeId = recipeId.substring(0, genIdx);
        }

        // Look up the recipe gate — XP amount is defined there
        RecipeGate gate = craftingGateManager.getGate(recipeId);

        Profession targetProfession;
        int baseXp;
        int recipeLevel;

        if (gate != null && gate.professionXpGranted() > 0) {
            // Gated recipe — use gate values
            targetProfession = gate.requiredProfession();
            baseXp = gate.professionXpGranted();
            recipeLevel = gate.requiredLevel();
        } else {
            // No gate — try bench-type fallback
            Profession benchProfession = resolveProfessionFromBench(recipe);
            if (benchProfession == null) return; // Not a profession bench craft

            targetProfession = benchProfession;
            baseXp = DEFAULT_UNGATED_XP;
            recipeLevel = 1;
            LOGGER.at(Level.FINE).log("Ungated recipe %s -> bench fallback: %s, %d XP",
                recipeId, targetProfession, baseXp);
        }

        if (targetProfession == null) return;

        int quantity = event.getQuantity();

        // Grey-out system: reduce XP when player outlevels the recipe
        int playerLevel = allProfessionManager != null
            ? allProfessionManager.getLevel(playerRef.getUuid(), targetProfession)
            : professionManager.getLevel(playerRef.getUuid());
        int xp = applyLevelGapReduction(baseXp, playerLevel, recipeLevel);

        if (xp <= 0) {
            // Grey recipe — show grey notification and skip XP
            try {
                Message greyMsg = Message.raw("(Grey recipe -- no XP)").color(Color.GRAY);
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), greyMsg, NotificationStyle.Default);
            } catch (Exception ignored) {}
            return;
        }

        int totalXp = xp * quantity;

        for (int i = 0; i < quantity; i++) {
            // Grant per-profession XP (AllProfessionManager handles non-native cap)
            if (allProfessionManager != null) {
                allProfessionManager.grantXp(playerRef, targetProfession, xp);
            }

            // Also grant to main profession manager if target matches primary
            Profession mainProfession = professionManager.getProfession(playerRef.getUuid());
            if (mainProfession != null && targetProfession == mainProfession) {
                professionManager.grantXp(playerRef, xp);
            }
        }

        // Show XP gain notification with color indicating recipe difficulty
        try {
            int gap = playerLevel - recipeLevel;
            Color xpColor;
            if (gap < 5) {
                xpColor = targetProfession.getColor();  // Normal color — at-level
            } else if (gap < 10) {
                xpColor = new Color(255, 255, 0);       // Yellow — reduced XP
            } else {
                xpColor = new Color(30, 180, 30);       // Green — heavily reduced
            }
            Message xpMsg = Message.raw("+" + totalXp + " " + targetProfession.getDisplayName() + " XP")
                .color(xpColor);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), xpMsg, NotificationStyle.Default);
        } catch (Exception ignored) {}

        LOGGER.at(Level.FINE).log("%s crafted %s x%d -> %d profession XP (%s, gap=%d)",
            playerRef.getUsername(), recipeId, quantity, totalXp, targetProfession,
            playerLevel - recipeLevel);
    }

    /**
     * Resolves a profession from the recipe's bench requirement.
     * Returns null if the recipe doesn't require a recognized profession bench.
     */
    @Nullable
    private static Profession resolveProfessionFromBench(CraftingRecipe recipe) {
        BenchRequirement[] requirements = recipe.getBenchRequirement();
        if (requirements == null) return null;

        for (BenchRequirement req : requirements) {
            Profession profession = BENCH_PROFESSION_MAP.get(req.id);
            if (profession != null) return profession;
        }
        return null;
    }

    /**
     * Reduces XP based on how far the player's level exceeds the recipe's required level.
     * 0-4 levels above: 100%, 5-9: 50%, 10-14: 25%, 15+: 0% (grey).
     */
    private static int applyLevelGapReduction(int baseXp, int playerLevel, int recipeLevel) {
        int gap = playerLevel - recipeLevel;
        if (gap < 5) return baseXp;
        if (gap < 10) return baseXp / 2;
        if (gap < 15) return baseXp / 4;
        return 0;
    }

    @NonNullDecl
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @NonNullDecl
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
