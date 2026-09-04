package com.mohistmc.academy.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public class SkillChargingManager {

    public enum FinalResult { RELEASED, ABORTED }

    public static class ChargingState {
        public final int slotIndex;
        public final String skillId;
        public final long epoch;
        public final long generation;
        public final long startedAt;
        public int ticks;
        public boolean releasing = false; // 防止 onChargingRelease 被重复调用
        public boolean acknowledged;

        public ChargingState(int slotIndex, String skillId, long epoch, long generation, long startedAt) {
            this.slotIndex = slotIndex;
            this.skillId = skillId;
            this.epoch = epoch;
            this.generation = generation;
            this.startedAt = startedAt;
            this.ticks = 0;
        }
    }

    private static final Map<UUID, ChargingState> STATES = new HashMap<>();

    public static ChargingState startCharging(UUID playerId, int slotIndex, String skillId, long generation, long startedAt) {
        long epoch;
        do epoch = ThreadLocalRandom.current().nextLong(); while (epoch == 0);
        ChargingState state = new ChargingState(slotIndex, skillId, epoch, generation, startedAt);
        STATES.put(playerId, state);
        return state;
    }

    public static ChargingState getState(UUID playerId) {
        return STATES.get(playerId);
    }

    public static boolean matches(ChargingState state,int slotIndex,String skillId,long epoch){return state!=null&&!state.releasing&&state.slotIndex==slotIndex&&state.skillId.equals(skillId)&&state.epoch==epoch;}

    /** The sole terminal transition for a known state. Removes before callbacks. */
    public static void finalizeCharging(ServerPlayer player, ChargingState expected, FinalResult result) {
        if (!STATES.remove(player.getUUID(), expected)) return;
        if (result == FinalResult.ABORTED) {
            Skill skill = SkillRegistry.getSkill(expected.skillId);
            if (skill != null && skill.getEffect() instanceof ChargingSkillEffect chargingEffect) {
                chargingEffect.onChargingAbort(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
            }
        }
    }

    /** Removes first, then invokes abort, guaranteeing exactly-once cleanup. */
    public static void cancel(ServerPlayer player) {
        ChargingState state = STATES.get(player.getUUID());
        if (state == null) return;
        finalizeCharging(player, state, FinalResult.ABORTED);
    }

    public static boolean isCharging(UUID playerId) {
        return STATES.containsKey(playerId);
    }
    /** Invoke context cleanup while players and their levels still exist. */
    public static void cancelAll(MinecraftServer server) {
        for (UUID playerId : java.util.List.copyOf(STATES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) cancel(player);
        }
        STATES.clear();
    }
    public static void clearAll() { STATES.clear(); }
}
