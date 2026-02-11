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
import com.hcprofessions.interaction.ProfessionBenchInteraction;
import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.CraftQualityTier;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.SkillDefinition;
import com.hcprofessions.models.Tradeskill;
import com.hcprofessions.services.ActionXpService;
import com.hcprofessions.systems.GatheringTradeskillXPSystem;
import com.hcprofessions.systems.PickupXpListener;
import com.hcprofessions.systems.MobKillXpSystem;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class HC_ProfessionsPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";
    private static final String MOD_FOLDER = "mods/config/HC_Professions";

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

    // Managers
    private TradeskillManager tradeskillManager;
    private ProfessionManager professionManager;
    private CraftingGateManager craftingGateManager;

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
    public CraftingGateManager getCraftingGateManager() { return craftingGateManager; }
    public ActionXpService getActionXpService() { return actionXpService; }

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

        // Reload action XP service
        if (actionXpService != null) {
            actionXpService.reload();
            this.getLogger().at(Level.INFO).log("Reloaded action XP service (%d entries)", actionXpService.size());
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

            // Seed defaults
            recipeGateRepository.seedDefaults();
            recipeGateRepository.seedComponentGates();
            configRepository.seedDefaults();
            qualityTierRepository.seedDefaults();
            definitionRepository.seedDefaults();
            xpActionRepository.seedDefaults();
            xpActionRepository.seedCraftingXpActions();

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

        // ═══════════════════════════════════════════════════════
        // MANAGER INITIALIZATION
        // ═══════════════════════════════════════════════════════
        tradeskillManager = new TradeskillManager(tradeskillRepository);
        professionManager = new ProfessionManager(professionRepository);
        craftingGateManager = new CraftingGateManager(recipeGateRepository, professionManager);
        craftingGateManager.loadCache();

        this.getLogger().at(Level.INFO).log("Managers initialized (%d recipe gates loaded)", craftingGateManager.getGateCount());

        // ═══════════════════════════════════════════════════════
        // ACTION XP SERVICE
        // ═══════════════════════════════════════════════════════
        actionXpService = new ActionXpService(xpActionRepository, professionManager, tradeskillManager);
        this.getLogger().at(Level.INFO).log("ActionXpService initialized (%d entries)", actionXpService.size());

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

        // Item pickup -> profession/tradeskill XP via HC_Factions pickup listener API
        PickupXpListener pickupXpListener = new PickupXpListener(actionXpService);
        if (pickupXpListener.register()) {
            this.getLogger().at(Level.INFO).log("Registered PickupXpListener (via HC_Factions pickup listener)");
        } else {
            this.getLogger().at(Level.WARNING).log("PickupXpListener failed to register - pickup XP disabled");
        }

        // ═══════════════════════════════════════════════════════
        // TEMPERING BENCH INTERACTION (handles Tempering_Bench F-key)
        // ═══════════════════════════════════════════════════════
        ProfessionBenchInteraction benchInteraction = new ProfessionBenchInteraction("*HC_Prof_Crafting");
        RootInteraction benchRoot = new RootInteraction("*HC_Prof_Crafting_Root", benchInteraction.getId());
        AssetRegistry.getAssetStore(Interaction.class).loadAssets("ModServer:HC_Professions", List.of(benchInteraction));
        AssetRegistry.getAssetStore(RootInteraction.class).loadAssets("ModServer:HC_Professions", List.of(benchRoot));
        Bench.registerRootInteraction(BenchType.Crafting, benchRoot);
        this.getLogger().at(Level.INFO).log("Registered TemperingBenchInteraction (Tempering_Bench F-key)");

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
        // ═══════════════════════════════════════════════════════
        this.getEventRegistry().register(PlayerDisconnectEvent.class, (event) -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();

            // Save dirty data
            tradeskillManager.savePlayer(uuid);
            professionManager.savePlayer(uuid);

            // Invalidate cache
            tradeskillManager.invalidateCache(uuid);
            professionManager.invalidateCache(uuid);
        });

        this.getLogger().at(Level.INFO).log("HC_Professions enabled successfully!");
        this.getLogger().at(Level.INFO).log("=================================");
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
