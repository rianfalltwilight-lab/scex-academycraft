package com.mohistmc.academy.client.gui;

import java.util.Locale;

/** Pure responsive layout model shared by developer UI tests and screens. */
public record DevMachineLayout(int left, int top, int width, int height, boolean compact) {
    public static final int MIN_INTERACTIVE_WIDTH = 240;
    public static final int MIN_INTERACTIVE_HEIGHT = 200;

    public record Rect(int left, int top, int width, int height) {
        public int right() { return left + width; }
        public int bottom() { return top + height; }
    }

    /** Geometry for the four independent regions in the developer skill-tree sidebar. */
    public record SkillPanel(Rect primaryAction, Rect networkAction, Rect energy, Rect syncRate) {}

    public static DevMachineLayout fit(int screenWidth, int screenHeight) {
        int margin = Math.max(4, Math.min(10, Math.min(screenWidth, screenHeight) / 20));
        int availableW = Math.max(1, screenWidth - margin * 2);
        int availableH = Math.max(1, screenHeight - margin * 2);
        boolean compact = availableW < 400 || availableH < 240;
        int preferredW = compact ? 320 : 460;
        int preferredH = compact ? 200 : 280;
        int w = Math.min(availableW, preferredW);
        int h = Math.min(availableH, preferredH);
        return new DevMachineLayout((screenWidth - w) / 2, (screenHeight - h) / 2, w, h, compact);
    }

    public boolean interactive() { return width >= MIN_INTERACTIVE_WIDTH && height >= MIN_INTERACTIVE_HEIGHT; }

    /** Safe fallback/content rectangle; never inverted, even for a 1x1 logical viewport. */
    public Rect content() {
        int inset = Math.min(10, Math.min(width / 2, height / 2));
        return new Rect(left + inset, top + inset, Math.max(0, width - inset * 2), Math.max(0, height - inset * 2));
    }

    /** Bottom action rectangle, or a zero-size hidden action on non-interactive layouts. */
    public Rect action() {
        if (!interactive()) return new Rect(left, top, 0, 0);
        int w = Math.max(0, Math.min(100, width / 3 - 20));
        int h = Math.min(14, Math.max(0, height - 20));
        return new Rect(left + 15, top + height - h - 10, w, h);
    }

    /**
     * Matches the legacy separation of ability/action controls from wireless and machine status.
     * The caller supplies the already-computed sidebar rectangle.
     */
    public static SkillPanel skillPanel(Rect panel, boolean hasAbility, boolean normalMachine) {
        int innerLeft = panel.left() + 5;
        int innerWidth = Math.max(0, panel.width() - 10);
        int machineHeight = (normalMachine ? 18 : 0) + 18 + 2 + 18;
        int machineTop = panel.bottom() - 4 - machineHeight;
        int preferredActionTop = panel.top() + (hasAbility ? 72 : 6);
        int actionTop = Math.max(panel.top() + 4, Math.min(preferredActionTop, machineTop - 18));

        Rect primary = new Rect(innerLeft, actionTop, innerWidth, 14);
        Rect network = normalMachine
                ? new Rect(innerLeft, machineTop, innerWidth, 14)
                : new Rect(innerLeft, machineTop, 0, 0);
        int energyTop = machineTop + (normalMachine ? 18 : 0);
        Rect energy = new Rect(innerLeft, energyTop, innerWidth, 18);
        Rect sync = new Rect(innerLeft, energyTop + 20, innerWidth, 18);
        return new SkillPanel(primary, network, energy, sync);
    }

    /** Compact enough for the 1.0.7-style developer sidebar at GUI scale 2+. */
    public static String compactEnergyLabel(int energy, int maxEnergy) {
        return "IF " + compactPositive(energy) + "/" + compactPositive(maxEnergy);
    }

    private static String compactPositive(int value) {
        int safe = Math.max(0, value);
        if (safe < 10_000) return Integer.toString(safe);
        int divisor = safe >= 1_000_000 ? 1_000_000 : 1_000;
        String suffix = divisor == 1_000_000 ? "M" : "k";
        if (safe % divisor == 0) return (safe / divisor) + suffix;
        return String.format(Locale.ROOT, "%.1f%s", safe / (double) divisor, suffix);
    }
}
