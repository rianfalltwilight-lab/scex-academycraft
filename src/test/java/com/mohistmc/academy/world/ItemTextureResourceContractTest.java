package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ItemTextureResourceContractTest {
    private static final Path ITEM_TEXTURES = Path.of("src/main/resources/assets/academy/textures/item");
    private static final Path ITEM_MODELS = Path.of("src/main/resources/assets/academy/models/item");

    @Test
    void phaseMatterUnitIsAnAnimatedOfficialShellComposite() throws Exception {
        BufferedImage image = ImageIO.read(ITEM_TEXTURES.resolve("matter_unit_phase_liquid.png").toFile());
        BufferedImage shell = ImageIO.read(ITEM_TEXTURES.resolve("matter_unit.png").toFile());
        assertEquals(32, image.getWidth());
        assertEquals(32 * 32, image.getHeight());
        assertEquals(32, shell.getWidth());
        assertEquals(32, shell.getHeight());
        assertTrue(Files.isRegularFile(ITEM_TEXTURES.resolve("matter_unit_phase_liquid.png.mcmeta")));
        assertFrameContainsShellAndFluid(image, shell, 32);
        String model = Files.readString(ITEM_MODELS.resolve("matter_unit_phase_liquid.json"));
        assertTrue(model.contains("academy:item/matter_unit_phase_liquid"));
    }

    @Test
    void phaseBucketUsesBucketSizedAnimatedFramesNotTheRawLiquidSheet() throws Exception {
        BufferedImage bucket = ImageIO.read(ITEM_TEXTURES.resolve("imag_phase.png").toFile());
        BufferedImage unit = ImageIO.read(ITEM_TEXTURES.resolve("matter_unit_phase_liquid.png").toFile());
        assertEquals(16, bucket.getWidth());
        assertEquals(16 * 32, bucket.getHeight());
        assertTrue(Files.isRegularFile(ITEM_TEXTURES.resolve("imag_phase.png.mcmeta")));
        assertNotEquals(unit.getWidth(), bucket.getWidth(), "bucket and matter unit must not share a raw liquid texture");
        String model = Files.readString(ITEM_MODELS.resolve("imag_phase.json"));
        assertTrue(model.contains("academy:item/imag_phase"));
    }

    @Test
    void everyNonSquareItemTextureIsAValidAnimatedStripWithMetadata() throws Exception {
        try (var paths = Files.list(ITEM_TEXTURES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".png")).toList()) {
                BufferedImage image = ImageIO.read(path.toFile());
                assertTrue(image != null, () -> "unreadable PNG: " + path);
                if (image.getWidth() != image.getHeight()) {
                    assertEquals(0, image.getHeight() % image.getWidth(), () -> "bad animation geometry: " + path);
                    assertTrue(Files.isRegularFile(Path.of(path + ".mcmeta")), () -> "animation metadata missing: " + path);
                }
            }
        }
    }

    @Test
    void everyInductionFactorModelUsesItsOwnCategoryTexture() throws Exception {
        for (String category : java.util.List.of("aerohand", "telekinesis")) {
            String model = Files.readString(ITEM_MODELS.resolve("factor_" + category + ".json"));
            assertTrue(model.contains("academy:item/factor_" + category),
                    () -> category + " factor model must not render as the shared no-category icon");
            assertFalse(model.contains("academy:guis/icons/icon_nocategory"));

            BufferedImage image = ImageIO.read(ITEM_TEXTURES.resolve("factor_" + category + ".png").toFile());
            assertTrue(image != null, () -> "unreadable factor texture: " + category);
            assertEquals(64, image.getWidth(), () -> "category icon width changed: " + category);
            assertEquals(64, image.getHeight(), () -> "category icon height changed: " + category);
            assertTrue(countVisibleColors(image) > 16,
                    () -> category + " factor was replaced by a low-detail placeholder texture");
        }
        BufferedImage aero = ImageIO.read(ITEM_TEXTURES.resolve("factor_aerohand.png").toFile());
        BufferedImage telekinesis = ImageIO.read(ITEM_TEXTURES.resolve("factor_telekinesis.png").toFile());
        assertFalse(samePixels(aero, telekinesis),
                "Aero Hand and Telekinesis factors must remain visually distinguishable");
    }

    private static void assertFrameContainsShellAndFluid(BufferedImage animated, BufferedImage shell, int size) {
        boolean retainedShell = false;
        boolean addedFluid = false;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int actual = animated.getRGB(x, y);
                int base = shell.getRGB(x, y);
                if (((base >>> 24) & 0xff) > 0 && actual == base) retainedShell = true;
                if (actual != base && ((actual >>> 24) & 0xff) > 0) addedFluid = true;
            }
        }
        assertTrue(retainedShell, "composite lost the official matter-unit shell");
        assertTrue(addedFluid, "composite contains no masked phase-liquid pixels");
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) return false;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) return false;
            }
        }
        return true;
    }

    private static int countVisibleColors(BufferedImage image) {
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) != 0) colors.add(pixel);
            }
        }
        return colors.size();
    }
}
