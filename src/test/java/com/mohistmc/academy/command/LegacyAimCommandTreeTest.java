package com.mohistmc.academy.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fast source contracts; the dedicated GameTest executes the real Brigadier tree. */
class LegacyAimCommandTreeTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/").resolve(path));
    }

    @Test
    void registersLegacySelfAndTargetedCommandLibraries() throws Exception {
        String commands = source("command/LegacyAimCommands.java");
        assertTrue(commands.contains("Commands.literal(\"aim\")"));
        assertTrue(commands.contains("Commands.literal(\"aimp\")"));
        assertTrue(commands.contains("Commands.argument(\"targets\", EntityArgument.players())"));
        assertTrue(commands.contains("attachActions(self"));
        assertTrue(commands.contains("attachActions(targets"));

        for (String literal : List.of("help", "?", "info", "devmode", "cheats_on", "cheats_off",
                "cat", "catlist", "learn", "unlearn", "learn_all", "learned", "skills",
                "level", "exp", "fullcp", "cd_clear", "maxout", "reset")) {
            assertTrue(commands.contains("Commands.literal(\"" + literal + "\")"),
                    () -> "missing legacy command literal " + literal);
        }
        assertTrue(commands.contains("IntegerArgumentType.integer(1, 5)"));
        assertTrue(commands.contains("FloatArgumentType.floatArg(0.0f, 1.0f)"));
        assertTrue(commands.contains("Commands.literal(\"on\")"));
        assertTrue(commands.contains("Commands.literal(\"off\")"));
        assertTrue(commands.contains("Commands.literal(indexedCategory)"));
        assertTrue(commands.contains("Commands.literal(indexedSkill)"));
        assertTrue(commands.contains("normalizeIndexToken(raw)"));
    }

    @Test
    void playerDataSupportsSafeUnlearnAndCooldownClear() throws Exception {
        String data = source("skill/PlayerAbilityData.java");
        assertTrue(data.contains("public boolean unlearnSkill(String skillId)"));
        assertTrue(data.contains("learnedSkills.remove(skillId)"));
        assertTrue(data.contains("skillId.equals(preset.getSlot(slot))"));
        assertTrue(data.contains("getSkillProficiencies()"));
        assertTrue(data.contains("public int clearCooldowns()"));
        assertTrue(data.contains("cooldowns.clear()"));
    }

    @Test
    void commandMutationsRetainPermissionEventsSyncAndTargetBoundary() throws Exception {
        String commands = source("command/LegacyAimCommands.java");
        assertTrue(commands.contains("REQUIRED_PERMISSION = 2"));
        assertTrue(commands.contains("EntityArgument.players()"));
        assertTrue(commands.contains("new AbilityEvents.CategoryChanged"));
        assertTrue(commands.contains("new AbilityEvents.LevelChanged"));
        assertTrue(commands.contains("new AbilityEvents.SkillLearned"));
        assertTrue(commands.contains("new AbilityEvents.SkillExpChanged"));
        assertTrue(commands.contains("data.syncTo(player)"));
        assertTrue(commands.contains("requireAbility(context.getSource(), player, data)"));
    }
}
