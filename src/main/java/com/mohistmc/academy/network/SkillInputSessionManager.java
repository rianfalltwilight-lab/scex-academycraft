package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Rejects queued old-player work as well as validly encoded inputs from a previous level/connection. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class SkillInputSessionManager {
    private record Session(ServerPlayer owner, ResourceKey<Level> dimension, SkillInputToken.Ledger ledger, long revision) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long nextRevision;
    private SkillInputSessionManager() {}
    public static boolean isCurrentPlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.hasDisconnected() && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    public static boolean canAccept(ServerPlayer player, SkillInputToken token) {
        if (!isCurrentPlayer(player)) return false;
        var session = SESSIONS.get(player.getUUID());
        return session != null && session.owner == player && session.dimension == player.level().dimension()
                && session.ledger.isFresh(token);
    }
    public static boolean accept(ServerPlayer player, SkillInputToken token) {
        if (!isCurrentPlayer(player)) return false;
        var session = SESSIONS.get(player.getUUID());
        return session != null && session.owner == player && session.dimension == player.level().dimension()
                && session.ledger.accept(token);
    }
    /** Read-only identity used by handlers and explicit server-side audit fixtures. */
    public static UUID sessionId(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        return session != null && session.owner == player && session.dimension == player.level().dimension()
                ? session.ledger.session() : SkillInputToken.ABSENT;
    }
    public static void sendCurrent(ServerPlayer player) {
        if (!isCurrentPlayer(player)) return;
        var session = SESSIONS.get(player.getUUID());
        if (session == null || session.owner != player || session.dimension != player.level().dimension()) rotate(player);
        else SafePayloadSender.send(player, new SyncSkillInputSessionPacket(session.ledger.session(), session.revision, session.dimension.location()));
    }
    public static void refresh(ServerPlayer player) {
        if (isCurrentPlayer(player) && PayloadRateLimiter.allow(player.getUUID(), "skill_input_session_feedback", player.serverLevel().getGameTime(), 20, 1)) sendCurrent(player);
    }
    private static void rotate(ServerPlayer player) {
        if (!isCurrentPlayer(player)) return;
        var session = new Session(player, player.level().dimension(), new SkillInputToken.Ledger(UUID.randomUUID()), ++nextRevision);
        SESSIONS.put(player.getUUID(), session);
        SafePayloadSender.send(player, new SyncSkillInputSessionPacket(session.ledger.session(), session.revision, session.dimension.location()));
    }
    private static void forget(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        if (session != null && session.owner == player) SESSIONS.remove(player.getUUID());
    }
    /** Called only after the final death-cancellation decision, before any respawn session is created. */
    public static void onConfirmedDeath(ServerPlayer player) { forget(player); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) rotate(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) rotate(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) rotate(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }
}