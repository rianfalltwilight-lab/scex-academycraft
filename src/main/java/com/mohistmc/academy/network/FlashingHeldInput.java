package com.mohistmc.academy.network;

/** Pure protocol rules shared by the Flashing server state machine and executable tests. */
public final class FlashingHeldInput {
    private FlashingHeldInput() {}
    public static boolean canHold(int currentDirection, int requestedDirection) {
        return requestedDirection >= 0 && requestedDirection < 4
                && (currentDirection == -1 || currentDirection == requestedDirection);
    }
    public static boolean canRelease(int currentDirection, int requestedDirection, long heldSince,
                                     long now, long timeoutTicks) {
        return currentDirection == requestedDirection && requestedDirection >= 0 && requestedDirection < 4
                && heldSince >= 0 && now >= heldSince && now - heldSince <= timeoutTicks;
    }
}
