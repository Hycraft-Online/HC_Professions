package com.hcprofessions.config;

import com.hcprofessions.models.Tradeskill;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeskillSourceConfig {

    public record XpEntry(Tradeskill tradeskill, int xp, int minLevel) {}

    private final Map<String, XpEntry> exactMatches;
    private final List<PatternEntry> prefixMatches;
    private final List<PatternEntry> containsMatches;

    private record PatternEntry(String pattern, XpEntry entry) {}

    private TradeskillSourceConfig(Map<String, XpEntry> exactMatches,
                                   List<PatternEntry> prefixMatches,
                                   List<PatternEntry> containsMatches) {
        this.exactMatches = exactMatches;
        this.prefixMatches = prefixMatches;
        this.containsMatches = containsMatches;
    }

    @Nullable
    public XpEntry lookup(String id) {
        String lower = id.toLowerCase();

        XpEntry exact = exactMatches.get(lower);
        if (exact != null) return exact;

        for (PatternEntry pe : prefixMatches) {
            if (lower.startsWith(pe.pattern)) return pe.entry;
        }

        for (PatternEntry pe : containsMatches) {
            if (lower.contains(pe.pattern)) return pe.entry;
        }

        return null;
    }

    public int size() {
        return exactMatches.size() + prefixMatches.size() + containsMatches.size();
    }

    public static class Builder {
        private final Map<String, XpEntry> exact = new HashMap<>();
        private final List<PatternEntry> prefix = new ArrayList<>();
        private final List<PatternEntry> contains = new ArrayList<>();

        public Builder addEntry(String pattern, String matchType, String tradeskillName, int xp) {
            return addEntry(pattern, matchType, tradeskillName, xp, 0);
        }

        public Builder addEntry(String pattern, String matchType, String tradeskillName, int xp, int minLevel) {
            Tradeskill skill = Tradeskill.fromString(tradeskillName);
            if (skill == null) return this;

            String lowerPattern = pattern.toLowerCase();
            XpEntry entry = new XpEntry(skill, xp, minLevel);

            switch (matchType) {
                case "exact" -> exact.put(lowerPattern, entry);
                case "prefix" -> prefix.add(new PatternEntry(lowerPattern, entry));
                case "contains" -> contains.add(new PatternEntry(lowerPattern, entry));
            }
            return this;
        }

        public TradeskillSourceConfig build() {
            Comparator<PatternEntry> byLengthDesc = (a, b) -> Integer.compare(b.pattern.length(), a.pattern.length());
            prefix.sort(byLengthDesc);
            contains.sort(byLengthDesc);

            return new TradeskillSourceConfig(
                Map.copyOf(exact),
                List.copyOf(prefix),
                List.copyOf(contains)
            );
        }
    }
}
