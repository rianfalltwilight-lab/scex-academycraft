package com.mohistmc.academy.regression;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The same assertions through entrypoints which exist in the frozen 0.0.23 release. */
@GameTestHolder("academy_baseline")
@PrefixGameTestTemplate(false)
public final class AuditBaselineGameTests {
    @GameTest(template="empty") public static void beforeSinglePlace(GameTestHelper h) { AuditGameTests.shiftSinglePlacementCancel(h); }
    @GameTest(template="empty") public static void beforeMultiPlace(GameTestHelper h) { AuditGameTests.shiftMultiPlacementCancel(h); }
    @GameTest(template="empty") public static void beforeThunder(GameTestHelper h) { AuditGameTests.thunderClapHasNoVanillaServerLightningEffects(h); }
    @GameTest(template="empty") public static void beforePenetrate(GameTestHelper h) { AuditGameTests.penetratePositiveGap(h); }
    @GameTest(template="empty") public static void beforeRailgunEnergy(GameTestHelper h) { AuditGameTests.railgunExactEnergyAndAdjacentValuesStop(h); }
    @GameTest(template="empty") public static void beforeRailgunReflection(GameTestHelper h) { AuditGameTests.reflectedRailgunCannotDamageThroughWall(h); }
    @GameTest(template="empty") public static void beforeMark(GameTestHelper h) { AuditGameTests.markRechecksBlockedDestinationWithoutPayment(h); }
    @GameTest(template="empty") public static void beforeGroundShock(GameTestHelper h) { AuditGameTests.groundShockFootprintIsInvariantUnderIntegerTranslation(h); }
}
