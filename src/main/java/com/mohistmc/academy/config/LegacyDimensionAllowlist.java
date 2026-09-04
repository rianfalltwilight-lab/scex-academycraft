package com.mohistmc.academy.config;

import java.util.List;

/** Dependency-free migration rules for the 1.0.7 numeric dimension allowlist. */
public final class LegacyDimensionAllowlist {
    private LegacyDimensionAllowlist() {}

    public static boolean contains(String dimension, List<? extends String> entries) {
        if (dimension == null || entries == null) return false;
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.strip();
            if (entry.equals(dimension) || alias(entry).equals(dimension)) return true;
        }
        return false;
    }

    public static boolean validEntry(String raw) {
        if (raw == null) return false;
        String entry = raw.strip();
        if (entry.equals("-1") || entry.equals("0") || entry.equals("1")) return true;
        int separator = entry.indexOf(':');
        if (separator <= 0 || separator == entry.length() - 1) return false;
        return entry.chars().allMatch(character -> Character.isLowerCase(character)
                || Character.isDigit(character) || character == '_' || character == '-'
                || character == '.' || character == '/' || character == ':');
    }

    private static String alias(String entry) {
        return switch (entry) {
            case "-1" -> "minecraft:the_nether";
            case "0" -> "minecraft:overworld";
            case "1" -> "minecraft:the_end";
            default -> "";
        };
    }
}
