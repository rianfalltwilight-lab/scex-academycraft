package com.mohistmc.academy.regression;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** These assertions also link against the frozen 0.0.24 release to demonstrate the defects. */
@GameTestHolder("academy_followup_baseline")
@PrefixGameTestTemplate(false)
public final class FollowupBaselineGameTests {
    @GameTest(template = "empty") public static void jetThinWall(GameTestHelper h) { FollowupGameTests.jetThinWall(h); }
    @GameTest(template = "empty") public static void jetDiagonalThinWall(GameTestHelper h) { FollowupGameTests.jetDiagonalThinWall(h); }
    @GameTest(template = "empty") public static void jetBuildHeight(GameTestHelper h) { FollowupGameTests.jetBuildHeight(h); }
    @GameTest(template = "empty") public static void jetRepeatedStartRestoresOriginalSpeed(GameTestHelper h) { FollowupGameTests.jetRepeatedStartRestoresOriginalSpeed(h); }
    @GameTest(template = "empty") public static void jetLifecycleCallbackCannotResurrect(GameTestHelper h) { FollowupGameTests.jetLifecycleCallbackCannotResurrect(h); }
    @GameTest(template = "empty") public static void threateningRejectedSurvival(GameTestHelper h) { FollowupGameTests.threateningRejectedSurvival(h); }
    @GameTest(template = "empty") public static void threateningRejectedCreative(GameTestHelper h) { FollowupGameTests.threateningRejectedCreative(h); }
    @GameTest(template = "empty") public static void threateningFallback(GameTestHelper h) { FollowupGameTests.threateningFallback(h); }
    @GameTest(template = "empty") public static void transmissionCancelRollsBackGrowth(GameTestHelper h) { FollowupGameTests.transmissionCancelRollsBackGrowth(h); }
    @GameTest(template = "empty") public static void transmissionFullMainIgnoresEmptyEquipment(GameTestHelper h) { FollowupGameTests.transmissionFullMainIgnoresEmptyEquipment(h); }
    @GameTest(template = "empty") public static void throwingRejectedReturn(GameTestHelper h) { FollowupGameTests.throwingRejectedReturn(h); }
    @GameTest(template = "empty") public static void needlingRejectedReturn(GameTestHelper h) { FollowupGameTests.needlingRejectedReturn(h); }
    @GameTest(template = "empty") public static void throwingPreservesComponents(GameTestHelper h) { FollowupGameTests.throwingPreservesComponents(h); }
    @GameTest(template = "empty") public static void needlingPreservesComponents(GameTestHelper h) { FollowupGameTests.needlingPreservesComponents(h); }
}
