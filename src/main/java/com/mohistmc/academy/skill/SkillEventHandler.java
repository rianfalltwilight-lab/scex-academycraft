package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.RequestNodesPacket;
import com.mohistmc.academy.network.DevLearningSessionManager;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.SyncChargingStatePacket;
import com.mohistmc.academy.network.LocationTeleportActionPacket;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = AcademyCraft.MODID)
public class SkillEventHandler {

    @SubscribeEvent public static void onServerStopping(ServerStoppingEvent event) { SkillChargingManager.cancelAll(event.getServer()); }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { SkillChargingManager.clearAll(); }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        PlayerAbilityData data = event.getEntity().getData(AcademyAttachments.PLAYER_ABILITY);
        data.tick(); // 服务端和客户端都递减冷却


        if (event.getEntity() instanceof ServerPlayer player) {
            DevLearningSessionManager.tick(player);
            DevLearningSessionManager.clearExpired(player);
            LocationTeleportActionPacket.clearExpired(player);
            SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
            if (AbilityInterferenceService.isInterfered(player)) {
                if (state != null) {
                    SkillChargingManager.finalizeCharging(player, state,
                            SkillChargingManager.FinalResult.ABORTED);
                    notifyCharging(player, state, -1, 0);
                }
                FlashingSessionManager.abort(player);
                LocationTeleportActionPacket.forgetPlayer(player.getUUID());
                player.setData(AcademyAttachments.PLAYER_ABILITY, data);
                return;
            }
            if (state != null && !state.releasing) {
                if (com.mohistmc.academy.network.ChargingHandshake.serverStartExpired(state.acknowledged,state.startedAt,player.serverLevel().getGameTime())) {
                    SkillChargingManager.cancel(player);
                    notifyCharging(player,state,-1,0);
                    player.setData(AcademyAttachments.PLAYER_ABILITY, data);
                    return;
                }
                Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), state.slotIndex);
                if (skill == null || !skill.getId().equals(state.skillId)
                        || !data.isAbilityActive() || !data.hasLearnedSkill(state.skillId)) {
                    SkillChargingManager.cancel(player);
                    notifyCharging(player,state,-1,0);
                } else {
                    SkillEffect effect = skill.getEffect();
                    if (effect instanceof ChargingSkillEffect chargingEffect) {
                        if (!state.acknowledged) return;
                        state.ticks++;
                        com.mohistmc.academy.network.SafePayloadSender.send(player,
                                new SyncChargingStatePacket(state.ticks, chargingEffect.getMaxChargeTicks(data), state.slotIndex, state.skillId, state.epoch, state.generation, true));

                        ChargingSkillEffect.TickResult tickResult = chargingEffect.getTickResult(player, data, state.ticks);
                        int sessionTimeout = Math.max(1, chargingEffect.getSessionTimeoutTicks(data));
                        if (tickResult == ChargingSkillEffect.TickResult.CONTINUE && state.ticks >= sessionTimeout) {
                            tickResult = chargingEffect.getSessionTimeoutResult(player, data, state.ticks);
                        }
                        if (tickResult != ChargingSkillEffect.TickResult.CONTINUE) {
                            state.releasing = true;
                            if (tickResult == ChargingSkillEffect.TickResult.ABORT_RESOURCE) {
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.ABORTED);
                            }
                            float preCastProficiency = data.getProficiency(skill.getId());
                            // The charging session's immutable skill identity and learned/active state
                            // were checked above. Dynamic effects own their release ledger, so registry
                            // placeholder CP/OL must never veto a session which already paid its cost.
                            boolean canUse = true;
                            ChargingSettlement.TickOutcome outcome = ChargingSettlement.TickOutcome.valueOf(tickResult.name());
                            ChargingSettlement.Decision preflight = ChargingSettlement.decide(outcome,
                                    state.ticks, chargingEffect.getMinChargeTicks(data), canUse, false);
                            boolean released = preflight.attemptRelease()
                                    && chargingEffect.tryRelease(player, data, state.ticks);
                            ChargingSettlement.Decision settlement = ChargingSettlement.decide(outcome,
                                    state.ticks, chargingEffect.getMinChargeTicks(data), canUse, released);
                            if (settlement.grantProficiency()) {
                                com.mohistmc.academy.advancement.LegacyAdvancementBridge.used(player,skill);
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.RELEASED);
                                if (effect.grantsActivationProficiency()) {
                                    AbilityMutationService.addSkillExp(player, data, skill.getId(), 0.002f);
                                }
                                if (!data.isDevMode() && chargingEffect.shouldApplyCooldownAfterRelease(player, data, state.ticks)) {
                                    int cd = chargingEffect.getCooldownTicks(preCastProficiency, state.ticks);
                                    data.setCooldown(skill.getId(), cd);
                                }
                            } else if (!settlement.abortResource()) {
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.ABORTED);
                            }
                            notifyCharging(player,state,-1,0);
                            data.syncTo(player);
                        }
                    } else {
                        SkillChargingManager.cancel(player);
                        notifyCharging(player,state,-1,0);
                    }
                }
            }

            player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LearnSkillPacket.syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkillChargingManager.cancel(player);
        com.mohistmc.academy.network.SwitchPresetPacket.forget(event.getEntity().getUUID());
        RequestNodesPacket.forgetPlayer(event.getEntity().getUUID());
        ConnectToNodePacket.forgetPlayer(event.getEntity().getUUID());
        DevLearningSessionManager.clear(event.getEntity().getUUID());
        LocationTeleportActionPacket.forgetPlayer(event.getEntity().getUUID());
        com.mohistmc.academy.network.PayloadRateLimiter.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancelChargingAndNotify(player);
        LocationTeleportActionPacket.forgetPlayer(event.getEntity().getUUID());
        DevLearningSessionManager.clear(event.getEntity().getUUID());
    }

    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            cancelChargingAndNotify(player);
            LocationTeleportActionPacket.forgetPlayer(player.getUUID());
            DevLearningSessionManager.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            cancelChargingAndNotify(sp);
            DevLearningSessionManager.clear(sp.getUUID());
            LearnSkillPacket.syncToClient(sp);
        }
    }

    private static void cancelChargingAndNotify(ServerPlayer player){SkillChargingManager.ChargingState state=SkillChargingManager.getState(player.getUUID());if(state==null)return;SkillChargingManager.cancel(player);notifyCharging(player,state,-1,0);}
    private static void notifyCharging(ServerPlayer player,SkillChargingManager.ChargingState state,int ticks,int maxTicks){
        com.mohistmc.academy.network.SafePayloadSender.send(player,
                new SyncChargingStatePacket(ticks,maxTicks,state.slotIndex,state.skillId,state.epoch,state.generation,true));
    }
}
