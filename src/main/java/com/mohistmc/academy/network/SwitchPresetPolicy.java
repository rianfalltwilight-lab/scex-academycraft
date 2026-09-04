package com.mohistmc.academy.network;

/** Pure validation seam for the server-authoritative preset switch request. */
public final class SwitchPresetPolicy {
    public static final long MIN_INTERVAL_TICKS = 4;
    public static final long REQUEST_INTERVAL_TICKS = 2;
    public static final long REJECT_SYNC_INTERVAL_TICKS = 20;
    private SwitchPresetPolicy() {}

    public enum Action { DROP, ACCEPT, REJECT_SYNC, REJECT_SILENT }
    public record State(long lastRequest, long lastAccepted, long lastRejectSync) {
        public static State empty() { return new State(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE); }
    }
    public record Decision(Action action, State state) {}

    public static boolean validIndex(int index, int presetCount) {
        return index >= 0 && index < presetCount;
    }

    public static boolean maySwitch(int index, int presetCount, boolean hasAbility,
                                    boolean charging, long lastAcceptedTick, long now) {
        return validIndex(index, presetCount) && hasAbility && !charging
                && (lastAcceptedTick == Long.MIN_VALUE || now - lastAcceptedTick >= MIN_INTERVAL_TICKS);
    }

    /** Every request is bounded before semantic validation; rejected traffic gets at most one sync/window. */
    public static Decision decide(int index, int presetCount, int currentIndex, boolean hasAbility,
                                  boolean charging, State old, long now) {
        if (old.lastRequest != Long.MIN_VALUE && now - old.lastRequest < REQUEST_INTERVAL_TICKS)
            return new Decision(Action.DROP, old);
        State requested = new State(now, old.lastAccepted, old.lastRejectSync);
        boolean acceptable = index != currentIndex && maySwitch(index, presetCount, hasAbility,
                charging, old.lastAccepted, now);
        if (acceptable)
            return new Decision(Action.ACCEPT, new State(now, now, old.lastRejectSync));
        if (old.lastRejectSync == Long.MIN_VALUE || now - old.lastRejectSync >= REJECT_SYNC_INTERVAL_TICKS)
            return new Decision(Action.REJECT_SYNC, new State(now, old.lastAccepted, now));
        return new Decision(Action.REJECT_SILENT, requested);
    }
}
