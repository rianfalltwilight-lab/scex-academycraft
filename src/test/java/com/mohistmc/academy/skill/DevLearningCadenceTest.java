package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.network.DevLearningSessionManager;
import org.junit.jupiter.api.Test;

class DevLearningCadenceTest {
    @Test void machineCadenceAndConsumptionMatchAcademyCraft107() {
        assertMachine(DevMachineType.PORTABLE, 30, 25, 750, 780);
        assertMachine(DevMachineType.NORMAL, 70, 20, 700, 735);
        assertMachine(DevMachineType.ADVANCED, 100, 15, 600, 640);
    }

    @Test void legacySkillStimulationCurveIsExactForLevelsOneThroughFive() {
        int[] expected = {3, 5, 7, 11, 15};
        for (int level=1; level<=5; level++)
            assertEquals(expected[level-1], (int)(3 + level * level * .5f));
    }

    @Test void initialAbilityInductionUsesTheLegacyLevelZeroFiveStimulations() {
        assertEquals(5, DevLearningSessionManager.INDUCTION_STIMULATIONS);
    }

    private static void assertMachine(DevMachineType type, int sync, int ticks, int energy, int actualEnergy) {
        assertEquals(sync,type.syncRate);
        assertEquals(ticks,type.stimulationTicks);
        assertEquals(energy,type.energyPerStimulation);
        assertEquals(energy / ticks,type.energyPerTick());
        assertEquals(ticks + 1, type.developmentTicksPerStimulation());
        assertEquals(actualEnergy, type.actualEnergyPerStimulation());
    }
}
