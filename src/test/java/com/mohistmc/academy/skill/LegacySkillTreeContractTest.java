package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LegacySkillTreeContractTest {
    private record Req(String id, float proficiency) {}
    private static final Pattern REQ = Pattern.compile("\\.prereq\\(\\\"([^\\\"]+)\\\",\\s*([0-9.]+)f\\)");

    @Test void fourLegacyTreesRetainExact112ParentGraphThresholdsAndRegistrationOrder() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/SkillRegistry.java"));
        String em = section(source, "registerElectromasterSkills", "registerMeltdownerSkills");
        assertReq(em, "charging", req("arc_gen", .3f));
        assertReq(em, "mag_movement", req("arc_gen", 1f), req("charging", .7f));
        assertReq(em, "mag_manip", req("mag_movement", .5f));
        assertReq(em, "body_intensify", req("arc_gen", 1f), req("charging", 1f));
        assertReq(em, "mine_detect", req("mag_manip", 1f));
        assertReq(em, "thunder_bolt", req("arc_gen", 1f), req("charging", .7f));
        assertReq(em, "railgun", req("thunder_bolt", .3f), req("mag_manip", 1f));
        assertReq(em, "thunder_clap", req("thunder_bolt", 1f));
        assertPositions(em, Map.ofEntries(
                pos("arc_gen",24,46), pos("charging",55,18), pos("mag_movement",137,35),
                pos("mag_manip",204,33), pos("body_intensify",97.1,15), pos("mine_detect",225,12),
                pos("thunder_bolt",86,67), pos("railgun",164,59), pos("thunder_clap",204,80),
                pos("brain_course",30,110), pos("brain_course_advanced",115,110), pos("mind_course",205,110)));
        assertOrder(em, "arc_gen", "charging", "mag_movement", "mag_manip", "mine_detect",
                "body_intensify", "thunder_bolt", "railgun", "thunder_clap",
                "brain_course", "brain_course_advanced", "mind_course");

        String md = section(source, "registerMeltdownerSkills", "registerTeleporterSkills");
        assertReq(md, "rad_intensify", req("electron_bomb", .5f));
        assertReq(md, "scatter_bomb", req("electron_bomb", .8f));
        assertReq(md, "light_shield", req("electron_bomb", 1f));
        assertReq(md, "meltdowner", req("scatter_bomb", .8f), req("light_shield", .8f));
        assertReq(md, "mine_ray_basic", req("meltdowner", .3f));
        assertReq(md, "ray_barrage", req("meltdowner", .5f));
        assertReq(md, "jet_engine", req("meltdowner", 1f));
        assertReq(md, "mine_ray_expert", req("mine_ray_basic", .8f));
        assertReq(md, "mine_ray_luck", req("mine_ray_expert", 1f));
        assertReq(md, "electron_missile", req("jet_engine", .3f));
        assertPositions(md, Map.ofEntries(
                pos("electron_bomb",15,45), pos("rad_intensify",35,75), pos("scatter_bomb",70,50),
                pos("light_shield",55,15), pos("meltdowner",115,40), pos("mine_ray_basic",140,70),
                pos("ray_barrage",140,10), pos("jet_engine",170,32), pos("mine_ray_expert",172,70),
                pos("mine_ray_luck",205,82), pos("electron_missile",210,35),
                pos("brain_course",30,110), pos("brain_course_advanced",115,110), pos("mind_course",205,110)));
        assertOrder(md, "electron_bomb", "rad_intensify", "scatter_bomb", "light_shield",
                "meltdowner", "mine_ray_basic", "ray_barrage", "jet_engine", "mine_ray_expert",
                "mine_ray_luck", "electron_missile", "brain_course", "brain_course_advanced", "mind_course");

        String tp = section(source, "registerTeleporterSkills", "registerVecmanipSkills");
        assertReq(tp, "dim_folding_theorem", req("threatening_teleport", .2f));
        assertReq(tp, "penetrate_teleport", req("threatening_teleport", .5f));
        assertReq(tp, "mark_teleport", req("threatening_teleport", .4f));
        assertReq(tp, "flesh_ripping", req("mark_teleport", .5f), req("penetrate_teleport", .5f));
        assertReq(tp, "location_teleport", req("penetrate_teleport", .8f), req("mark_teleport", .8f));
        assertReq(tp, "shift_tp", req("location_teleport", .5f));
        assertReq(tp, "space_fluct", req("shift_tp", 0f));
        assertReq(tp, "flashing", req("shift_tp", .8f));
        assertPositions(tp, Map.ofEntries(
                pos("threatening_teleport",14,42), pos("dim_folding_theorem",50,75),
                pos("penetrate_teleport",60,46), pos("mark_teleport",70,16), pos("flesh_ripping",130,12),
                pos("location_teleport",118,50), pos("shift_tp",175,47), pos("space_fluct",160,80),
                pos("flashing",220,20), pos("brain_course",30,110),
                pos("brain_course_advanced",115,110), pos("mind_course",205,110)));
        assertOrder(tp, "threatening_teleport", "dim_folding_theorem", "penetrate_teleport",
                "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "space_fluct",
                "flashing", "brain_course", "brain_course_advanced", "mind_course");

        String vm = section(source, "registerVecmanipSkills", "registerAerohandSkills");
        assertReq(vm, "ground_shock", req("dir_shock", 0f));
        assertReq(vm, "vec_accel", req("dir_shock", 0f));
        assertReq(vm, "vec_deviation", req("vec_accel", 0f));
        assertReq(vm, "dir_blast", req("ground_shock", 0f));
        assertReq(vm, "storm_wing", req("vec_accel", 0f));
        assertReq(vm, "blood_retro", req("dir_blast", 0f));
        assertReq(vm, "vec_reflection", req("vec_deviation", 0f));
        assertReq(vm, "plasma_cannon", req("storm_wing", 0f));
        assertPositions(vm, Map.ofEntries(
                pos("dir_shock",16,45), pos("ground_shock",64,85), pos("vec_accel",76,40),
                pos("vec_deviation",145,53), pos("dir_blast",136,80), pos("storm_wing",130,20),
                pos("blood_retro",204,83), pos("vec_reflection",210,50), pos("plasma_cannon",175,14),
                pos("brain_course",30,110), pos("brain_course_advanced",115,110), pos("mind_course",205,110)));
        assertOrder(vm, "dir_shock", "ground_shock", "vec_accel", "vec_deviation", "dir_blast",
                "storm_wing", "blood_retro", "vec_reflection", "plasma_cannon",
                "brain_course", "brain_course_advanced", "mind_course");

        for (String tree : List.of(em, md, tp, vm)) assertGenericCourseChain(tree);
    }

    private static void assertGenericCourseChain(String tree) {
        assertReq(tree, "brain_course");
        assertTrue(builder(tree, "brain_course").contains("anyLevelPrereq(3)"));
        assertReq(tree, "brain_course_advanced", req("brain_course", 0f));
        assertTrue(builder(tree, "brain_course_advanced").contains("anyLevelPrereq(4)"));
        assertReq(tree, "mind_course", req("brain_course_advanced", 0f));
        assertTrue(builder(tree, "mind_course").contains("anyLevelPrereq(5)"));
    }

    private static Req req(String id, float proficiency) { return new Req(id, proficiency); }
    private static Map.Entry<String, double[]> pos(String id, double x, double y) {
        return Map.entry(id, new double[]{x, y});
    }

    private static void assertPositions(String section, Map<String, double[]> expected) {
        for (var entry : expected.entrySet()) {
            Matcher matcher = Pattern.compile("\\.position\\(([-0-9.]+),\\s*([-0-9.]+)\\)")
                    .matcher(builder(section, entry.getKey()));
            assertTrue(matcher.find(), "missing 1.12.2 position for " + entry.getKey());
            assertArrayEquals(entry.getValue(), new double[]{Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2))}, 1.0e-6, "wrong 1.12.2 position for " + entry.getKey());
        }
    }

    private static void assertOrder(String section, String... expected) {
        Matcher matcher = Pattern.compile("new Skill\\.Builder\\(\\\"([^\\\"]+)\\\"").matcher(section);
        List<String> actual = new ArrayList<>();
        while (matcher.find()) actual.add(matcher.group(1));
        assertEquals(List.of(expected), actual, "wrong 1.12.2 category registration order");
    }

    private static void assertReq(String section, String skill, Req... expected) {
        Matcher matcher = REQ.matcher(builder(section, skill));
        List<Req> actual = new ArrayList<>();
        while (matcher.find()) actual.add(new Req(matcher.group(1), Float.parseFloat(matcher.group(2))));
        assertEquals(List.of(expected), actual, "wrong 1.12.2 prerequisites for " + skill);
    }

    private static String builder(String section, String skill) {
        Matcher matcher = Pattern.compile("new Skill\\.Builder\\(\\\"" + Pattern.quote(skill)
                + "\\\".*?\\.build\\(\\)\\);", Pattern.DOTALL).matcher(section);
        assertTrue(matcher.find(), "missing skill builder: " + skill);
        return matcher.group();
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf("private static void " + start);
        int to = source.indexOf("private static void " + end, from + 1);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }
}
