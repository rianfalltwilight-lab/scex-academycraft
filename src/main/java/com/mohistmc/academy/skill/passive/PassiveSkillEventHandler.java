package com.mohistmc.academy.skill.passive;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-authoritative runtime for the two held vector defenses. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class PassiveSkillEventHandler {
    private static final ThreadLocal<Boolean> REFLECTING_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static final String VECTOR_MARK = AcademyCraft.MODID + ":vec_deviated";

    /** Optional compatibility opt-in for modded entities excluded by the legacy Mob rule. */
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> VECTOR_AFFECTABLE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "vector_affectable"));

    private PassiveSkillEventHandler() {}

    private static PlayerAbilityData data(ServerPlayer player) {
        return player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    private static boolean valid(ServerPlayer player, PlayerAbilityData data, String id,
                                 VecDefenseRuntime.Mode mode) {
        return !AbilityInterferenceService.isInterfered(player)
                && data.isAbilityActive()
                && data.getCurrentAbility() == AbilityCategory.VECMANIP
                && data.hasLearnedSkill(id)
                && VecDefenseRuntime.active(player.getUUID(), mode);
    }

    private static boolean pay(PlayerAbilityData data, String skill, float cp) {
        return DynamicSkillRules.tryPay(data, skill, cp, 0);
    }

    private static boolean payForced(PlayerAbilityData data, String skill, float cp) {
        return DynamicSkillRules.payForced(data, skill, cp, 0);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = data(player);
        if (valid(player, data, "vec_deviation", VecDefenseRuntime.Mode.DEVIATION)) {
            deviation(player, data);
        } else if (valid(player, data, "vec_reflection", VecDefenseRuntime.Mode.REFLECTION)) {
            reflection(player, data);
        } else {
            VecDefenseRuntime.stop(player.getUUID());
        }
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        if (player.tickCount % 20 == 0) data.syncTo(player);
    }

    private static boolean marked(Entity entity) {
        return entity.getPersistentData().getBoolean(VECTOR_MARK);
    }

    private static void mark(Entity entity) {
        entity.getPersistentData().putBoolean(VECTOR_MARK, true);
    }

    /**
     * 1.0.7 excluded Item, Mob/Monster and XPOrb by default, but did not
     * exclude other players, armor stands or a projectile merely because its
     * owner was the defender or an ally.
     */
    private static boolean vectorEntity(ServerPlayer player, Entity entity) {
        if (!entity.isAlive() || entity == player || marked(entity)) return false;
        boolean legacyDefault = !(entity instanceof Mob)
                && !(entity instanceof ItemEntity)
                && !(entity instanceof ExperienceOrb);
        return legacyDefault || entity.getType().is(VECTOR_AFFECTABLE);
    }

    private static float difficulty(Entity entity) {
        return entity instanceof ThrownPotion ? 1.4f : entity instanceof Snowball ? 0.1f : 1.0f;
    }

    private static void deviation(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency("vec_deviation");
        float floor = VecDefenseRuntime.overloadFloor(player.getUUID());
        if (!data.isDevMode() && data.getCurrentOverload() < floor) data.setCurrentOverload(floor);

        // VecDeviationContext.s_tick terminates on a failed upkeep debit, but
        // continues the already-running tick and still force-stops nearby
        // entities. Do not return before that final legacy action.
        boolean maintained = pay(data, "vec_deviation", VecDefenseLegacyMath.deviationTickCost(proficiency));
        if (maintained) {
            // The shared client/server tick performed this second atomic debit
            // and deliberately ignored failure.
            DynamicSkillRules.tryPay(data, "vec_deviation",
                    VecDefenseLegacyMath.deviationSecondaryCp(proficiency),
                    VecDefenseLegacyMath.deviationSecondaryOverload(proficiency));
            VecDefenseRuntime.maintained(player.getUUID(), player.level().getGameTime());
        }

        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(5),
                candidate -> vectorEntity(player, candidate))) {
            if (!VecDefenseRuntime.visit(player.getUUID(), entity.getUUID())) continue;
            // consumeWithForce in 1.0.7 clamps CP to zero and still completes
            // the stop. Difficulty affected proficiency gain, not this debit.
            if (!payForced(data, "vec_deviation",
                    VecDefenseLegacyMath.deviationEntityCost(proficiency))) continue;

            float entityDifficulty = difficulty(entity);
            Vec3 cuePosition = entity.position();
            boolean needsCue = true;
            if (entity instanceof LargeFireball largeFireball) {
                int power = explosionPower(largeFireball);
                entity.discard();
                player.level().explode(null, cuePosition.x, cuePosition.y, cuePosition.z,
                        power, true, Level.ExplosionInteraction.MOB);
                // The legacy client suppressed the wave cue for fireballs;
                // their removal/explosion was the presentation.
                needsCue = false;
            } else if (entity instanceof SmallFireball) {
                entity.discard();
                needsCue = false;
            } else {
                entity.setDeltaMovement(Vec3.ZERO);
                entity.hurtMarked = true;
                if (entity instanceof AbstractArrow arrow) arrow.setBaseDamage(0);
                mark(entity);
            }
            AbilityMutationService.addSkillExp(player, data, "vec_deviation", .001f * entityDifficulty);
            if (needsCue) vectorCue(player, cuePosition, false);
        }

        if (!maintained) VecDefenseRuntime.stop(player.getUUID());
    }

    private static int explosionPower(LargeFireball fireball) {
        CompoundTag tag = new CompoundTag();
        fireball.addAdditionalSaveData(tag);
        return Math.max(0, tag.getByte("ExplosionPower"));
    }

    private static void reflection(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency("vec_reflection");
        float floor = VecDefenseRuntime.overloadFloor(player.getUUID());
        if (!data.isDevMode() && data.getCurrentOverload() < floor) data.setCurrentOverload(floor);

        Vec3 aim = player.pick(20, 0, false).getLocation();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(4),
                candidate -> vectorEntity(player, candidate))) {
            if (!VecDefenseRuntime.visit(player.getUUID(), entity.getUUID())) continue;
            // Legacy awarded this as soon as an affectable entity was seen,
            // before the ordinary (non-forced) per-entity debit.
            LegacyAdvancementBridge.award(player, "vecmanip/vec_reflection");
            float entityDifficulty = difficulty(entity);
            if (!pay(data, "vec_reflection",
                    VecDefenseLegacyMath.reflectionEntityCost(proficiency, entityDifficulty))) continue;

            double speed = entity.getDeltaMovement().length();
            Vec3 direction = aim.subtract(entity.getEyePosition());
            if (direction.lengthSqr() < 1.0e-6) direction = player.getLookAngle();
            // 1.0.7 retained the original shooter/damage attribution. In
            // 1.21.1 a fireball derives acceleration from its current motion,
            // so redirecting the vector is sufficient; reset acceleration to
            // the value a freshly recreated legacy fireball had.
            if (entity instanceof Fireball fireball) fireball.accelerationPower = .1;
            entity.setDeltaMovement(direction.normalize().scale(speed));
            entity.hurtMarked = true;
            mark(entity);
            AbilityMutationService.addSkillExp(player, data, "vec_reflection", .0008f * entityDifficulty);
            vectorCue(player, entity.position(), true);
        }

        // VecReflectionContext paid normal upkeep after processing entities.
        if (pay(data, "vec_reflection", VecDefenseLegacyMath.reflectionTickCost(proficiency))) {
            VecDefenseRuntime.maintained(player.getUUID(), player.level().getGameTime());
        } else {
            VecDefenseRuntime.stop(player.getUUID());
        }
    }

    /** Single audited entry for special rays that do not naturally enter the damage event. */
    public static boolean reflectSpecialRay(ServerPlayer reflector, Entity attacker, float incoming) {
        PlayerAbilityData data = data(reflector);
        if (REFLECTING_DAMAGE.get()
                || attacker == null
                || attacker == reflector
                || !Float.isFinite(incoming)
                || incoming < 0
                || !valid(reflector, data, "vec_reflection", VecDefenseRuntime.Mode.REFLECTION)) return false;

        // ReflectEvent in 1.0.7 was canceled solely by the live reflection
        // context. It had no damage-proportional CP debit or proficiency gain.
        LegacyAdvancementBridge.award(reflector, "vecmanip/vec_reflection");
        Vec3 delta = attacker.getEyePosition().subtract(reflector.getEyePosition());
        Vec3 point = reflector.position().add(0, reflector.getBbHeight() * .6, 0);
        if (delta.lengthSqr() > 1.0e-6) point = point.add(delta.normalize().scale(.5));
        vectorCue(reflector, point, true);
        return true;
    }

    public static boolean reflectedDamage(ServerPlayer reflector, LivingEntity target, float amount) {
        if (target == reflector || REFLECTING_DAMAGE.get()) return false;
        REFLECTING_DAMAGE.set(true);
        try {
            return AcademyDamageHelper.hurt(reflector, target,
                    reflector.damageSources().playerAttack(reflector), amount);
        } finally {
            REFLECTING_DAMAGE.remove();
        }
    }

    /** Invoked at the accepted-hit boundary before armour reductions. */
    public static void damage(AcceptedAbilityDamage event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REFLECTING_DAMAGE.get()) return;
        PlayerAbilityData data = data(player);
        float amount = event.getAmount();

        if (valid(player, data, "vec_deviation", VecDefenseRuntime.Mode.DEVIATION)) {
            float proficiency = data.getProficiency("vec_deviation");
            // min(currentCP, cost) + ordinary consume in 1.0.7 is equivalent
            // to a forced, clamped debit while preserving the defensive action.
            if (!payForced(data, "vec_deviation",
                    VecDefenseLegacyMath.deviationDamageCost(proficiency))) return;
            event.setAmount(amount * VecDefenseLegacyMath.deviationDamageMultiplier(proficiency));
            AbilityMutationService.addSkillExp(player, data, "vec_deviation", amount * .0006f);
            vectorCue(player, player.position().add(0, player.getBbHeight() * .6, 0), false);
        } else if (valid(player, data, "vec_reflection", VecDefenseRuntime.Mode.REFLECTION)) {
            float proficiency = data.getProficiency("vec_reflection");
            if (!payForced(data, "vec_reflection",
                    VecDefenseLegacyMath.reflectionDamageCost(proficiency, amount))) return;
            float reflected = VecDefenseLegacyMath.reflectedDamage(proficiency, amount);
            event.setAmount(Math.max(0, amount - reflected));
            // Final 1.12.2 commit b61e67c2 canceled a fully reflected hurt
            // event; merely setting damage to zero still allowed vanilla
            // knockback and other downstream hurt side effects.
            if (event.getAmount() <= 0) event.setCanceled(true);
            // DamageSource.getSourceOfDamage in 1.0.7 maps to the direct
            // entity, not the projectile's owner/causing entity.
            Entity source = event.getSource().getDirectEntity();
            if (source != null && source != player) {
                REFLECTING_DAMAGE.set(true);
                try {
                    AcademyDamageHelper.hurt(player, source,
                            player.damageSources().playerAttack(player), reflected);
                } finally {
                    REFLECTING_DAMAGE.remove();
                }
                vectorCue(player, source.position(), true);
            }
            AbilityMutationService.addSkillExp(player, data, "vec_reflection", amount * .0004f);
            LegacyAdvancementBridge.award(player, "vecmanip/vec_reflection");
        }

        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        data.syncTo(player);
    }

    private static void vectorCue(ServerPlayer player, Vec3 at, boolean reflection) {
        EffectHelper.waveRings(player.serverLevel(), at, player.getLookAngle(),
                reflection ? 2 : 1, reflection ? 1.1 : .6);
        AcademySounds.playSound(player.serverLevel(), at.x, at.y, at.z,
                reflection ? AcademySounds.VM_VEC_REFLECTION : AcademySounds.VM_VEC_DEVIATION,
                SoundSource.PLAYERS, .5f, 1);
    }

    private static void stop(Entity entity) {
        VecDefenseRuntime.stop(entity.getUUID());
        VecDeviationRuntime.stop(entity.getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        stop(event.getEntity());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        stop(event.getEntity());
    }

    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) {
        stop(entity);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        stop(event.getEntity());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        VecDefenseRuntime.clear();
        VecDeviationRuntime.clear();
    }
}
