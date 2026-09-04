package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DevMachineLayoutTest {
    @Test void largeDeveloperEnergyValuesStayReadableInTheCompactSidebar() {
        assertEquals("IF 200k/200k", DevMachineLayout.compactEnergyLabel(200_000, 200_000));
        assertEquals("IF 0/50k", DevMachineLayout.compactEnergyLabel(0, 50_000));
        assertEquals("IF 1.3M/2M", DevMachineLayout.compactEnergyLabel(1_250_000, 2_000_000));
    }

    @Test void fitsBothDimensionsAtCommonAndTinySizes() {
        for (int[] size : new int[][]{{1920,1080},{854,480},{400,240},{320,180},{200,120}}) {
            DevMachineLayout l = DevMachineLayout.fit(size[0], size[1]);
            assertTrue(l.left() >= 0 && l.top() >= 0);
            assertTrue(l.left() + l.width() <= size[0]);
            assertTrue(l.top() + l.height() <= size[1]);
            assertTrue(l.width() > 0 && l.height() > 0);
        }
    }
    @Test void compactModeUsesBothWidthAndHeightConstraints() {
        assertTrue(DevMachineLayout.fit(350, 1000).compact());
        assertTrue(DevMachineLayout.fit(1000, 220).compact());
        assertFalse(DevMachineLayout.fit(1000, 600).compact());
    }

    @Test void legacyDeveloperCanvasAndSidebarFitAtTheCommon427LogicalWidth() {
        int viewport = 427;
        int canvasLeft = RegularMachineLayout.developerCanvasLeft(viewport);
        int menuLeft = RegularMachineLayout.developerMenuLeft(viewport);
        int sidebarLeft = RegularMachineLayout.developerSidebarLeft(viewport);
        assertTrue(sidebarLeft >= 0);
        assertEquals(canvasLeft + RegularMachineLayout.DEVELOPER_MENU_X, menuLeft);
        assertEquals(canvasLeft - RegularMachineLayout.SIDEBAR_WIDTH, sidebarLeft);
        assertTrue(canvasLeft + RegularMachineLayout.DEVELOPER_CANVAS_WIDTH <= viewport);
    }

    @Test void exhaustiveLogicalViewportRectanglesAreOrderedAndContained() {
        for (int width = 1; width <= 512; width++) {
            for (int height = 1; height <= 320; height++) {
                DevMachineLayout l = DevMachineLayout.fit(width, height);
                assertTrue(l.left() >= 0 && l.top() >= 0, width + "x" + height);
                assertTrue(l.width() >= 0 && l.height() >= 0, width + "x" + height);
                assertTrue(l.left() + l.width() <= width, width + "x" + height);
                assertTrue(l.top() + l.height() <= height, width + "x" + height);
                assertContained(l, l.content(), width, height);
                assertContained(l, l.action(), width, height);
                if (!l.interactive()) {
                    assertEquals(0, l.action().width());
                    assertEquals(0, l.action().height());
                }
            }
        }
    }

    @Test void compactSkillSidebarRegionsNeverOverlap() {
        for (boolean hasAbility : new boolean[]{false, true}) {
            for (boolean normal : new boolean[]{false, true}) {
                DevMachineLayout.Rect panel = new DevMachineLayout.Rect(10, 40, 80, 150);
                DevMachineLayout.SkillPanel regions = DevMachineLayout.skillPanel(panel, hasAbility, normal);
                assertContained(panel, regions.primaryAction());
                assertContained(panel, regions.energy());
                assertContained(panel, regions.syncRate());
                if (normal) assertContained(panel, regions.networkAction());
                assertNoOverlap(regions.primaryAction(), regions.energy());
                assertNoOverlap(regions.primaryAction(), regions.syncRate());
                assertNoOverlap(regions.primaryAction(), regions.networkAction());
                assertNoOverlap(regions.networkAction(), regions.energy());
                assertNoOverlap(regions.energy(), regions.syncRate());
            }
        }
    }

    private static void assertContained(DevMachineLayout layout, DevMachineLayout.Rect rect, int sw, int sh) {
        String at = sw + "x" + sh;
        assertTrue(rect.width() >= 0 && rect.height() >= 0, at);
        assertTrue(rect.right() >= rect.left() && rect.bottom() >= rect.top(), at);
        assertTrue(rect.left() >= layout.left() && rect.top() >= layout.top(), at);
        assertTrue(rect.right() <= layout.left() + layout.width(), at);
        assertTrue(rect.bottom() <= layout.top() + layout.height(), at);
    }

    private static void assertContained(DevMachineLayout.Rect outer, DevMachineLayout.Rect inner) {
        assertTrue(inner.left() >= outer.left());
        assertTrue(inner.top() >= outer.top());
        assertTrue(inner.right() <= outer.right());
        assertTrue(inner.bottom() <= outer.bottom());
    }

    private static void assertNoOverlap(DevMachineLayout.Rect a, DevMachineLayout.Rect b) {
        if (a.width() == 0 || a.height() == 0 || b.width() == 0 || b.height() == 0) return;
        assertTrue(a.right() <= b.left() || b.right() <= a.left()
                || a.bottom() <= b.top() || b.bottom() <= a.top(), a + " overlaps " + b);
    }
}
