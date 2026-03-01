package com.hcprofessions.config;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;

/**
 * Reads the global XP multiplier from HC_Leveling's settings via HC_CoreAPI.
 * Includes scheduled event window support. Uses reflection to avoid a
 * compile-time dependency on HC_Core.
 */
public class GlobalXpMultiplier {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Professions-XpMult");

    private static final DateTimeFormatter[] PARSERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    };

    private static Method getSettingDoubleMethod;
    private static Method getSettingStringMethod;
    private static boolean initialized = false;
    private static boolean available = false;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("com.hccore.api.HC_CoreAPI");
            getSettingDoubleMethod = apiClass.getMethod("getSettingDouble", String.class, String.class, double.class);
            getSettingStringMethod = apiClass.getMethod("getSetting", String.class, String.class, String.class);
            available = true;
        } catch (ClassNotFoundException e) {
            LOGGER.at(Level.FINE).log("HC_Core not found — global XP multiplier unavailable");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to init global XP multiplier: " + e.getMessage());
        }
    }

    /**
     * Get the effective XP multiplier, accounting for scheduled events.
     * Mirrors HC_Leveling's PluginConfig.getGlobalXpMultiplier() logic.
     */
    public static double get() {
        init();
        if (!available) return 1.0;
        try {
            double baseMultiplier = (double) getSettingDoubleMethod.invoke(null, "HC_Leveling", "globalXpMultiplier", 1.0);
            double eventMultiplier = getEventMultiplier();
            return baseMultiplier * eventMultiplier;
        } catch (Exception e) {
            return 1.0;
        }
    }

    /**
     * Apply the multiplier to an XP amount. Returns the original amount if multiplier is 1.0.
     */
    public static int apply(int xpAmount) {
        double mult = get();
        if (mult == 1.0) return xpAmount;
        return (int) Math.round(xpAmount * mult);
    }

    private static double getEventMultiplier() {
        try {
            String startStr = ((String) getSettingStringMethod.invoke(null, "HC_Leveling", "xpEvent.startTime", "")).trim();
            String endStr = ((String) getSettingStringMethod.invoke(null, "HC_Leveling", "xpEvent.endTime", "")).trim();

            if (startStr.isEmpty() || endStr.isEmpty()) return 1.0;

            LocalDateTime start = parseDateTime(startStr);
            LocalDateTime end = parseDateTime(endStr);
            if (start == null || end == null) return 1.0;

            LocalDateTime now = LocalDateTime.now();
            if (!now.isBefore(start) && now.isBefore(end)) {
                return (double) getSettingDoubleMethod.invoke(null, "HC_Leveling", "xpEvent.multiplier", 2.0);
            }
        } catch (Exception ignored) {}
        return 1.0;
    }

    private static LocalDateTime parseDateTime(String value) {
        for (DateTimeFormatter fmt : PARSERS) {
            try {
                return LocalDateTime.parse(value, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
