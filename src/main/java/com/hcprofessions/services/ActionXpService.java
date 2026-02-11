package com.hcprofessions.services;

import com.hcprofessions.database.XpActionRepository;
import com.hcprofessions.database.XpActionRepository.XpRewardRow;
import com.hcprofessions.managers.ProfessionManager;
import com.hcprofessions.managers.TradeskillManager;
import com.hcprofessions.models.ActionType;
import com.hcprofessions.models.Profession;
import com.hcprofessions.models.SkillTarget;
import com.hcprofessions.models.Tradeskill;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ActionXpService {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-ActionXp");

    private final XpActionRepository repository;
    private final ProfessionManager professionManager;
    private final TradeskillManager tradeskillManager;

    // Per-event: exact match lookup (case-insensitive key)
    private volatile Map<ActionType, Map<String, List<MatchedGrant>>> exactGrants = new EnumMap<>(ActionType.class);
    // Per-event: pattern match list (iterated)
    private volatile Map<ActionType, List<PatternGrant>> patternGrants = new EnumMap<>(ActionType.class);

    private volatile int totalEntries = 0;

    public ActionXpService(XpActionRepository repository, ProfessionManager professionManager,
                           TradeskillManager tradeskillManager) {
        this.repository = repository;
        this.professionManager = professionManager;
        this.tradeskillManager = tradeskillManager;
        reload();
    }

    public void reload() {
        List<XpRewardRow> rows = repository.loadAll();

        Map<ActionType, Map<String, List<MatchedGrant>>> newExact = new EnumMap<>(ActionType.class);
        Map<ActionType, List<PatternGrant>> newPattern = new EnumMap<>(ActionType.class);

        for (XpRewardRow row : rows) {
            MatchedGrant grant = new MatchedGrant(row.skillType(), row.skillName(), row.xpAmount(), row.minLevel());

            if (row.isPattern()) {
                // Convert SQL LIKE pattern to regex
                Pattern regex = likeToRegex(row.identifier());
                PatternGrant pg = new PatternGrant(row.identifier(), regex, grant);
                newPattern.computeIfAbsent(row.event(), k -> new ArrayList<>()).add(pg);
            } else {
                String key = row.identifier().toLowerCase();
                newExact.computeIfAbsent(row.event(), k -> new HashMap<>())
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(grant);
            }
        }

        this.exactGrants = newExact;
        this.patternGrants = newPattern;
        this.totalEntries = rows.size();

        LOGGER.at(Level.INFO).log("ActionXpService loaded %d entries", totalEntries);
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

        // Track best grant per (skillType, skillName) — most specific wins
        // Key: "TRADESKILL:MINING" or "PROFESSION:null"
        Map<String, MatchedGrant> bestBySkill = new HashMap<>();
        Map<String, Integer> specificityBySkill = new HashMap<>();

        // 1. Check exact matches (specificity = Integer.MAX_VALUE, always win)
        Map<String, List<MatchedGrant>> exactMap = exactGrants.get(event);
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

        // 2. Check pattern matches (specificity = raw pattern length minus wildcards)
        List<PatternGrant> patterns = patternGrants.get(event);
        if (patterns != null) {
            for (PatternGrant pg : patterns) {
                if (pg.regex.matcher(lowerIdentifier).matches()) {
                    String key = pg.grant.skillType() + ":" + pg.grant.skillName();
                    int specificity = patternSpecificity(pg.rawPattern);
                    int existing = specificityBySkill.getOrDefault(key, -1);
                    if (specificity > existing) {
                        bestBySkill.put(key, pg.grant);
                        specificityBySkill.put(key, specificity);
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
            // Check if player has a profession
            Profession prof = professionManager.getProfession(uuid);
            if (prof == null) return;

            // Check min level
            if (grant.minLevel() > 0) {
                int level = professionManager.getLevel(uuid);
                if (level < grant.minLevel()) return;
            }

            professionManager.grantXp(playerRef, grant.xpAmount());

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
        return totalEntries;
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

    private record PatternGrant(String rawPattern, Pattern regex, MatchedGrant grant) {}
}
