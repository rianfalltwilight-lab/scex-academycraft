package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Legacy two-stage psycho slam: lift one sighted target for 30 ticks, then drive it down. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class PsychoSlamEffect implements DynamicOneShotSkillEffect {
    private record Slam(ResourceKey<Level> dimension, UUID owner, UUID target,
                        Vec3 horizontal, float proficiency, long started) {}
    private static final Map<UUID, Slam> ACTIVE = new HashMap<>();

    @Override public String getId() { return "psycho_slam"; }
    @Override public float rawCp(float p) { return 3000 - 1000 * Math.clamp(p, 0, 1); }
    @Override public float rawOverload(float p) { return 180 - 60 * Math.clamp(p, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return findTarget(player, data.getProficiency(getId())) != null
                && DynamicOneShotSkillEffect.super.canActivate(player, data);
    }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        LivingEntity target = findTarget(player, p);
        if (target == null || !DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        launch(player, data, target, p);
        return true;
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        LivingEntity target = findTarget(player, data.getProficiency(getId()));
        if (target != null) launch(player, data, target, data.getProficiency(getId()));
    }

    private static void launch(ServerPlayer player, PlayerAbilityData data, LivingEntity target, float p) {
        Vec3 horizontal = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        if (horizontal.lengthSqr() < 1.0e-6) horizontal = new Vec3(0, 0, 1);
        ACTIVE.put(target.getUUID(), new Slam(player.level().dimension(), player.getUUID(),
                target.getUUID(), horizontal.normalize(), p, player.serverLevel().getGameTime()));
        AcademyDamageHelper.hurt(player, target, player.damageSources().playerAttack(player),
                DynamicSkillRules.damage("psycho_slam", 15 + 5 * p));
        EffectHelper.psychoBurst(player.serverLevel(), target.getX(), target.getY() + 0.8,
                target.getZ(), 18, 0.45);
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.75F, 1.35F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, "psycho_slam", 0.001F);
    }

    private static LivingEntity findTarget(ServerPlayer player, float p) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize().scale(4 + 4 * Math.clamp(p, 0, 1)));
        return SkillRaycast.trace(player, from, intended).firstEntity();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ACTIVE.entrySet().removeIf(entry -> {
            Slam slam = entry.getValue();
            ServerLevel level = event.getServer().getLevel(slam.dimension());
            if (level == null) return true;
            Entity ownerEntity = level.getEntity(slam.owner());
            Entity targetEntity = level.getEntity(slam.target());
            if (!(ownerEntity instanceof ServerPlayer owner) || !(targetEntity instanceof LivingEntity target)
                    || !owner.isAlive() || !target.isAlive()) return true;
            long age = level.getGameTime() - slam.started();
            if (age < 30) {
                Vec3 push = new Vec3(slam.horizontal().x, Math.sqrt(3), slam.horizontal().z)
                        .normalize().scale(0.2);
                target.push(push.x, push.y, push.z);
                target.hurtMarked = true;
                return false;
            }
            target.setDeltaMovement(0, -2, 0);
            target.hurtMarked = true;
            AcademyDamageHelper.hurt(owner, target, owner.damageSources().playerAttack(owner),
                    DynamicSkillRules.damage("psycho_slam", 2 * (15 + 5 * slam.proficiency())));
            EffectHelper.psychoBurst(level, target.getX(), target.getY(), target.getZ(), 24, 0.6);
            return true;
        });
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
    @Override public int getCooldownTicks(float p) { return Math.round(200 - 100 * Math.clamp(p, 0, 1)); }
}
