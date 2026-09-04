package com.mohistmc.academy.network;

/** Pure production decision seam for reordered charging start acknowledgements. */
public final class ChargingHandshake {
    private ChargingHandshake() {}
    public enum AckAction { IGNORE, CLEAR, ACCEPT, ACK_AND_CANCEL }
    public static AckAction ack(boolean pending, boolean tombstone, long expectedGeneration,
                                String expectedSkill, long generation, String skill, boolean accepted, long epoch) {
        if (!pending || expectedGeneration != generation || !java.util.Objects.equals(expectedSkill,skill)) return AckAction.IGNORE;
        if (!accepted) return AckAction.CLEAR;
        if (epoch == 0) return AckAction.IGNORE;
        return tombstone ? AckAction.ACK_AND_CANCEL : AckAction.ACCEPT;
    }
    public static boolean shouldTombstone(boolean pending, long epoch, int requestTicks) {
        return pending && epoch == 0 && requestTicks > 40;
    }
    public static boolean serverStartExpired(boolean acknowledged, long startedAt, long now) {
        return !acknowledged && now - startedAt > 100;
    }
}
