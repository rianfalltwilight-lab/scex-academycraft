package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.PayloadRateLimiter;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Transient, server-owned index of already-paid Ability Interferer pulses.
 * Entries deliberately never enter NBT or SavedData: unload/removal and pulse
 * expiry revoke suppression without leaving a persisted phantom source.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AbilityInterferenceService {
    private record ActiveSource(AbilityInterfererBlockEntity source, long validThrough) {}

    private static final Map<ResourceKey<Level>, ConcurrentHashMap<Long, ActiveSource>> ACTIVE =
            new ConcurrentHashMap<>();

    private AbilityInterferenceService() {}

    public static void publish(AbilityInterfererBlockEntity source, long validThrough) {
        if (!(source.getLevel() instanceof ServerLevel level)
                || source.isRemoved() || !source.isPulseActive(level.getGameTime())) return;
        ACTIVE.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(source.getBlockPos().asLong(), new ActiveSource(source, validThrough));
    }

    public static void remove(AbilityInterfererBlockEntity source) {
        if (source == null) return;
        if (source.getLevel() instanceof ServerLevel level) {
            ConcurrentHashMap<Long, ActiveSource> dimension = ACTIVE.get(level.dimension());
            if (dimension != null) {
                dimension.computeIfPresent(source.getBlockPos().asLong(),
                        (ignored, entry) -> entry.source == source ? null : entry);
                if (dimension.isEmpty()) ACTIVE.remove(level.dimension(), dimension);
            }
            return;
        }
        ACTIVE.values().forEach(entries ->
                entries.entrySet().removeIf(entry -> entry.getValue().source == source));
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static boolean isInterfered(ServerPlayer player) {
        if (player == null || player.isCreative()) return false;
        ServerLevel level = player.serverLevel();
        ConcurrentHashMap<Long, ActiveSource> dimension = ACTIVE.get(level.dimension());
        if (dimension == null || dimension.isEmpty()) return false;
        long now = level.getGameTime();

        for (Map.Entry<Long, ActiveSource> indexed : dimension.entrySet()) {
            ActiveSource entry = indexed.getValue();
            AbilityInterfererBlockEntity source = entry.source;
            if (entry.validThrough < now || source == null || source.isRemoved()
                    || source.getLevel() != level || !level.isLoaded(source.getBlockPos())
                    || level.getBlockEntity(source.getBlockPos()) != source
                    || !source.isPulseActive(now)) {
                dimension.remove(indexed.getKey(), entry);
                continue;
            }
            if (source.isWhitelisted(player.getUUID())) continue;
            var pos = source.getBlockPos();
            if (AbilityInterferenceRules.affects(false, false,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    player.getX(), player.getY(), player.getZ(), source.getRange())) {
                return true;
            }
        }
        if (dimension.isEmpty()) ACTIVE.remove(level.dimension(), dimension);
        return false;
    }

    public static void notifyBlocked(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (PayloadRateLimiter.allow(player.getUUID(), "ability_interference_feedback", now, 20, 1)) {
            player.sendSystemMessage(Component.translatable("message.academy.ability_interfered"));
        }
    }

    static int indexedSourceCount() {
        return ACTIVE.values().stream().mapToInt(Map::size).sum();
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }
}
