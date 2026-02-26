package com.hcprofessions;

import com.hcprofessions.commands.ProfessionAdminCommand;
import com.hcprofessions.commands.ProfessionCommand;
import com.hcprofessions.commands.TradeskillCommand;
import com.hcprofessions.config.XPCurve;
import com.hcprofessions.database.ConfigRepository;
import com.hcprofessions.database.DatabaseConfig;
import com.hcprofessions.database.DatabaseManager;
import com.hcprofessions.database.DefinitionRepository;
import com.hcprofessions.database.ProfessionRepository;
import com.hcprofessions.database.QualityTierRepository;
import com.hcprofessions.database.RecipeGateRepository;
import com.hcprofessions.database.TradeskillRepository;
import com.hcprofessions.database.XpActionRepository;
import com.hcprofessions.interaction.ConsumableBuffInteraction;
import com.hcprofessions.interaction.GatedLearnRecipeInteraction;
import com.hcprofessions.interaction.ProfessionBenchInteraction;
import com.hcprofessions.patching.RecipeInjector;
import com.hcprofessions.patching.RecipeKnowledgePatcher;
import com.hcprofessions.patching.RecipeScrollGenerator;
import com.hcprofessions.database.TemperMaterialRepository;
import com.hcprofessions.database.AllProfessionRepository;
import com.hcprofessions.managers.AllProfessionManager;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.CraftQualityTier;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.SkillDefinition;
import com.hcprofessions.models.Tradeskill;
import com.hcprofessions.services.ActionXpService;
import com.hcprofessions.systems.CraftingXpSystem;
import com.hcprofessions.systems.GatheringTradeskillXPSystem;
import com.hcprofessions.systems.PickupXpListener;
import com.hcprofessions.systems.MobKillXpSystem;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HC_ProfessionsPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";
    private static final String MOD_FOLDER = "mods/.hc_config/HC_Professions";

    private static HC_ProfessionsPlugin instance;

    // Database
    private DatabaseManager databaseManager;
    private TradeskillRepository tradeskillRepository;
    private ProfessionRepository professionRepository;
    private RecipeGateRepository recipeGateRepository;
    private ConfigRepository configRepository;
    private QualityTierRepository qualityTierRepository;
    private DefinitionRepository definitionRepository;
    private XpActionRepository xpActionRepository;
    private AllProfessionRepository allProfessionRepository;
    private TemperMaterialRepository temperMaterialRepository;

    // Managers
    private TradeskillManager tradeskillManager;
    private ProfessionManager professionManager;
    private AllProfessionManager allProfessionManager;
    private CraftingGateManager craftingGateManager;

    // Config caches
    private volatile Map<String, Integer> temperMaterialRequirements = Map.of();

    // Services
    private ActionXpService actionXpService;

    // Systems (kept for runtime updates)
    private GatheringTradeskillXPSystem gatheringSystem;

    public HC_ProfessionsPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static HC_ProfessionsPlugin getInstance() {
        return instance;
    }

    // Getters
    public TradeskillManager getTradeskillManager() { return tradeskillManager; }
    public ProfessionManager getProfessionManager() { return professionManager; }
    public AllProfessionManager getAllProfessionManager() { return allProfessionManager; }
    public CraftingGateManager getCraftingGateManager() { return craftingGateManager; }
    public ActionXpService getActionXpService() { return actionXpService; }
    public RecipeGateRepository getRecipeGateRepository() { return recipeGateRepository; }
    public Map<String, Integer> getTemperMaterialRequirements() { return temperMaterialRequirements; }

    public void reloadAll() {
        // Reload XP config
        Map<String, String> xpConfig = configRepository.loadAll();
        int maxLevel = Integer.parseInt(xpConfig.getOrDefault("max_level", "100"));
        double xpBase = Double.parseDouble(xpConfig.getOrDefault("xp_base", "100"));
        double xpExponent = Double.parseDouble(xpConfig.getOrDefault("xp_exponent", "1.5"));
        XPCurve.configure(maxLevel, xpBase, xpExponent);
        this.getLogger().at(Level.INFO).log("Reloaded XP config (max=%d, base=%.0f, exp=%.2f)", maxLevel, xpBase, xpExponent);

        // Reload definitions
        List<SkillDefinition> tradeskillDefs = definitionRepository.loadByType("tradeskill");
        Tradeskill.setDefinitions(tradeskillDefs);
        this.getLogger().at(Level.INFO).log("Reloaded %d tradeskill definitions", tradeskillDefs.size());

        List<SkillDefinition> professionDefs = definitionRepository.loadByType("profession");
        Profession.setDefinitions(professionDefs);
        this.getLogger().at(Level.INFO).log("Reloaded %d profession definitions", professionDefs.size());

        // Reload quality tiers
        var tiers = qualityTierRepository.loadAll();
        CraftQualityTier.setTiers(tiers);
        this.getLogger().at(Level.INFO).log("Reloaded %d quality tiers", tiers.size());

        // Reload recipe gates
        craftingGateManager.reloadCache();

        // Reload temper material requirements
        if (temperMaterialRepository != null) {
            temperMaterialRequirements = temperMaterialRepository.loadAll();
            this.getLogger().at(Level.INFO).log("Reloaded %d temper material requirements", temperMaterialRequirements.size());
        }

        // Reload action XP service
        if (actionXpService != null) {
            int craftCap = Integer.parseInt(xpConfig.getOrDefault("non_native_craft_level_cap", "10"));
            actionXpService.setNonNativeCraftLevelCap(craftCap);
            actionXpService.reload();
            this.getLogger().at(Level.INFO).log("Reloaded action XP service (%d entries, non-native craft cap: %d)", actionXpService.size(), craftCap);
        }

        // Sync all-profession level cap
        if (allProfessionManager != null) {
            int profCap = Integer.parseInt(xpConfig.getOrDefault("non_native_craft_level_cap", "10"));
            allProfessionManager.setNonNativeLevelCap(profCap);
        }

        // Sync release level cap
        if (professionManager != null) {
            int releaseCap = Integer.parseInt(xpConfig.getOrDefault("release_level_cap", "20"));
            professionManager.setReleaseLevelCap(releaseCap);
            this.getLogger().at(Level.INFO).log("Release level cap: %d", releaseCap);
        }
    }

    @Override
    protected void setup() {
        super.setup();

        this.getLogger().at(Level.INFO).log("=================================");
        this.getLogger().at(Level.INFO).log("    HC PROFESSIONS " + VERSION);
        this.getLogger().at(Level.INFO).log("=================================");

        // ═══════════════════════════════════════════════════════
        // DATABASE INITIALIZATION
        // ═══════════════════════════════════════════════════════
        File modFolder = new File(MOD_FOLDER);
        DatabaseConfig dbConfig = DatabaseConfig.load(modFolder);

        this.getLogger().at(Level.INFO).log("Initializing database connection...");
        try {
            databaseManager = new DatabaseManager(
                dbConfig.getUrl(),
                dbConfig.getUsername(),
                dbConfig.getPassword(),
                dbConfig.getPoolSize()
            );
            this.getLogger().at(Level.INFO).log("Database connection established");

            tradeskillRepository = new TradeskillRepository(databaseManager);
            professionRepository = new ProfessionRepository(databaseManager);
            recipeGateRepository = new RecipeGateRepository(databaseManager);
            configRepository = new ConfigRepository(databaseManager);
            qualityTierRepository = new QualityTierRepository(databaseManager);
            definitionRepository = new DefinitionRepository(databaseManager);
            xpActionRepository = new XpActionRepository(databaseManager);
            allProfessionRepository = new AllProfessionRepository(databaseManager);
            temperMaterialRepository = new TemperMaterialRepository(databaseManager);

            // Seed defaults (schema + base config only, not bulk data)
            recipeGateRepository.seedDefaults();
            configRepository.seedDefaults();
            qualityTierRepository.seedDefaults();
            definitionRepository.seedDefaults();
            xpActionRepository.seedDefaults();
            temperMaterialRepository.seedDefaults();

            // ── RELEASE RESTRICTIONS ──────────────────────────────
            definitionRepository.disableAllExcept("profession", List.of(
                "ALCHEMIST", "COOK", "WEAPONSMITH", "ARMORSMITH", "LEATHERWORKER", "TAILOR", "CARPENTER"
            ));

            // Disabled: recipes are now managed via admin UI
            // recipeGateRepository.seedComponentGates();
            recipeGateRepository.seedComponentIngredients();
            xpActionRepository.seedGatheringXpActions();

            this.getLogger().at(Level.INFO).log("Repositories initialized");

        } catch (Exception e) {
            this.getLogger().at(Level.SEVERE).log("Failed to initialize database: " + e.getMessage());
            this.getLogger().at(Level.SEVERE).log("Plugin features will be disabled!");
            return;
        }

        // ═══════════════════════════════════════════════════════
        // LOAD DB-DRIVEN CONFIG
        // ═══════════════════════════════════════════════════════
        Map<String, String> xpConfig = configRepository.loadAll();
        int maxLevel = Integer.parseInt(xpConfig.getOrDefault("max_level", "100"));
        double xpBase = Double.parseDouble(xpConfig.getOrDefault("xp_base", "100"));
        double xpExponent = Double.parseDouble(xpConfig.getOrDefault("xp_exponent", "1.5"));
        XPCurve.configure(maxLevel, xpBase, xpExponent);
        this.getLogger().at(Level.INFO).log("XP curve: max=%d, base=%.0f, exponent=%.2f", maxLevel, xpBase, xpExponent);

        List<SkillDefinition> tradeskillDefs = definitionRepository.loadByType("tradeskill");
        Tradeskill.setDefinitions(tradeskillDefs);
        this.getLogger().at(Level.INFO).log("Loaded %d tradeskill definitions", tradeskillDefs.size());

        List<SkillDefinition> professionDefs = definitionRepository.loadByType("profession");
        Profession.setDefinitions(professionDefs);
        this.getLogger().at(Level.INFO).log("Loaded %d profession definitions", professionDefs.size());

        var qualityTiers = qualityTierRepository.loadAll();
        CraftQualityTier.setTiers(qualityTiers);
        this.getLogger().at(Level.INFO).log("Loaded %d quality tiers", qualityTiers.size());

        temperMaterialRequirements = temperMaterialRepository.loadAll();
        this.getLogger().at(Level.INFO).log("Loaded %d temper material requirements", temperMaterialRequirements.size());

        // ═══════════════════════════════════════════════════════
        // MANAGER INITIALIZATION
        // ═══════════════════════════════════════════════════════
        tradeskillManager = new TradeskillManager(tradeskillRepository);
        professionManager = new ProfessionManager(professionRepository);
        int releaseCap = Integer.parseInt(xpConfig.getOrDefault("release_level_cap", "20"));
        professionManager.setReleaseLevelCap(releaseCap);
        this.getLogger().at(Level.INFO).log("Release level cap: %d", releaseCap);
        allProfessionManager = new AllProfessionManager(allProfessionRepository, professionManager);
        int allProfCap = Integer.parseInt(xpConfig.getOrDefault("non_native_craft_level_cap", "10"));
        allProfessionManager.setNonNativeLevelCap(allProfCap);
        craftingGateManager = new CraftingGateManager(recipeGateRepository, professionManager);
        craftingGateManager.loadCache();

        this.getLogger().at(Level.INFO).log("Managers initialized (%d recipe gates loaded)", craftingGateManager.getGateCount());

        // ═══════════════════════════════════════════════════════
        // ACTION XP SERVICE
        // ═══════════════════════════════════════════════════════
        actionXpService = new ActionXpService(xpActionRepository, professionManager, tradeskillManager);
        actionXpService.setAllProfessionManager(allProfessionManager);
        int craftCap = Integer.parseInt(xpConfig.getOrDefault("non_native_craft_level_cap", "10"));
        actionXpService.setNonNativeCraftLevelCap(craftCap);
        this.getLogger().at(Level.INFO).log("ActionXpService initialized (%d entries, non-native craft cap: %d)", actionXpService.size(), craftCap);

        // ═══════════════════════════════════════════════════════
        // ECS SYSTEMS
        // ═══════════════════════════════════════════════════════

        // Tradeskill XP from block breaking
        gatheringSystem = new GatheringTradeskillXPSystem();
        gatheringSystem.initialize(actionXpService, tradeskillManager);
        this.getEntityStoreRegistry().registerSystem(gatheringSystem);
        this.getLogger().at(Level.INFO).log("Registered GatheringTradeskillXPSystem (using ActionXpService)");

        // Mob kill -> profession/tradeskill XP via ActionXpService
        MobKillXpSystem mobKillXpSystem = new MobKillXpSystem();
        mobKillXpSystem.initialize(actionXpService);
        this.getEntityStoreRegistry().registerSystem(mobKillXpSystem);
        this.getLogger().at(Level.INFO).log("Registered MobKillXpSystem");

        // Crafting -> profession XP via recipe gate's profession_xp_granted
        CraftingXpSystem craftingXpSystem = new CraftingXpSystem();
        craftingXpSystem.initialize(craftingGateManager, professionManager, allProfessionManager);
        this.getEntityStoreRegistry().registerSystem(craftingXpSystem);
        this.getLogger().at(Level.INFO).log("Registered CraftingXpSystem");

        // Item pickup -> profession/tradeskill XP via HC_Factions pickup listener API
        PickupXpListener pickupXpListener = new PickupXpListener(actionXpService);
        if (pickupXpListener.register()) {
            this.getLogger().at(Level.INFO).log("Registered PickupXpListener (via HC_Factions pickup listener)");
        } else {
            this.getLogger().at(Level.WARNING).log("PickupXpListener failed to register - pickup XP disabled");
        }


        // ═══════════════════════════════════════════════════════
        // BENCH INTERACTION OVERRIDE (replaces vanilla *Simple_Crafting_Default)
        // Handles: Tempering_Bench -> custom page, All other benches -> KnowledgeGatedCraftingWindow
        // ═══════════════════════════════════════════════════════
        ProfessionBenchInteraction benchInteraction = new ProfessionBenchInteraction("*Simple_Crafting_Default");
        AssetRegistry.getAssetStore(Interaction.class).loadAssets("ModServer:HC_Professions", List.of(benchInteraction));
        // Re-load the RootInteraction to force rebuild with our replacement interaction
        RootInteraction benchRoot = new RootInteraction("*Simple_Crafting_Default", "*Simple_Crafting_Default");
        AssetRegistry.getAssetStore(RootInteraction.class).loadAssets("ModServer:HC_Professions", List.of(benchRoot));
        this.getLogger().at(Level.INFO).log("Replaced *Simple_Crafting_Default with KnowledgeGatedCraftingWindow handler");

        // ═══════════════════════════════════════════════════════
        // GATED LEARN RECIPE INTERACTION (level-checks before teaching recipe)
        // ═══════════════════════════════════════════════════════
        this.getCodecRegistry(Interaction.CODEC).register(
            "GatedLearnRecipe",
            GatedLearnRecipeInteraction.class,
            GatedLearnRecipeInteraction.CODEC
        );
        this.getLogger().at(Level.INFO).log("Registered GatedLearnRecipe interaction type");

        // ═══════════════════════════════════════════════════════
        // RUNTIME ITEM PATCHING & SCROLL GENERATION
        // ═══════════════════════════════════════════════════════
        this.getEventRegistry().register((short) 64, LoadAssetEvent.class, event -> {
            RecipeKnowledgePatcher.removeDisabledRecipes(craftingGateManager);
            RecipeKnowledgePatcher.removeRecipesForBench("Arcanebench");
            RecipeKnowledgePatcher.patchTodoBenchIds(craftingGateManager);
            RecipeInjector.injectAll();
            RecipeKnowledgePatcher.patchAll(craftingGateManager);
            RecipeScrollGenerator.generateAll(craftingGateManager);
        });

        // Register DynamicTooltipsLib scroll tooltip provider (soft dependency)
        registerScrollTooltipProvider();

        // ═══════════════════════════════════════════════════════
        // CONSUMABLE BUFF INTERACTION (HC_Attributes integration)
        // ═══════════════════════════════════════════════════════
        registerConsumableBuffs();

        // ═══════════════════════════════════════════════════════
        // COMMANDS
        // ═══════════════════════════════════════════════════════
        this.getCommandRegistry().registerCommand(new ProfessionCommand(this));
        this.getCommandRegistry().registerCommand(new TradeskillCommand(this));
        this.getCommandRegistry().registerCommand(new ProfessionAdminCommand(this));
        this.getLogger().at(Level.INFO).log("Registered commands: /profession, /tradeskill, /profadmin");

        // ═══════════════════════════════════════════════════════
        // PLAYER CONNECT EVENT - pre-cache data
        // ═══════════════════════════════════════════════════════
        this.getEventRegistry().register(PlayerConnectEvent.class, (event) -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID playerUuid = playerRef.getUuid();

            // Pre-cache player data
            tradeskillManager.getPlayerData(playerUuid);
            professionManager.getPlayerData(playerUuid);
            allProfessionManager.getPlayerData(playerUuid);

            // Show profession info
            var profData = professionManager.getPlayerData(playerUuid);
            if (profData.hasProfession()) {
                String profInfo = String.format("[Professions] %s Lv. %d - %s",
                    profData.getProfession().getDisplayName(),
                    profData.getLevel(),
                    professionManager.getProgressString(playerUuid));
                playerRef.sendMessage(Message.raw(profInfo).color(profData.getProfession().getColor()));
            } else {
                playerRef.sendMessage(Message.raw("[Professions] No profession chosen. Use /profession choose").color(Color.GRAY));
            }
        });

        // ═══════════════════════════════════════════════════════
        // PLAYER DISCONNECT EVENT - save and cleanup
        // PlayerDisconnectEvent fires during world teleport transitions (false positive).
        // Delay cache invalidation to confirm the player actually left the server.
        // ═══════════════════════════════════════════════════════
        this.getEventRegistry().register(PlayerDisconnectEvent.class, (event) -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();

            // Always save dirty data immediately (safe even during teleport — just persists current state)
            tradeskillManager.savePlayer(uuid);
            professionManager.savePlayer(uuid);
            allProfessionManager.savePlayer(uuid);

            // Delay cache invalidation to avoid evicting data for a player who is merely teleporting
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                PlayerRef stillOnline = Universe.get().getPlayer(uuid);
                if (stillOnline == null) {
                    // Player is truly gone — safe to invalidate
                    tradeskillManager.invalidateCache(uuid);
                    professionManager.invalidateCache(uuid);
                    allProfessionManager.invalidateCache(uuid);
                    actionXpService.clearPlayerNotifications(uuid);
                }
            }, 2, TimeUnit.SECONDS);
        });

        this.getLogger().at(Level.INFO).log("HC_Professions enabled successfully!");
        this.getLogger().at(Level.INFO).log("=================================");
    }

    /**
     * Registers RecipeScrollTooltipProvider with DynamicTooltipsLib via reflection (soft dependency).
     */
    private void registerScrollTooltipProvider() {
        try {
            Class<?> apiProviderClass = Class.forName("org.herolias.tooltips.api.DynamicTooltipsApiProvider");
            java.lang.reflect.Method getMethod = apiProviderClass.getMethod("get");
            Object api = getMethod.invoke(null);
            Class<?> apiInterface = Class.forName("org.herolias.tooltips.api.DynamicTooltipsApi");
            Class<?> providerInterface = Class.forName("org.herolias.tooltips.api.TooltipProvider");
            java.lang.reflect.Method registerMethod = apiInterface.getMethod("registerProvider", providerInterface);
            registerMethod.invoke(api, new com.hcprofessions.tooltip.RecipeScrollTooltipProvider());
            this.getLogger().at(Level.INFO).log("DynamicTooltipsLib detected - recipe scroll tooltips enabled");
        } catch (ClassNotFoundException e) {
            this.getLogger().at(Level.INFO).log("DynamicTooltipsLib not found - scroll tooltips will use placeholder text");
        } catch (Exception e) {
            this.getLogger().at(Level.WARNING).log("Failed to register scroll tooltip provider: " + e.getMessage());
        }
    }

    /**
     * Registers the ConsumableBuff interaction type.
     * Buff definitions are loaded from the database by HC_Attributes (BuiltinBuffs.loadDatabaseBuffs).
     * Requires HC_Attributes plugin to be loaded.
     */
    private void registerConsumableBuffs() {
        try {
            // Register the interaction type so item JSONs can use "Type": "ConsumableBuff"
            this.getCodecRegistry(Interaction.CODEC).register(
                "ConsumableBuff",
                ConsumableBuffInteraction.class,
                ConsumableBuffInteraction.CODEC
            );
            this.getLogger().at(Level.INFO).log("Registered ConsumableBuff interaction type (buff definitions loaded from DB by HC_Attributes)");

        } catch (NoClassDefFoundError e) {
            this.getLogger().at(Level.WARNING).log("HC_Attributes not available - consumable buffs disabled");
        } catch (Exception e) {
            this.getLogger().at(Level.WARNING).log("Failed to register ConsumableBuff interaction: " + e.getMessage());
        }
    }

    @Override
    protected void shutdown() {
        super.shutdown();

        if (databaseManager != null) {
            databaseManager.close();
        }

        this.getLogger().at(Level.INFO).log("HC_Professions disabled");
    }
}
