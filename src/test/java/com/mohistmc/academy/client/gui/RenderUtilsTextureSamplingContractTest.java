package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RenderUtilsTextureSamplingContractTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/academy/textures/guis");

    @Test void officialMachineTexturesHaveExplicitFullImageSamplingContract() throws Exception {
        BufferedImage panel = ImageIO.read(ASSETS.resolve("parent/parent_background.png").toFile());
        BufferedImage overlay = ImageIO.read(ASSETS.resolve("ui/ui_phasegen.png").toFile());
        BufferedImage icon = ImageIO.read(ASSETS.resolve("icons/icon_inv.png").toFile());
        assertEquals(352, panel.getWidth());
        assertEquals(374, panel.getHeight());
        assertEquals(352, overlay.getWidth());
        assertEquals(374, overlay.getHeight());
        assertEquals(48, icon.getWidth());
        assertEquals(48, icon.getHeight());

        String source = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/utils/RenderUtils.java"));
        assertTrue(source.contains("readTextureSize"));
        assertTrue(source.contains("textureWidth, textureHeight, textureWidth, textureHeight"));
        assertTrue(source.contains("clearTextureSizeCache"));
    }

    @Test void terminalAndAbilityIconsAlsoSampleTheirWholePng() throws Exception {
        Path java = Path.of("src/main/java/com/mohistmc/academy");
        for (String relative : new String[]{
                "client/ChargingHudOverlay.java",
                "client/gui/AbilityHudOverlay.java",
                "client/gui/DataTerminalGui.java",
                "client/gui/MediaPlayerAppGui.java",
                "client/gui/NotifyOverlay.java",
                "client/gui/SkillSlotGui.java",
                "client/gui/SkillTreeGui.java"
        }) {
            String source = Files.readString(java.resolve(relative));
            assertTrue(source.contains("RenderUtils.render"), relative
                    + " must use the shared full-image renderer so resource-pack dimensions remain supported");
        }
    }

    @Test void tutorialScalingUsesIntrinsicPngDimensionsInsteadOfDestinationSize() throws Exception {
        String helper = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/GuiRenderHelper.java"));
        assertTrue(helper.contains("RenderUtils.textureSize(tex)"));
        assertTrue(helper.contains("srcW, srcH, texW, texH"));
        assertTrue(helper.contains("g.blit(tex, x, y, w, h"));

        BufferedImage portrait = ImageIO.read(Path.of(
                "src/main/resources/assets/academy/textures/tutorial/ability_ui.png").toFile());
        BufferedImage strip = ImageIO.read(Path.of(
                "src/main/resources/assets/academy/textures/tutorial/overload.png").toFile());
        assertTrue(portrait.getHeight() > portrait.getWidth());
        assertTrue(strip.getWidth() > strip.getHeight() * 5);

        String tutorial = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/TutorialAppGui.java"));
        assertTrue(tutorial.contains("w * dimensions.height() / (float) dimensions.width()"));
        assertTrue(tutorial.contains("RenderUtils.textureSize(line.image())"));
    }
}
