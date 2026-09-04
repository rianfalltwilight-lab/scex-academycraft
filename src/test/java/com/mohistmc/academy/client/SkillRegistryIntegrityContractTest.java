package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SkillRegistryIntegrityContractTest {
    @Test void postLegacyCategoriesUseTheirOfficialMohistArtwork() throws Exception {
        assertCategoryArtwork("aerohand", List.of(
                "volcanic_ball", "ascending_air", "air_blade", "airflow", "air_cooling", "air_wall",
                "air_jet", "offense_armour", "bomber_lance", "flying", "storm_core", "aero_separator"));
        assertCategoryArtwork("telekinesis", List.of(
                "psycho_throwing", "psycho_transmission", "psycho_needling", "insulation", "cruise_bomb",
                "overload_thinking", "perfect_paper", "psycho_slam", "psycho_harden", "liquid_shadow",
                "paper_drill"));

        String category = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/AbilityCategory.java"));
        String skill = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/Skill.java"));
        assertFalse(category.contains("GENERIC_CATEGORY_ICON"),
                "a category with official artwork must not silently use the no-category icon");
        assertFalse(skill.contains("!category.hasBundledArtwork()"),
                "official skill icons must not be redirected to cat_not_found");
    }

    @Test void registrationFailsFastForDuplicateIdsAndMissingActiveBehavior() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/SkillRegistry.java"));
        assertEquals(79, source.split("registerSkill\\(new Skill\\.Builder", -1).length - 1);
        assertTrue(source.contains("generic passive course ids are intentionally shared"));
        assertTrue(source.contains("Duplicate AcademyCraft skill effect id"));
        assertTrue(source.contains("skill.getType() == SkillType.ACTIVE && !skill.hasEffect()"));
        assertTrue(source.contains("Active AcademyCraft skills without behavior"));
        assertTrue(source.indexOf("bindEffects();") < source.indexOf("validateBindings();"));
    }

    @Test void emptyExecuteEntrypointsAreRestrictedToChargingSkills() throws Exception {
        Path root = Path.of("src/main/java/com/mohistmc/academy/skill/ability");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith("Effect.java")).forEach(p -> {
                try {
                    String s = Files.readString(p);
                    if (s.matches("(?s).*execute\\([^)]*\\)\\s*\\{\\s*\\}.*")) {
                        assertTrue(s.contains("implements ChargingSkillEffect"),
                                "empty execute on non-charging effect: " + p);
                    }
                } catch (Exception e) {
                    fail(e);
                }
            });
        }
    }

    private static void assertCategoryArtwork(String category, List<String> skills) throws Exception {
        Path root = Path.of("src/main/resources/assets/academy/textures/abilities").resolve(category);
        assertPng(root.resolve("icon.png"), 64, 64);
        assertPng(root.resolve("icon_overlay.png"), 64, 64);
        for (String skill : skills) assertPng(root.resolve("skills").resolve(skill + ".png"), 32, 32);
    }

    private static void assertPng(Path path, int width, int height) throws Exception {
        assertTrue(Files.isRegularFile(path), () -> "missing official ability artwork: " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, () -> "unreadable official ability artwork: " + path);
        assertEquals(width, image.getWidth(), () -> "wrong artwork width: " + path);
        assertEquals(height, image.getHeight(), () -> "wrong artwork height: " + path);
    }
}
