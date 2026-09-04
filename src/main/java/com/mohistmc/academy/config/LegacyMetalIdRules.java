package com.mohistmc.academy.config;

import java.util.Map;

/** Dependency-free aliases for 1.0.7 block/entity configuration names. */
public final class LegacyMetalIdRules {
    private static final Map<String, String> BLOCK_ALIASES = Map.of(
            "golden_rail", "minecraft:powered_rail"
    );
    private static final Map<String, String> ENTITY_ALIASES = Map.ofEntries(
            Map.entry("MinecartRideable", "minecraft:minecart"),
            Map.entry("MinecartChest", "minecraft:chest_minecart"),
            Map.entry("MinecartFurnace", "minecraft:furnace_minecart"),
            Map.entry("MinecartTNT", "minecraft:tnt_minecart"),
            Map.entry("MinecartHopper", "minecraft:hopper_minecart"),
            Map.entry("MinecartSpawner", "minecraft:spawner_minecart"),
            Map.entry("MinecartCommandBlock", "minecraft:command_block_minecart"),
            Map.entry("academy-craft.ac_Entity_EntityMagHook", "academy:mag_hook"),
            Map.entry("VillagerGolem", "minecraft:iron_golem")
    );

    private LegacyMetalIdRules() {}

    public static String blockId(String raw) {
        return normalize(raw, BLOCK_ALIASES);
    }

    public static String entityId(String raw) {
        return normalize(raw, ENTITY_ALIASES);
    }

    private static String normalize(String raw, Map<String, String> aliases) {
        if (raw == null) return "";
        String entry = raw.strip();
        boolean tag = entry.startsWith("#");
        String body = tag ? entry.substring(1) : entry;
        String mapped = aliases.getOrDefault(body, body.contains(":") ? body : "minecraft:" + body);
        return tag ? "#" + mapped : mapped;
    }
}
