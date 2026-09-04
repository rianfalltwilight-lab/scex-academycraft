package com.mohistmc.academy.network;

/** Pure state machine shared by the client bridge and protocol regression tests. */
public final class FlashingEpochState {
    private long epoch;

    public long epoch() { return epoch; }
    public boolean active() { return epoch != 0; }
    public void reset() { epoch = 0; }

    /** Returns true when the visible active state changed. */
    public boolean accept(boolean active, long incomingEpoch) {
        if (active) {
            if (incomingEpoch == 0 || incomingEpoch == epoch) return false;
            epoch = incomingEpoch;
            return true;
        }
        if (incomingEpoch != 0 && incomingEpoch != epoch) return false;
        boolean changed = epoch != 0;
        epoch = 0;
        return changed;
    }
}
