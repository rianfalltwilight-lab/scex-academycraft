package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.FlashingStatePacket;
import com.mohistmc.academy.network.FlashingHeldInput;
import com.mohistmc.academy.network.SafePayloadSender;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.AcademySounds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Server-owned Flashing context: direction keys preview while held and commit only on release. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class FlashingSessionManager {
    /** Lost key-up packets cancel the preview instead of causing a delayed teleport. */
    static final long HELD_INPUT_TIMEOUT_TICKS = 40;
    private record State(long epoch, long expires, float exp, float overloadKeep, long lastBlink,
                         ResourceKey<Level> dimension, int heldDirection, long heldSince) {}
    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private FlashingSessionManager() {}

    public static boolean start(ServerPlayer player, PlayerAbilityData data) {
        Skill skill = SkillRegistry.getSkill("flashing");
        State existing = ACTIVE.get(player.getUUID());
        if (existing != null) return false;
        if (skill == null || !player.isAlive() || AbilityInterferenceService.isInterfered(player)
                || !data.isAbilityActive()
                || !data.hasLearnedSkill(skill.getId()) || data.isOnCooldown(skill.getId())) {
            SafePayloadSender.send(player, new FlashingStatePacket(false, 0)); return false;
        }
        float exp = data.getProficiency(skill.getId());
        if (!TeleportSkillHelper.consume(data,"flashing", 80 - 20 * exp, 250 - 70 * exp)) {
            SafePayloadSender.send(player, new FlashingStatePacket(false, 0)); return false;
        }
        long epoch = player.getRandom().nextLong(); if (epoch == 0) epoch = 1;
        ACTIVE.put(player.getUUID(), new State(epoch, player.serverLevel().getGameTime() + (long) (60 + 90 * exp),
                exp, data.getCurrentOverload(), -1, player.level().dimension(), -1, -1));
        SafePayloadSender.send(player, new FlashingStatePacket(true, epoch));
        return true;
    }

    /** Begin a held preview. No CP is charged and no teleport occurs here. */
    public static boolean hold(ServerPlayer player, PlayerAbilityData data, int direction, long epoch) {
        State state = ACTIVE.get(player.getUUID()); long now = player.serverLevel().getGameTime();
        if (!matches(state, direction, epoch) || invalid(player, data, state, now)) return false;
        if (!FlashingHeldInput.canHold(state.heldDirection, direction)) return false;
        ACTIVE.put(player.getUUID(), copyHeld(state, direction, now));
        return true;
    }

    /** Release the same held key to perform. Stale/mismatched releases are harmless. */
    public static boolean release(ServerPlayer player, PlayerAbilityData data, int direction, long epoch) {
        State state = ACTIVE.get(player.getUUID()); long now = player.serverLevel().getGameTime();
        if (!matches(state, direction, epoch) || state.heldDirection != direction) return false;
        ACTIVE.put(player.getUUID(), clearHeld(state));
        if (invalid(player, data, state, now)
                || !FlashingHeldInput.canRelease(state.heldDirection, direction, state.heldSince, now, HELD_INPUT_TIMEOUT_TICKS)
                || state.lastBlink == now) return false;
        Vec3 vector = FlashingTargeting.direction(player, direction);
        Vec3 destination = FlashingTargeting.destination(player, vector, 12 + 6 * state.exp);
        if (destination == null || !TeleportSkillHelper.consume(data,"flashing", 13 - 7 * state.exp, 0)) return false;
        ACTIVE.put(player.getUUID(), new State(state.epoch, state.expires, state.exp, state.overloadKeep,
                now, state.dimension, -1, -1));
        TeleportSkillHelper.teleport(player, destination); GravityCancelRuntime.start(player);
        player.serverLevel().playSound(null, player.blockPosition(), AcademySounds.TP_TP_FLASHING.value(), SoundSource.PLAYERS, 1, 1);
        com.mohistmc.academy.config.DynamicSkillRules.addExp(player,data,"flashing", .002f);
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.award(player,"teleporter/flashing");
        return true;
    }

    public static boolean cancelHold(ServerPlayer player, int direction, long epoch) {
        State state = ACTIVE.get(player.getUUID());
        if (matches(state, direction, epoch) && state.heldDirection == direction) { ACTIVE.put(player.getUUID(), clearHeld(state)); return true; }
        return false;
    }

    public static boolean end(ServerPlayer player, PlayerAbilityData data, long epoch) {
        State state = ACTIVE.get(player.getUUID()); if (state != null && state.epoch == epoch) { endNow(player, data); return true; }
        return false;
    }
    public static void abort(ServerPlayer player) {
        State state = ACTIVE.remove(player.getUUID()); if (state != null) SafePayloadSender.send(player, new FlashingStatePacket(false, state.epoch));
    }
    private static boolean matches(State state, int direction, long epoch) { return state != null && state.epoch == epoch && direction >= 0 && direction < 4; }
    private static State copyHeld(State s, int direction, long now) { return new State(s.epoch,s.expires,s.exp,s.overloadKeep,s.lastBlink,s.dimension,direction,now); }
    private static State clearHeld(State s) { return new State(s.epoch,s.expires,s.exp,s.overloadKeep,s.lastBlink,s.dimension,-1,-1); }
    private static void endNow(ServerPlayer player, PlayerAbilityData data) {
        State state = ACTIVE.remove(player.getUUID());
        if (state != null) { data.setCooldown("flashing", (int) (900 - 500 * state.exp)); SafePayloadSender.send(player, new FlashingStatePacket(false, state.epoch)); }
    }
    private static boolean invalid(ServerPlayer player, PlayerAbilityData data, State state, long now) {
        return !player.isAlive() || player.isRemoved() || AbilityInterferenceService.isInterfered(player)
                || !data.isAbilityActive() || !data.hasLearnedSkill("flashing")
                || player.level().dimension() != state.dimension || now > state.expires;
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = ACTIVE.get(player.getUUID()); if (state == null) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY); long now = player.serverLevel().getGameTime();
        if (invalid(player, data, state, now)) endNow(player, data);
        else {
            if (state.heldDirection != -1 && now - state.heldSince > HELD_INPUT_TIMEOUT_TICKS) ACTIVE.put(player.getUUID(), clearHeld(state));
            if (data.getCurrentOverload() < state.overloadKeep) data.setCurrentOverload(state.overloadKeep);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) SafePayloadSender.send(p,new FlashingStatePacket(false,0)); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) ACTIVE.remove(p.getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) abort(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) abort(p); }
    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) { if (entity instanceof ServerPlayer p) abort(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {
        for (UUID id : java.util.List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = e.getServer().getPlayerList().getPlayer(id);
            if (player != null) endNow(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
        }
        ACTIVE.clear();
    }
}
