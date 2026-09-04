package com.mohistmc.academy.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dependency-free 1.0.7 achievement-id table shared by command and unit tests. */
final class LegacyAchievementIds {
    private static final List<String> DEFAULT_IDS = List.of(
            "phase_liquid", "matrix1", "matrix2", "node", "developer1", "developer2",
            "developer3", "phasegen", "solargen", "windgen", "crystal", "terminal");
    private static final List<String> ELECTROMASTER_IDS = List.of(
            "lv1", "lv2", "lv3", "lv4", "lv5", "arc_gen", "attack_creeper",
            "mag_movement", "body_intensify", "mine_detect", "thunder_bolt", "railgun",
            "thunder_clap");
    private static final List<String> MELTDOWNER_IDS = List.of(
            "lv1", "lv2", "lv3", "lv4", "lv5", "rad_intensify", "light_shield",
            "meltdowner", "mine_ray", "jet_engine", "electron_missile");
    private static final List<String> TELEPORTER_IDS = List.of(
            "lv1", "lv2", "lv3", "lv4", "lv5", "threatening_teleport",
            "critical_attack", "ignore_barrier", "flashing", "mastery");
    private static final List<String> VECMANIP_IDS = List.of(
            "lv1", "lv2", "lv3", "lv4", "lv5", "ground_shock", "dir_blast",
            "storm_wing", "blood_retro", "vec_reflection");
    private static final List<String> ALL = buildLegacyIds();

    private LegacyAchievementIds() {}

    static List<String> all() { return ALL; }

    static String toAdvancementPath(String supplied) {
        if (supplied == null) return null;
        String value = supplied.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("academy:legacy/")) value = value.substring("academy:legacy/".length());
        value = value.replace('.', '/');
        if (!value.contains("/")) value = "default/" + value;
        String oldId = value.startsWith("default/")
                ? value.substring("default/".length())
                : value.replace('/', '.');
        return ALL.contains(oldId) ? value : null;
    }

    private static List<String> buildLegacyIds() {
        List<String> ids = new ArrayList<>(56);
        ids.addAll(DEFAULT_IDS);
        addCategory(ids, "electromaster", ELECTROMASTER_IDS);
        addCategory(ids, "meltdowner", MELTDOWNER_IDS);
        addCategory(ids, "teleporter", TELEPORTER_IDS);
        addCategory(ids, "vecmanip", VECMANIP_IDS);
        return List.copyOf(ids);
    }

    private static void addCategory(List<String> output, String category, List<String> ids) {
        ids.forEach(id -> output.add(category + "." + id));
    }
}
