package com.hcprofessions.config;

public class XPCurve {

    private static int maxLevel = 100;
    private static long[] xpToNextLevel;

    static {
        buildCurve(100, 100.0, 1.5);
    }

    public static void configure(int maxLevel, double base, double exponent) {
        XPCurve.maxLevel = maxLevel;
        buildCurve(maxLevel, base, exponent);
    }

    private static void buildCurve(int maxLevel, double base, double exponent) {
        xpToNextLevel = new long[maxLevel + 1];
        xpToNextLevel[0] = (long) base;
        for (int level = 1; level <= maxLevel; level++) {
            xpToNextLevel[level] = (long) Math.floor(base * Math.pow(level, exponent));
        }
    }

    public static long getXpToNextLevel(int level) {
        if (level < 0 || level >= maxLevel) return Long.MAX_VALUE;
        return xpToNextLevel[level];
    }

    public static long getTotalXpForLevel(int targetLevel) {
        long total = 0;
        for (int i = 0; i < targetLevel && i < maxLevel; i++) {
            total += xpToNextLevel[i];
        }
        return total;
    }

    public static int getMaxLevel() {
        return maxLevel;
    }
}
