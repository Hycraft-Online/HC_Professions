package com.hcprofessions.patching;

import com.hcprofessions.managers.CraftingGateManager;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.RecipeGate;
import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Generates recipe scroll Item assets at runtime from the database.
 * Eliminates the need for 161+ static JSON files and .lang entries.
 *
 * Each scroll has a GatedLearnRecipe interaction chained with ModifyInventory(-1).
 * Tooltips are provided by RecipeScrollTooltipProvider via DynamicTooltipsLib.
 *
 * Called once during LoadAssetEvent after all base items are loaded.
 */
public class RecipeScrollGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ScrollGen");
    private static final String PACK_KEY = "ModServer:HC_Professions";

    /**
     * Generates and registers scroll Item assets for all recipe gates at or above the threshold.
     */
    public static void generateAll(CraftingGateManager gateManager) {
        if (gateManager == null) {
            LOGGER.at(Level.WARNING).log("CraftingGateManager is null -- skipping scroll generation");
            return;
        }

        List<Item> items = new ArrayList<>();
        List<Interaction> interactions = new ArrayList<>();
        List<RootInteraction> rootInteractions = new ArrayList<>();

        int generated = 0;
        int skipped = 0;

        for (RecipeGate gate : gateManager.getAllGates()) {
            if (!gate.enabled()) continue;
            if (gate.requiredLevel() < RecipeKnowledgePatcher.SCROLL_LEVEL_THRESHOLD) {
                skipped++;
                continue;
            }

            String itemId = gate.recipeOutputId();
            String scrollId = "Recipe_" + itemId;

            // Skip if a scroll item already exists (from a JSON file or previous registration)
            if (Item.getAssetMap().getAsset(scrollId) != null) {
                continue;
            }

            try {
                // Create the GatedLearnRecipe interaction — it handles both the level
                // check and item consumption atomically in firstRun(), so no separate
                // ModifyInventory step is needed in the chain.
                String gatedInteractionId = scrollId + "_GatedLearn";
                Interaction gatedInteraction = createGatedLearnRecipeInteraction(gatedInteractionId, itemId);
                interactions.add(gatedInteraction);

                // RootInteraction with only the gated interaction (consumption is internal)
                String rootId = scrollId + "_Root";
                RootInteraction root = new RootInteraction(rootId, gatedInteractionId);
                rootInteractions.add(root);

                // Create the Item asset
                Item scrollItem = createScrollItem(scrollId, rootId, gate);
                items.add(scrollItem);

                // Log diagnostic info for first generated scroll
                if (generated == 0) {
                    LOGGER.at(Level.INFO).log("First scroll diagnostic [%s]: qualityIndex=%d, model=%s, texture=%s",
                            scrollId, scrollItem.getQualityIndex(),
                            scrollItem.getModel(), scrollItem.getTexture());
                }

                generated++;
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to generate scroll for %s: %s: %s",
                        itemId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Register all assets in bulk
        if (!interactions.isEmpty()) {
            AssetRegistry.getAssetStore(Interaction.class).loadAssets(PACK_KEY, interactions);
        }
        if (!rootInteractions.isEmpty()) {
            AssetRegistry.getAssetStore(RootInteraction.class).loadAssets(PACK_KEY, rootInteractions);
        }
        if (!items.isEmpty()) {
            AssetRegistry.getAssetStore(Item.class).loadAssets(PACK_KEY, items);
        }

        // Register translation entries so clients can display scroll names/descriptions
        registerTranslations(gateManager);

        LOGGER.at(Level.INFO).log("Recipe scroll generation complete: %d generated, %d below threshold (skipped)",
                generated, skipped);
    }

    /**
     * Creates a GatedLearnRecipeInteraction via the CODEC system.
     * This ensures all base Interaction fields are properly initialized.
     */
    private static Interaction createGatedLearnRecipeInteraction(String id, String targetItemId) throws Exception {
        // Use CODEC to create a properly initialized instance
        Class<?> clazz = Class.forName("com.hcprofessions.interaction.GatedLearnRecipeInteraction");
        Interaction interaction = (Interaction) clazz.getDeclaredConstructor().newInstance();

        // Set the id field
        setField(interaction, "id", id);

        // Set the itemId field (fallback for when metadata doesn't have it)
        setField(interaction, "itemId", targetItemId);

        // Set data
        setField(interaction, "data", new AssetExtraInfo.Data(Interaction.class, id, null));

        return interaction;
    }

    /**
     * Creates a scroll Item asset with interactions pointing to the RootInteraction.
     */
    private static Item createScrollItem(String scrollId, String rootInteractionId, RecipeGate gate) throws Exception {
        Item item = new Item(scrollId);

        // Set quality based on level
        String quality = getQuality(gate.requiredLevel());
        setField(item, "qualityId", quality);

        // Basic item properties
        setField(item, "maxStack", 1);
        setField(item, "consumable", true);
        setField(item, "dropOnDeath", true);

        // Model, texture, and icon (same pouch model as the old JSON scrolls)
        setField(item, "model", "Items/pouch.blockymodel");
        setField(item, "texture", "Items/pouch.png");
        setField(item, "icon", "Icons/ItemsGenerated/Recipe_Scroll.png");

        // Categories
        setField(item, "categories", new String[]{"Items.Recipes"});

        // Interactions: Primary and Secondary both point to the same RootInteraction
        Map<InteractionType, String> interactionMap = new HashMap<>();
        interactionMap.put(InteractionType.Primary, rootInteractionId);
        interactionMap.put(InteractionType.Secondary, rootInteractionId);
        setField(item, "interactions", interactionMap);

        // Set placeholder translation properties (DynamicTooltipsLib overrides these)
        setTranslationProperties(item, scrollId);

        // Asset metadata
        setField(item, "data", new AssetExtraInfo.Data(Item.class, scrollId, null));

        // processConfig() resolves qualityId -> qualityIndex, soundEventIndex, etc.
        invokeProcessConfig(item);

        return item;
    }

    /**
     * Sets TranslationProperties on the item via reflection.
     * These are placeholder keys - DynamicTooltipsLib overrides the displayed text.
     */
    private static void setTranslationProperties(Item item, String scrollId) {
        try {
            Class<?> translationClass = Class.forName(
                    "com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties");
            // ItemTranslationProperties(String name, String description)
            Object translationProps = translationClass.getDeclaredConstructor(String.class, String.class)
                    .newInstance("server.items." + scrollId + ".name",
                                "server.items." + scrollId + ".description");
            setField(item, "translationProperties", translationProps);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not set TranslationProperties on %s: %s", scrollId, e.getMessage());
        }
    }

    /**
     * Calls Item.processConfig() to resolve quality indices, sound events, etc.
     * Also ensures critical fields (itemEntityConfig, interactionConfig) are non-null
     * since toPacket() will NPE without them.
     */
    private static void invokeProcessConfig(Item item) {
        try {
            java.lang.reflect.Method method = findMethod(item.getClass(), "processConfig");
            if (method != null) {
                method.setAccessible(true);
                method.invoke(item);
            } else {
                LOGGER.at(Level.WARNING).log("processConfig method not found on %s", item.getClass().getName());
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOGGER.at(Level.WARNING).log("processConfig failed: %s: %s",
                    cause.getClass().getSimpleName(), cause.getMessage());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Could not call processConfig: %s: %s",
                    e.getClass().getSimpleName(), e.getMessage());
        }

        // Safety net: ensure fields required by toPacket() are non-null
        ensureToPacketDefaults(item);
    }

    /**
     * Ensures itemEntityConfig and interactionConfig are non-null.
     * These fields MUST be set for Item.toPacket() to succeed — it calls
     * .toPacket() on them without null checks at lines 540 and 542.
     */
    private static void ensureToPacketDefaults(Item item) {
        try {
            Field iecField = findField(Item.class, "itemEntityConfig");
            if (iecField != null) {
                iecField.setAccessible(true);
                if (iecField.get(item) == null) {
                    Class<?> iecClass = Class.forName(
                            "com.hypixel.hytale.server.core.asset.type.item.config.ItemEntityConfig");
                    Field defaultField = iecClass.getDeclaredField("DEFAULT");
                    defaultField.setAccessible(true);
                    iecField.set(item, defaultField.get(null));
                    LOGGER.at(Level.WARNING).log("itemEntityConfig was null after processConfig, set to DEFAULT for %s", item.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to ensure itemEntityConfig: %s", e.getMessage());
        }

        try {
            Field icField = findField(Item.class, "interactionConfig");
            if (icField != null) {
                icField.setAccessible(true);
                if (icField.get(item) == null) {
                    Class<?> icClass = Class.forName(
                            "com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionConfiguration");
                    Field defaultField = icClass.getDeclaredField("DEFAULT");
                    defaultField.setAccessible(true);
                    icField.set(item, defaultField.get(null));
                    LOGGER.at(Level.WARNING).log("interactionConfig was null after processConfig, set to DEFAULT for %s", item.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to ensure interactionConfig: %s", e.getMessage());
        }
    }

    /**
     * Registers translation entries (name + description) for all generated scrolls
     * with the I18nModule's language map. Since this runs during LoadAssetEvent (initial),
     * the translations are available when clients connect and receive the translation packet.
     */
    @SuppressWarnings("unchecked")
    private static void registerTranslations(CraftingGateManager gateManager) {
        try {
            Class<?> i18nClass = Class.forName("com.hypixel.hytale.server.core.modules.i18n.I18nModule");
            Field instanceField = i18nClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object i18nModule = instanceField.get(null);
            if (i18nModule == null) {
                LOGGER.at(Level.WARNING).log("I18nModule instance is null -- cannot register scroll translations");
                return;
            }

            Field languagesField = i18nClass.getDeclaredField("languages");
            languagesField.setAccessible(true);
            Map<String, Map<String, String>> languages =
                    (Map<String, Map<String, String>>) languagesField.get(i18nModule);

            Map<String, String> enUS = languages.computeIfAbsent("en-US", k -> new ConcurrentHashMap<>());

            int count = 0;
            for (RecipeGate gate : gateManager.getAllGates()) {
                if (!gate.enabled()) continue;
                if (gate.requiredLevel() < RecipeKnowledgePatcher.SCROLL_LEVEL_THRESHOLD) continue;

                String itemId = gate.recipeOutputId();
                String scrollId = "Recipe_" + itemId;
                Profession prof = gate.requiredProfession();
                String humanName = humanizeName(itemId);

                enUS.put("server.items." + scrollId + ".name", "Recipe: " + humanName);
                enUS.put("server.items." + scrollId + ".description",
                        "Teaches how to craft: " + humanName
                        + "\n" + prof.getDisplayName() + " Level " + gate.requiredLevel()
                        + "\nRight-click to learn.");
                count++;
            }

            LOGGER.at(Level.INFO).log("Registered %d scroll translation entries with I18nModule", count);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to register scroll translations: %s: %s",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Convert an item ID like "Weapon_Sword_Iron" to "Sword Iron".
     */
    private static String humanizeName(String itemId) {
        String[] prefixes = {
                "Consumable_", "Weapon_", "Armor_", "Food_", "Potion_",
                "Ingredient_", "Component_", "Furniture_", "Bench_",
        };
        String name = itemId;
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
                break;
            }
        }
        return name.replace("_", " ");
    }

    private static String getQuality(int level) {
        if (level >= 55) return "Epic";
        if (level >= 35) return "Rare";
        if (level >= 15) return "Uncommon";
        return "Common";
    }

    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(fieldName + " on " + obj.getClass().getName());
        }
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static Field findField(Class<?> startClass, String fieldName) {
        for (Class<?> clazz = startClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                continue;
            }
        }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> startClass, String methodName) {
        for (Class<?> clazz = startClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    return m;
                }
            }
        }
        return null;
    }
}
