package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JadeCompatibilityContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(path));
    }

    @Test void cpHudAvoidsJadeAndJadeRemainsOptional() throws Exception {
        String hud=source("com/mohistmc/academy/client/gui/CPBarOverlay.java");
        assertTrue(hud.contains("isLoaded(\"jade\")"));
        assertTrue(hud.contains("screenH - HEIGHT * SCALE - 44"));
    }

    @Test void everyDeveloperStructurePartSharesNameAndAuthoritativeEnergy() throws Exception {
        String mod=source("com/mohistmc/academy/AcademyCraft.java");
        assertTrue(mod.contains("DEV_NORMAL_SUB.get()"));
        assertTrue(mod.contains("DEV_ADVANCED_SUB.get()"));
        assertTrue(mod.contains("energyViewOfMain"));
        assertTrue(source("com/mohistmc/academy/world/block/DevNormalSubBlock.java")
                .contains("block.academy.dev_normal"));
        assertTrue(source("com/mohistmc/academy/world/block/DevAdvancedSubBlock.java")
                .contains("block.academy.dev_advanced"));
    }

    @Test void windBaseAndMatrixProxiesResolveToTheirLogicalMachine() throws Exception {
        String mod=source("com/mohistmc/academy/AcademyCraft.java");
        assertTrue(mod.contains("WIND_GEN_BASE_SUB.get()"));
        assertTrue(mod.contains("energyViewOfWindBase"));
        String wind=source("com/mohistmc/academy/world/block/WindGenBaseSubBlock.java");
        assertTrue(wind.contains("block.academy.windgen_base"));
        assertTrue(wind.contains("getCloneItemStack"));
        String matrix=source("com/mohistmc/academy/world/block/MatrixSubBlock.java");
        assertTrue(matrix.contains("block.academy.matrix"));
        assertTrue(matrix.contains("getCloneItemStack"));
        assertTrue(matrix.contains("AcademyBlocks.MATRIX.get()"));
    }

    @Test void tutorialKeyTagIsResolvedBeforeUnderscoreItalicFormatting() throws Exception {
        String parser=source("com/mohistmc/academy/client/gui/TutorialMdParser.java");
        assertTrue(parser.indexOf("s = processKeyTags(s)") < parser.indexOf("ITALIC2_PAT.matcher"));
        assertTrue(source("com/mohistmc/academy/client/gui/DataTerminalGui.java")
                .contains("OPEN_TERMINAL.matches(keyCode, scanCode)"));
    }
}
