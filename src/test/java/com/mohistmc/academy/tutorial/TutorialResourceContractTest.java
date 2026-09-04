package com.mohistmc.academy.tutorial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TutorialResourceContractTest {
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\((academy:)?([^)]*)\\)");
    private static final Pattern KEY = Pattern.compile("!\\[key\\s+id=\"([^\"]+)\"]");
    private static final List<String> IDS = List.of(
            "welcome", "ores", "phase_generator", "solar_generator", "wind_generator",
            "metal_former", "imag_fusor", "terminal", "ability_developer", "ability_basis",
            "misc", "develop_ability", "wireless_network");

    @Test
    void everyRegisteredTutorialHasCompleteEnglishAndChineseUtf8Content() throws Exception {
        Path root = Path.of("src/main/resources/assets/academy/tutorials");
        for (String lang : List.of("en_us", "zh_cn")) {
            for (String id : IDS) {
                Path file = root.resolve(lang).resolve(id + ".md");
                assertTrue(Files.isRegularFile(file), () -> "missing tutorial: " + file);
                String text = Files.readString(file, StandardCharsets.UTF_8);
                int title = text.indexOf("![title]");
                int brief = text.indexOf("![brief]");
                int content = text.indexOf("![content]");
                assertTrue(title >= 0 && title < brief && brief < content, () -> "bad sections: " + file);
                assertFalse(text.substring(title + 8, brief).isBlank(), () -> "blank title: " + file);
                assertFalse(text.substring(brief + 8, content).isBlank(), () -> "blank brief: " + file);
                assertFalse(text.substring(content + 10).isBlank(), () -> "blank content: " + file);
                assertFalse(text.contains("\uFFFD"), () -> "invalid UTF-8 replacement: " + file);
                for (String line : text.lines().toList()) {
                    assertFalse(line.matches("^#{1,6}[^ #].*"),
                            () -> "heading is rendered as plain text (missing space): " + file + " :: " + line);
                }
            }
        }
    }

    @Test
    void windTutorialDocumentsTheFinal1122FifteenByFifteenClearanceRule() throws Exception {
        Path root = Path.of("src/main/resources/assets/academy/tutorials");
        for (String lang : List.of("en_us", "zh_cn")) {
            String text = Files.readString(root.resolve(lang).resolve("wind_generator.md"),
                    StandardCharsets.UTF_8);
            assertTrue(text.contains("15x15"), () -> "missing final 1.12.2 clearance size: " + lang);
            assertFalse(text.contains("7*7"), () -> "stale pre-fix clearance text: " + lang);
        }
    }

    @Test
    void guiHasHeightAwareScaleAndRendersEveryParserType() throws Exception {
        String gui = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/gui/TutorialAppGui.java"));
        assertTrue(gui.contains("Math.min(width / REF_WIDTH, height / (double) FRAME_H)"));
        for (String type : List.of("IMAGE", "TABLE_ROW", "TABLE_SEP", "OL")) {
            assertTrue(gui.contains("case " + type), () -> "unrendered markdown type: " + type);
        }
        assertTrue(gui.contains("my >= top && my <= bottom"), "content scrolling must have a Y boundary");
        assertTrue(gui.contains("enableScissor"), "scrolling regions must be clipped");
        assertFalse(gui.contains("Math.max(0.35f, fscale)"), "minimum scale can force the frame outside a tiny window");
    }

    @Test
    void everyMarkdownImageAndKeyReferenceResolves() throws Exception {
        Path tutorialRoot = Path.of("src/main/resources/assets/academy/tutorials");
        Path assetRoot = Path.of("src/main/resources/assets/academy");
        Set<String> keys = Set.of("ability_activation", "toggle_ability", "edit_preset", "skill_slot",
                "switch_preset", "open_data_terminal", "open_terminal");
        try (var files = Files.walk(tutorialRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                var images = IMAGE.matcher(text);
                while (images.find()) {
                    Path target = assetRoot.resolve(images.group(3).replace('/', java.io.File.separatorChar)).normalize();
                    assertTrue(target.startsWith(assetRoot.normalize()), () -> "image escapes asset root: " + file);
                    assertTrue(Files.isRegularFile(target), () -> "missing image " + images.group(3) + " in " + file);
                }
                var keyRefs = KEY.matcher(text);
                while (keyRefs.find()) {
                    assertTrue(keys.contains(keyRefs.group(1)), () -> "unknown key id " + keyRefs.group(1) + " in " + file);
                }
            }
        }
    }

    @Test
    void reloadHookInvalidatesBothTutorialCaches() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/com/mohistmc/academy/listener/ClientModListener.java"));
        assertTrue(listener.contains("RegisterClientReloadListenersEvent"));
        assertTrue(listener.contains("EventBusSubscriber.Bus.MOD"));
        assertTrue(listener.contains("TutorialMdParser.clearCache()"));
        assertTrue(listener.contains("ACTutorial.clearContentCache()"));
    }

    @Test
    void tutorialMachinePreviewUsesSameImagRecipeManagerTypeAsMachineAndJei() throws Exception {
        String views = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/gui/tutorial/RecipeViews.java"));
        String machine = Files.readString(Path.of("src/main/java/com/mohistmc/academy/world/block/entity/ImagFusorBlockEntity.java"));
        String jei = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/jei/AcademyJeiPlugin.java"));
        String token = "AcademyRecipeTypes.IMAG_FUSING.get()";
        assertTrue(views.contains("getAllRecipesFor(" + token + ")"));
        assertTrue(machine.contains("getRecipeFor(" + token));
        assertTrue(jei.contains("getAllRecipesFor(" + token + ")"));
        assertFalse(views.contains("ImagFusorRecipes.INSTANCE"), "tutorial must not use the removed hard-coded recipe singleton");
    }

    @Test
    void lockedTutorialsCannotRenderBodyOrRecipePreviewsAndRefreshWhileOpen() throws Exception {
        String gui = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/gui/TutorialAppGui.java"));
        assertTrue(gui.contains("if (!isCurrentLearned())"), "locked tutorial body must be gated");
        assertTrue(gui.contains("filter(group -> group.recipeTarget().isEmpty())"),
                "locked tutorial previews must exclude recipes");
        assertTrue(gui.contains("if (++refreshTicks >= 20)"), "learned groups must refresh while GUI remains open");
        assertTrue(gui.contains("ensureSelectedListItemVisible()"), "refresh must preserve a visible selection");
    }

    @Test
    void metalFormerTutorialCyclesAllIngredientCandidates() throws Exception {
        String views = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/gui/tutorial/RecipeViews.java"));
        assertTrue(views.contains("flatMap(ing -> java.util.Arrays.stream(ing.getItems()))"));
        assertFalse(views.contains("ing.getItems()[0]"), "first-candidate-only preview misrepresents tag recipes");
    }

    @Test
    void terminalTutorialIncludesEveryLegacyInstallableApp() throws Exception {
        String init = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/tutorial/TutorialInit.java"));
        for (String item : List.of("APP_SKILL_TREE", "APP_FREQ_TRANSMITTER", "APP_MEDIA_PLAYER")) {
            assertTrue(init.contains("addCondition(itemObtained(AcademyItems." + item + ".get()))"),
                    () -> "terminal tutorial does not unlock from " + item);
            assertTrue(init.contains("addPreview(recipes(AcademyItems." + item + ".get()))"),
                    () -> "terminal tutorial is missing the recipe preview for " + item);
        }
        assertFalse(init.contains("APP_SETTINGS.get()"),
                "the pre-installed settings app must not be advertised as an installable tutorial item");
    }

    @Test
    void tutorialActivationRetainsTheLegacyNotificationAndPersistentDeduplicationFlow() throws Exception {
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/assets/academy/textures/tutorial/update_notify.png")));
        String listener = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/listener/ServerListener.java"));
        String playerData = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/PlayerAbilityData.java"));
        String codec = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/PlayerAbilityDataCodec.java"));
        String mod = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/AcademyCraft.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/ClientPacketBridge.java"));
        assertTrue(listener.contains("TutorialUnlocks.activateForItem"));
        assertTrue(listener.contains("SafePayloadSender.send"));
        assertTrue(playerData.contains("activatedTutorials") && playerData.contains("activated_tutorials"));
        assertTrue(codec.contains("activated_tutorials"));
        assertTrue(mod.contains("TutorialActivatedPacket.TYPE"));
        assertTrue(bridge.contains("NotifyOverlay.notify") && bridge.contains("ac.tutorial.update"));
        assertTrue(bridge.contains("TutorialEvents.Activated"));
    }
}
