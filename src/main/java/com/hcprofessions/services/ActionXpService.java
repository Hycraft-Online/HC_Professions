package com.hcprofessions.services;

import com.hcprofessions.database.XpActionRepository;
import com.hcprofessions.database.XpActionRepository.XpRewardRow;
import com.hcprofessions.managers.AllProfessionManager;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.ActionType;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.SkillTarget;
import com.hcprofessions.models.Tradeskill;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ActionXpService {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ActionXp");

    private final XpActionRepository repository;
    private final ProfessionManager professionManager;
    private final TradeskillManager tradeskillManager;
    private AllProfessionManager allProfessionManager;

    /** Non-native profession crafting stops granting XP at this level (DB-configurable) */
    private volatile int nonNativeCraftLevelCap = 10;

    /** Track players already notified about the non-native craft cap (uuid:nativeProfession) */
    private final Set<String> notifiedNonNativeCap = ConcurrentHashMap.newKeySet();

    // Grant data swapped atomically to avoid torn reads during reload()
    private volatile GrantData grantData = new GrantData(new EnumMap<>(ActionType.class), new EnumMap<>(ActionType.class), 0);

    public ActionXpService(XpActionRepository repository, ProfessionManager professionManager,
                           TradeskillManager tradeskillManager) {
        this.repository = repository;
        this.professionManager = professionManager;
        this.tradeskillManager = tradeskillManager;
        reload();
    }

    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    public void setAllProfessionManager(AllProfessionManager allProfessionManager) {
        this.allProfessionManager = allProfessionManager;
    }

    public AllProfessionManager getAllProfessionManager() {
        return allProfessionManager;
    }

    public void setNonNativeCraftLevelCap(int cap) {
        this.nonNativeCraftLevelCap = cap;
    }

    public int getNonNativeCraftLevelCap() {
        return nonNativeCraftLevelCap;
    }

    /** Clear notification state for a player (call on disconnect) */
    public void clearPlayerNotifications(UUID uuid) {
        notifiedNonNativeCap.removeIf(key -> key.startsWith(uuid.toString()));
    }

    public void reload() {
        List<XpRewardRow> rows = repository.loadAll();

        Map<ActionType, Map<String, List<MatchedGrant>>> newExact = new EnumMap<>(ActionType.class);
        Map<ActionType, List<PatternGrant>> newPattern = new EnumMap<>(ActionType.class);

        for (XpRewardRow row : rows) {
            MatchedGrant grant = new MatchedGrant(row.skillType(), row.skillName(), row.xpAmount(), row.minLevel());

            if (row.isPattern()) {
                // Convert SQL LIKE pattern to regex, precompute specificity
                Pattern regex = likeToRegex(row.identifier());
                int specificity = patternSpecificity(row.identifier());
                PatternGrant pg = new PatternGrant(row.identifier(), regex, specificity, grant);
                newPattern.computeIfAbsent(row.event(), k -> new ArrayList<>()).add(pg);
            } else {
                String key = row.identifier().toLowerCase();
                newExact.computeIfAbsent(row.event(), k -> new HashMap<>())
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(grant);
            }
        }

        // Swap all grant data atomically — readers always see a consistent snapshot
        this.grantData = new GrantData(newExact, newPattern, rows.size());

        LOGGER.at(Level.INFO).log("ActionXpService loaded %d entries", rows.size());
    }

    public void onAction(PlayerRef playerRef, ActionType event, String identifier) {
        if (playerRef == null || event == null || identifier == null) return;

        List<MatchedGrant> matches = findMatches(event, identifier);
        if (matches.isEmpty()) return;

        applyGrants(playerRef, matches);
    }

    public List<MatchedGrant> findMatches(ActionType event, String identifier) {
        if (event == null || identifier == null) return List.of();

        String lowerIdentifier = identifier.toLowerCase();
        GrantData data = this.grantData; // single volatile read

        // Track best grant per (skillType, skillName) — most specific wins
        // Key: "TRADESKILL:MINING" or "PROFESSION:null"
        Map<String, MatchedGrant> bestBySkill = new HashMap<>();
        Map<String, Integer> specificityBySkill = new HashMap<>();

        // 1. Check exact matches (specificity = Integer.MAX_VALUE, always win)
        Map<String, List<MatchedGrant>> exactMap = data.exactGrants.get(event);
        if (exactMap != null) {
            List<MatchedGrant> exact = exactMap.get(lowerIdentifier);
            if (exact != null) {
                for (MatchedGrant grant : exact) {
                    String key = grant.skillType() + ":" + grant.skillName();
                    bestBySkill.put(key, grant);
                    specificityBySkill.put(key, Integer.MAX_VALUE);
                }
            }
        }

        // 2. Check pattern matches (specificity precomputed during reload)
        List<PatternGrant> patterns = data.patternGrants.get(event);
        if (patterns != null) {
            for (PatternGrant pg : patterns) {
                if (pg.regex.matcher(lowerIdentifier).matches()) {
                    String key = pg.grant.skillType() + ":" + pg.grant.skillName();
                    int existing = specificityBySkill.getOrDefault(key, -1);
                    if (pg.specificity > existing) {
                        bestBySkill.put(key, pg.grant);
                        specificityBySkill.put(key, pg.specificity);
                    }
                }
            }
        }

        return new ArrayList<>(bestBySkill.values());
    }

    /**
     * Calculate pattern specificity — more literal characters = more specific.
     * Counts non-wildcard characters in the raw LIKE pattern.
     */
    private static int patternSpecificity(String rawPattern) {
        int count = 0;
        for (int i = 0; i < rawPattern.length(); i++) {
            char c = rawPattern.charAt(i);
            if (c != '%' && c != '*' && c != '_') count++;
        }
        return count;
    }

    public void applyGrants(PlayerRef playerRef, List<MatchedGrant> grants) {
        if (playerRef == null || grants == null || grants.isEmpty()) return;
        UUID uuid = playerRef.getUuid();
        for (MatchedGrant grant : grants) {
            applyGrant(playerRef, uuid, grant);
        }
    }

    private void applyGrant(PlayerRef playerRef, UUID uuid, MatchedGrant grant) {
        if (grant.skillType() == SkillTarget.PROFESSION) {
            // Determine target profession from skill_name (if set)
            Profession targetProfession = null;
            if (grant.skillName() != null) {
                targetProfession = Profession.fromString(grant.skillName());
            }

            // Fall back to player's main profession if no specific target
            Profession mainProfession = professionManager.getProfession(uuid);
            if (targetProfession == null) {
                targetProfession = mainProfession;
            }
            if (targetProfession == null) return; // No profession at all

            // Always grant per-profession XP (AllProfessionManager handles non-native cap)
            if (allProfessionManager != null) {
                allProfessionManager.grantXp(playerRef, targetProfession, grant.xpAmount());
            }

            // Also grant to main profession manager if target matches primary
            if (mainProfession != null && targetProfession == mainProfession) {
                if (grant.minLevel() > 0) {
                    int level = professionManager.getLevel(uuid);
                    if (level < grant.minLevel()) return;
                }
                professionManager.grantXp(playerRef, grant.xpAmount());
            }

        } else if (grant.skillType() == SkillTarget.TRADESKILL) {
            // Determine which tradeskill
            Tradeskill tradeskill = null;
            if (grant.skillName() != null) {
                tradeskill = Tradeskill.fromString(grant.skillName());
            }
            if (tradeskill == null) return;

            // Check min level
            if (grant.minLevel() > 0) {
                int level = tradeskillManager.getLevel(uuid, tradeskill);
                if (level < grant.minLevel()) return;
            }

            tradeskillManager.grantXp(playerRef, tradeskill, grant.xpAmount());
        }
    }

    public int size() {
        return grantData.totalEntries;
    }

    /**
     * Convert a SQL LIKE pattern to a Java regex Pattern.
     * % = any chars, _ = single char, * = wildcard (matches everything)
     */
    private static Pattern likeToRegex(String likePattern) {
        if ("*".equals(likePattern)) {
            return Pattern.compile(".*");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < likePattern.length(); i++) {
            char c = likePattern.charAt(i);
            switch (c) {
                case '%' -> sb.append(".*");
                case '_' -> sb.append(".");
                case '.' , '\\', '[', ']', '(', ')', '{', '}', '^', '$', '|', '?', '+' ->
                    sb.append("\\").append(c);
                default -> sb.append(c);
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    public record MatchedGrant(SkillTarget skillType, String skillName, int xpAmount, int minLevel) {}

    private record PatternGrant(String rawPattern, Pattern regex, int specificity, MatchedGrant grant) {}

    /** Immutable holder for grant data — swapped atomically during reload() */
    private record GrantData(
        Map<ActionType, Map<String, List<MatchedGrant>>> exactGrants,
        Map<ActionType, List<PatternGrant>> patternGrants,
        int totalEntries
    ) {}
}
