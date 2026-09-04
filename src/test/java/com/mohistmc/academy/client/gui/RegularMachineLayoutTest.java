package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegularMachineLayoutTest {
    @Test void commonGuiScaleWidthsKeepSidebarMachineAndInfoCardTogether() {
        for (int width : new int[] {320, 426, 640}) {
            int left = RegularMachineLayout.machineLeft(width, true);
            int compositionLeft = left - RegularMachineLayout.SIDEBAR_WIDTH;
            int compositionRight = left + RegularMachineLayout.INFO_X + RegularMachineLayout.INFO_WIDTH;
            assertTrue(compositionLeft >= 0, Integer.toString(width));
            assertTrue(compositionRight <= width, Integer.toString(width));
            assertEquals(RegularMachineLayout.COMPOSITION_WIDTH,
                    compositionRight - compositionLeft, Integer.toString(width));
        }
        assertEquals(28, RegularMachineLayout.machineLeft(320, true));
        assertEquals(81, RegularMachineLayout.machineLeft(426, true));
        assertEquals(188, RegularMachineLayout.machineLeft(640, true));
    }

    @Test void panelElementRenderAndHitTestShareTheShiftedOrigin() {
        for (int width : new int[] {320, 426, 640}) {
            int left = RegularMachineLayout.machineLeft(width, true);
            assertEquals(left + 8,
                    RegularMachineLayout.centeredElementX(left, 160, 0));
            assertEquals(left + 155,
                    RegularMachineLayout.centeredElementX(left, 15, 75));
            assertEquals(left + 8,
                    RegularMachineLayout.centeredElementX(left, 150, -5));
        }
    }

    @Test void narrowViewFallsBackToCenteredMachineWithoutPretendingInfoFits() {
        assertEquals((302 - 176) / 2, RegularMachineLayout.machineLeft(302, true));
        assertEquals((320 - 176) / 2, RegularMachineLayout.machineLeft(320, false));
    }

    @Test void matrixSidebarAndWidePanelFitAtThreeHundredTwenty() {
        int left = RegularMachineLayout.contentLeftWithSidebar(320, 295);
        assertEquals(22, left);
        assertEquals(2, left - RegularMachineLayout.SIDEBAR_WIDTH);
        assertEquals(317, left + 295);
    }

    @Test void developerCompactAndFullSidebarsNeverCoverTheirCanvas() {
        for (int width : new int[] {320, 426}) {
            int left = RegularMachineLayout.machineLeft(width, true);
            assertTrue(left - 20 + 18 <= left, Integer.toString(width));
            assertTrue(left + RegularMachineLayout.INFO_X + RegularMachineLayout.INFO_WIDTH <= width,
                    Integer.toString(width));
            assertTrue(left + 94 + 72 <= left + RegularMachineLayout.MACHINE_WIDTH,
                    Integer.toString(width));
        }
        int fullLeft = (640 - 400) / 2;
        int fullSidebar = fullLeft - 20;
        assertTrue(fullSidebar >= 0);
        assertTrue(fullSidebar + 18 <= fullLeft);
        assertTrue(fullLeft + 400 <= 640);
    }
}
