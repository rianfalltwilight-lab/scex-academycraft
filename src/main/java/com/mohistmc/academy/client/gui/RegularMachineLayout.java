package com.mohistmc.academy.client.gui;

/**
 * Pure coordinate model for the regular AcademyCraft machine composition.
 *
 * <p>The 176x187 machine page is accompanied by a 20px left sidebar and the
 * original 100px information card at x=183.  Keeping the calculation outside
 * the Minecraft screen class makes the render and hit-test origins testable at
 * every GUI scale.</p>
 */
public final class RegularMachineLayout {
    public static final int MACHINE_WIDTH = 176;
    public static final int MACHINE_HEIGHT = 187;
    public static final int SIDEBAR_WIDTH = 20;
    public static final int INFO_X = 183;
    public static final int INFO_WIDTH = 100;
    public static final int COMPOSITION_WIDTH = SIDEBAR_WIDTH + INFO_X + INFO_WIDTH;
    /** Legacy page_developer.xml canvas and the 176px menu origin inside it. */
    public static final int DEVELOPER_CANVAS_WIDTH = 400;
    public static final int DEVELOPER_MENU_X = 112;
    public static final int DEVELOPER_COMPOSITION_WIDTH = SIDEBAR_WIDTH + DEVELOPER_CANVAS_WIDTH;

    private RegularMachineLayout() {}

    public static int machineLeft(int viewportWidth, boolean reserveSideArea) {
        if (reserveSideArea && viewportWidth >= COMPOSITION_WIDTH) {
            return contentLeftWithSidebar(viewportWidth, INFO_X + INFO_WIDTH);
        }
        return (viewportWidth - MACHINE_WIDTH) / 2;
    }

    /**
     * Centre a content area whose origin has a left sidebar. {@code rightExtent}
     * is the content's right edge relative to that origin, allowing wider
     * screens such as the 295px matrix page to use the same invariant.
     */
    public static int contentLeftWithSidebar(int viewportWidth, int rightExtent) {
        int totalWidth = SIDEBAR_WIDTH + rightExtent;
        if (viewportWidth < totalWidth) return (viewportWidth - rightExtent) / 2;
        return (viewportWidth - totalWidth) / 2 + SIDEBAR_WIDTH;
    }

    public static int machineTop(int viewportHeight) {
        return (viewportHeight - MACHINE_HEIGHT) / 2;
    }

    /** Left edge of the original 400px developer canvas, including room for the page selector. */
    public static int developerCanvasLeft(int viewportWidth) {
        return contentLeftWithSidebar(viewportWidth, DEVELOPER_CANVAS_WIDTH);
    }

    /** AbstractContainerScreen origin matching the right-hand work area of page_developer.xml. */
    public static int developerMenuLeft(int viewportWidth) {
        return developerCanvasLeft(viewportWidth) + DEVELOPER_MENU_X;
    }

    public static int developerSidebarLeft(int viewportWidth) {
        return developerCanvasLeft(viewportWidth) - SIDEBAR_WIDTH;
    }

    /** X coordinate used by elements centered inside the machine page. */
    public static int centeredElementX(int machineLeft, int elementWidth, int centerOffset) {
        return machineLeft + (MACHINE_WIDTH - elementWidth) / 2 + centerOffset;
    }
}
