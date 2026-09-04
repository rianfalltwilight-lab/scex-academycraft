package com.mohistmc.academy.skill;

/** Pure decision boundary for server-side charging settlement. */
public final class ChargingSettlement {
    private ChargingSettlement() {}

    public enum TickOutcome { CONTINUE, RELEASE, ABORT_RESOURCE }

    public record Decision(boolean attemptRelease, boolean grantProficiency,
                           boolean applyCooldown, boolean abortResource) {}

    public static Decision decide(TickOutcome result, int ticks, int minTicks,
                                  boolean canUse, boolean releaseSucceeded) {
        boolean attempt = result != TickOutcome.ABORT_RESOURCE
                && ticks >= minTicks && canUse;
        boolean success = attempt && releaseSucceeded;
        return new Decision(attempt, success, success,
                result == TickOutcome.ABORT_RESOURCE);
    }
}
