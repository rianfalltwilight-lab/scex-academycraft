package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Delayed server-owned port of the 1.0.7 Electron Bomb context. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ElectronBombEffect implements SkillEffect {
    private static final double RANGE = 15;

    private static final class Ball {
        final UUID owner;
        final MdBallEntity visual;
        final float proficiency;
        int remaining;
        boolean fired;

        Ball(ServerPlayer player, int life, float proficiency, MdBallEntity visual) {
            owner = player.getUUID();
            remaining = life;
            this.proficiency = proficiency;
            this.visual = visual;
        }
    }

    private static final List<Ball> BALLS = new ArrayList<>();

    @Override public String getId() { return "electron_bomb"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    private float cp(PlayerAbilityData data) { return lerpf(35, 80, data.getProficiency(getId())); }
    private float overload(PlayerAbilityData data) { return lerpf(16, 13, data.getProficiency(getId())); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), cp(data), overload(data));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        if (!canActivate(player, data)) return false;
        float proficiency = data.getProficiency(getId());
        int life = proficiency >= .8f ? 5 : 20;
        MdBallEntity visual = new MdBallEntity(AcademyEntities.MD_BALL.get(), player.serverLevel())
                .bind(player.getUUID(), 0, false, life);
        if (!player.serverLevel().addFreshEntity(visual)) return false;
        if (!DynamicSkillRules.tryPay(data, getId(), cp(data), overload(data))) {
            visual.discard();
            return false;
        }
        DynamicSkillRules.addExp(player, data, getId(), .005f);
        BALLS.add(new Ball(player, life, proficiency, visual));
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Iterator<Ball> iterator = BALLS.iterator();
        while (iterator.hasNext()) {
            Ball ball = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(ball.owner);
            if (player == null || !player.isAlive() || AbilityInterferenceService.isInterfered(player)) {
                ball.visual.discard();
                iterator.remove();
                continue;
            }
            if (ball.visual.isRemoved()) {
                iterator.remove();
                continue;
            }

            ball.remaining--;
            // EntityMdBall invoked its callback at life - 2, then remained for
            // the final two ticks. The five-tick mastery variant fires at 3.
            if (!ball.fired && ball.remaining == 2) {
                fire(player, ball);
                ball.fired = true;
            }
            if (ball.remaining <= 0) {
                ball.visual.discard();
                iterator.remove();
            }
        }
    }

    private static void fire(ServerPlayer player, Ball ball) {
        Vec3 from = ball.visual.position();
        Vec3 destination = lookingDestination(player, RANGE);
        Entity hit = firstEntity(player, from, destination);
        if (hit != null) {
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            PassiveDamageHelper.meltdownerAttack(player, data, hit, "electron_bomb",
                    lerpf(6, 12, ball.proficiency));
        }

        // EBContextC recomputed the player's looking destination and did not
        // shorten the client ray to the server-side ball obstruction.
        EffectHelper.mdRaySmall(player.serverLevel(), from, destination, 14);
        player.serverLevel().playSound(null, from.x, from.y, from.z,
                AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, .5f, 1f);
    }

    static Vec3 lookingDestination(ServerPlayer player, double range) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize().scale(range));
        HitResult block = player.serverLevel().clip(new ClipContext(from, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double best = block.getType() == HitResult.Type.MISS
                ? from.distanceToSqr(intended) : from.distanceToSqr(block.getLocation());
        Entity nearest = null;
        for (Entity candidate : player.serverLevel().getEntities(player,
                new AABB(from, intended).inflate(1),
                candidate -> candidate != player && candidate.isAlive() && candidate.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(from, intended);
            if (intercept.isEmpty()) continue;
            double distance = from.distanceToSqr(intercept.get());
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            return nearest.position().add(0, nearest.getEyeHeight() * .6, 0);
        }
        return block.getType() == HitResult.Type.MISS ? intended : block.getLocation();
    }

    static Entity firstEntity(ServerPlayer player, Vec3 from, Vec3 destination) {
        HitResult block = player.serverLevel().clip(new ClipContext(from, destination,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double best = block.getType() == HitResult.Type.MISS
                ? from.distanceToSqr(destination) : from.distanceToSqr(block.getLocation());
        Entity nearest = null;
        for (Entity candidate : player.serverLevel().getEntities(player,
                new AABB(from, destination).inflate(1), candidate -> candidate != player
                        && !(candidate instanceof MdBallEntity)
                        && candidate.isAlive() && candidate.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(from, destination);
            if (intercept.isEmpty()) continue;
            double distance = from.distanceToSqr(intercept.get());
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BALLS.clear(); }

    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(20, 10, proficiency); }
}
