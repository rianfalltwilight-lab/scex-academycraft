package com.mohistmc.academy.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataMigrationContractTest {
    @Test void codecsCarryVersionsAndCallIdempotentMigrationEntrypoints() throws Exception {
        String ability=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/PlayerAbilityDataCodec.java"));
        String migration=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/PlayerAbilityDataMigration.java"));
        String terminal=Files.readString(Path.of("src/main/java/com/mohistmc/academy/terminal/TerminalDataCodec.java"));
        assertTrue(ability.contains("DATA_VERSION = 4") && ability.contains("PlayerAbilityDataMigration.migrate(tag)"));
        assertTrue(ability.contains("putInt(\"data_version\", DATA_VERSION)"));
        assertTrue(migration.contains("input.copy()") && migration.contains("version < 1"));
        assertTrue(migration.contains("\"curCP\", \"cp\"") && migration.contains("\"activated\", \"ability_active\""));
        assertTrue(migration.contains("case 0 -> \"electromaster\"") && migration.contains("case 3 -> \"vecmanip\""));
        assertTrue(migration.contains("legacy_learned_ids") && migration.contains("legacy_skill_exps"));
        assertTrue(migration.contains("\"shift_tp\"") && !migration.contains("\"shift_teleport\""), "final 1.12.2 raw index 6 must migrate to the registered shift_tp id");
        assertTrue(migration.contains("LEGACY_ORDER") && migration.contains("learnedSkills") && migration.contains("skillExps"));
        assertTrue(migration.contains("version < 2") && migration.contains("activated_tutorials")
                && migration.contains("TutorialUnlocks.tutorialForItem"),
                "v1 saves must migrate tutorial activation history without replaying notifications");
        assertTrue(migration.contains("version < 3")
                && migration.contains("\"expAddedThisLevel\", \"level_progress_exp\""),
                "raw final-1.12.2 ability-level progress must survive migration");
        assertTrue(migration.contains("version < 4")
                && migration.contains("\"addMaxCP\"")
                && migration.contains("\"usage_max_cp\"")
                && migration.contains("\"addMaxOverload\"")
                && migration.contains("\"usage_max_overload\""),
                "raw growth and rebuilt v1-v3 totals must migrate into separate final-1.12.2 usage fields");
        assertTrue(ability.contains("putFloat(\"usage_max_cp\"")
                && ability.contains("putFloat(\"usage_max_overload\""));
        assertTrue(ability.contains("tutorial_item_granted")
                && ability.contains("data.hasObtained(\"academy:tutorial\")"),
                "pre-flag worlds must persist the one-shot tutorial grant and avoid pickup-based duplicates");
        assertTrue(terminal.contains("DATA_VERSION = 1") && terminal.contains("tag = migrate(tag)"));
        assertTrue(terminal.contains("legacy_app_ids") && terminal.contains("fail closed"));
    }

}
