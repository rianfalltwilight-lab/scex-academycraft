package com.mohistmc.academy.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TerminalAppResourceContractTest {
    @Test
    void legacyBuiltinAppsArePresentInTheAuthoritativePlayerData() throws Exception {
        String playerData = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/PlayerAbilityData.java"));
        assertTrue(playerData.contains("installedApps.add(AppRegistry.ABOUT.getAppId())"),
                "legacy About app must be pre-installed");
        assertTrue(playerData.contains("installedApps.add(AppRegistry.SETTINGS.getAppId())"),
                "legacy settings app must be pre-installed");
        assertTrue(playerData.contains("installedApps.add(AppRegistry.TUTORIAL.getAppId())"),
                "legacy tutorial app must be pre-installed, including when old saves are deserialized");
    }

    @Test
    void official112AboutVisualsAreRestoredWithoutThirdPartySolicitationMetadata() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/terminal/AppRegistry.java"));
        String terminal = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/DataTerminalGui.java"));
        assertTrue(registry.contains("new BuiltinApp(\"about\", \"item.academy.app_about\")"));
        assertTrue(registry.indexOf("register(ABOUT)") < registry.indexOf("register(SETTINGS)"));
        assertTrue(terminal.contains("new AboutAppGui(true)"));

        assertSha256("build/resources/main/assets/academy/textures/guis/about/bg.png",
                "5f1fae209d887bba05630c650f76708e3ae0a01b879477783369d2164a0e77d0");
        assertSha256("build/resources/main/assets/academy/textures/guis/about/button_glow.png",
                "39c55f1767e164235ad0a586920199c0f5b3723c90dd68f9c7c3d329f277a1b7");
        assertSha256("build/resources/main/assets/academy/textures/guis/apps/about/icon.png",
                "233c9269f9c91c164acb16fea6e75cbd9783d31990642acbb83926710e209649");
    }

    private static void assertSha256(String path, String expected) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        assertTrue(java.util.HexFormat.of().formatHex(digest).equals(expected),
                "official 1.12.2 asset hash mismatch: " + path);
    }

    @Test
    void tutorialLauncherUsesAllThreeWeightedLegacyMisakaCloudFrames() throws Exception {
        for (int frame = 0; frame < 3; frame++) {
            Path icon = Path.of("src/main/resources/assets/academy/textures/guis/apps/tutorial/icon_" + frame + ".png");
            assertTrue(Files.isRegularFile(icon), "legacy tutorial launcher frame must be bundled: " + frame);
        }

        String registry = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/terminal/AppRegistry.java"));
        assertTrue(registry.contains("random < 0.2 ? 0 : random < 0.3 ? 1 : 2"),
                "Tutorial icon must retain the 1.0.7 20/10/70 frame weights");
        assertTrue(registry.contains("textures/guis/apps/tutorial/icon_\" + frame + \".png"),
                "Tutorial must resolve one of the three real frames, not tutorial/icon.png");
    }
}
