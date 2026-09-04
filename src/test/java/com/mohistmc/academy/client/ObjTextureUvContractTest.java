package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Prevents OBJ models from silently falling back to similarly named 32px block placeholders. */
class ObjTextureUvContractTest {
    private static final Path TEXTURES = Path.of("src/main/resources/assets/academy/textures/block");

    @Test
    void legacyObjModelsKeepTheirNativeUvAtlasDimensions() throws Exception {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("dev_normal.png", 512),
                Map.entry("dev_advanced.png", 512),
                Map.entry("matrix.png", 256),
                Map.entry("phase_gen_0.png", 256),
                Map.entry("phase_gen_1.png", 256),
                Map.entry("phase_gen_2.png", 256),
                Map.entry("phase_gen_3.png", 256),
                Map.entry("phase_gen_4.png", 256),
                Map.entry("solar_gen.png", 256),
                Map.entry("windgen_base.png", 256),
                Map.entry("windgen_base_disable.png", 256),
                Map.entry("windgen_fan.png", 256),
                Map.entry("windgen_main.png", 256),
                Map.entry("windgen_pillar.png", 256));
        for (var entry : expected.entrySet()) {
            BufferedImage image = ImageIO.read(TEXTURES.resolve(entry.getKey()).toFile());
            assertEquals(entry.getValue().intValue(), image.getWidth(), entry.getKey() + " width");
            assertEquals(entry.getValue().intValue(), image.getHeight(), entry.getKey() + " height");
        }
    }

    @Test
    void advancedDeveloperMaterialHasOneResolvableDiffuseMapDirective() throws Exception {
        String material = Files.readString(Path.of("src/main/resources/assets/academy/models/dev_advanced.mtl"));
        assertTrue(material.contains("map_Kd academy:block/dev_advanced"));
        assertFalse(material.contains("map_Kd map_Kd"),
                "duplicate map_Kd token makes the OBJ loader treat the texture reference as a malformed path");
    }
}
