package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the registry/category/localization wiring that drives developer induction. */
class InductionFactorCategoryContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/mohistmc/academy");
    private static final Path LANG = Path.of("src/main/resources/assets/academy/lang");
    private static final List<String> CATEGORIES = List.of(
            "electromaster", "meltdowner", "teleporter", "vecmanip", "aerohand", "telekinesis");

    @Test
    void eachRegisteredFactorMapsToItsMatchingAbilityCategory() throws Exception {
        String items = Files.readString(JAVA.resolve("world/AcademyItems.java"));
        for (String category : CATEGORIES) {
            String className = "Factor" + Character.toUpperCase(category.charAt(0)) + category.substring(1);
            assertTrue(items.contains("ITEMS.register(\"factor_" + category + "\", " + className + "::new)"),
                    () -> "missing or mismatched factor registration: " + category);
            String factor = Files.readString(JAVA.resolve("world/item/" + className + ".java"));
            assertTrue(factor.contains("AbilityCategory." + category.toUpperCase()),
                    () -> "factor points at the wrong category: " + category);
        }
    }

    @Test
    void everyBundledLanguageNamesBothPostLegacyFactors() throws Exception {
        try (var languages = Files.list(LANG)) {
            for (Path language : languages.filter(path -> path.toString().endsWith(".json")).toList()) {
                String json = Files.readString(language);
                assertTrue(json.contains("\"item.academy.factor_aerohand\""), language::toString);
                assertTrue(json.contains("\"item.academy.factor_telekinesis\""), language::toString);
                assertFalse(json.contains("\"item.academy.factor_aerohand\": \"item.academy.factor_aerohand\""),
                        language::toString);
                assertFalse(json.contains("\"item.academy.factor_telekinesis\": \"item.academy.factor_telekinesis\""),
                        language::toString);
            }
        }
    }

    @Test
    void inductionSelectionUsesTheFactorOwnedCategory() throws Exception {
        String sessions = Files.readString(JAVA.resolve("network/DevLearningSessionManager.java"));
        assertTrue(sessions.contains("factor.getCategory()"));
        assertTrue(sessions.contains("selection.category()"));
        assertTrue(sessions.contains("factor.getCategory().equals(category)"));
    }
}
