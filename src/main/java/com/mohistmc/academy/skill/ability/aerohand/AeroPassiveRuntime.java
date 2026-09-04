package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative runtime for Aerohand's four dedicated passive skills.
 *
 * <p>Offense Armour uses a short reactive stance: damage or a nearby hostile
 * projectile engages it for ten ticks, while sneaking holds it deliberately.
 * This gives the otherwise passive skill an unambiguous way to coexist with
 * Flying and preserves the documented Air Jet lockout while the armour is up.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AeroPassiveRuntime {
    private static final String ASCENDING_AIR = "ascending_air";
    private static final String AIRFLOW = "airflow";
    private static final String OFFENSE_ARMOUR = "offense_armour";
    private static final String FLYING = "flying";
    private static final long ARMOUR_HOLD_TICKS = 10;
    private static final double ARMOUR_RADIUS = 2.75;

    private static final Map<UUID, Long> ARMOUR_UNTIL = new HashMap<>();
    /** Players for whom this class, rather than another mod/game mode, granted mayfly. */
    private static final Set<UUID> GRANTED_FLIGHT = new HashSet<>();

    private AeroPassiveRuntime() {}

    private static PlayerAbilityData data(ServerPlayer player) {
        return player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    private static boolean passive(ServerPlayer player, PlayerAbilityData data, String skillId) {
        return player.isAlive() && !AbilityInterferenceService.isInterfered(player)
                && data.isAbilityActive()
                && data.getCurrentAbility() == AbilityCategory.AEROHAND
                && data.hasLearnedSkill(skillId);
    }

    public static boolean isOffenseArmourEngaged(ServerPlayer player) {
        PlayerAbilityData data = data(player);
        if (!passive(player, data, OFFENSE_ARMOUR) || player.getAbilities().flying) return false;
        long until = ARMOUR_UNTIL.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        return player.isShiftKeyDown() || until >= player.serverLevel().getGameTime();
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = data(player);

        if (!player.isAlive() || !data.isAbilityActive()
                || data.getCurrentAbility() != AbilityCategory.AEROHAND) {
            ARMOUR_UNTIL.remove(player.getUUID());
            revokeGrantedFlight(player);
            return;
        }

        detectProjectileThreat(player, data);
        boolean armour = isOffenseArmourEngaged(player);
        if (armour) {
            revokeGrantedFlight(player);
            tickOffenseArmour(player, data);
        } else if (passive(player, data, FLYING)) {
            grantFlight(player);
            if (player.getAbilities().flying) {
                player.resetFallDistance();
                if (player.tickCount % 20 == 0) {
                    AbilityMutationService.addSkillExp(player, data, FLYING, 0.0002f);
                }
            }
        } else {
            revokeGrantedFlight(player);
        }

        long until = ARMOUR_UNTIL.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (until < player.serverLevel().getGameTime() && !player.isShiftKeyDown()) {
            ARMOUR_UNTIL.remove(player.getUUID());
        }
    }

    private static void detectProjectileThreat(ServerPlayer player, PlayerAbilityData data) {
        if (!passive(player, data, OFFENSE_ARMOUR) || player.getAbilities().flying) return;
        boolean threat = !player.serverLevel().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(ARMOUR_RADIUS), projectile ->
                        projectile.isAlive() && hostileProjectile(player, projectile)).isEmpty();
        if (threat) engageArmour(player);
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner != player && (owner == null || !player.isAlliedTo(owner));
    }

    private static void engageArmour(ServerPlayer player) {
        ARMOUR_UNTIL.put(player.getUUID(),
                player.serverLevel().getGameTime() + ARMOUR_HOLD_TICKS);
    }

    private static void tickOffenseArmour(ServerPlayer player, PlayerAbilityData data) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                5, 0, false, false, false));
        for (Entity entity : player.serverLevel().getEntities(player,
                player.getBoundingBox().inflate(ARMOUR_RADIUS), Entity::isAlive)) {
            if (entity instanceof Projectile projectile) {
                if (hostileProjectile(player, projectile)) {
                    projectile.discard();
                    AbilityMutationService.addSkillExp(player, data, OFFENSE_ARMOUR, 0.0004f);
                }
                continue;
            }
            if (entity instanceof LivingEntity living && player.isAlliedTo(living)) continue;
            Vec3 away = entity.position().subtract(player.position());
            if (away.lengthSqr() < 1.0e-6) away = new Vec3(0, 0.1, 0);
            away = away.normalize().scale(0.12 + 0.10 * data.getProficiency(OFFENSE_ARMOUR));
            entity.push(away.x, Math.max(0.025, away.y * 0.25), away.z);
            entity.hurtMarked = true;
        }
        if (player.tickCount % 5 == 0) {
            EffectHelper.windBurst(player.serverLevel(), player.getX(),
                    player.getY() + player.getBbHeight() * 0.5, player.getZ(), 8, 0.4);
        }
    }

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || player.getAbilities().flying) return;
        PlayerAbilityData data = data(player);
        if (!passive(player, data, OFFENSE_ARMOUR)) return;

        engageArmour(player);
        float original = event.getAmount();
        event.setAmount(original * AeroBehaviorMath.offenseArmourDamageMultiplier(
                data.getProficiency(OFFENSE_ARMOUR)));
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != player && !player.isAlliedTo(attacker)
                && attacker.distanceToSqr(player) <= ARMOUR_RADIUS * ARMOUR_RADIUS) {
            Vec3 away = attacker.position().subtract(player.position());
            if (away.lengthSqr() > 1.0e-6) {
                away = away.normalize().scale(0.35);
                attacker.push(away.x, 0.08, away.z);
                attacker.hurtMarked = true;
            }
        }
        AbilityMutationService.addSkillExp(player, data, OFFENSE_ARMOUR,
                original * 0.0002f);
    }

    @SubscribeEvent
    public static void falling(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = data(player);
        if (!passive(player, data, ASCENDING_AIR)) return;
        float oldDistance = event.getDistance();
        float capped = AeroBehaviorMath.cappedFallDistance(oldDistance,
                event.getDamageMultiplier(), data.getProficiency(ASCENDING_AIR));
        event.setDistance(capped);
        if (capped < oldDistance) {
            AbilityMutationService.addSkillExp(player, data, ASCENDING_AIR,
                    Math.min(0.003f, (oldDistance - capped) * 0.0001f));
        }
    }

    @SubscribeEvent
    public static void breathing(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.canBreathe()) return;
        PlayerAbilityData data = data(player);
        if (!passive(player, data, AIRFLOW)) return;
        float proficiency = data.getProficiency(AIRFLOW);
        if (proficiency >= 0.999f) {
            event.setCanBreathe(true);
            event.setRefillAirAmount(Math.max(4, event.getRefillAirAmount()));
        } else {
            int interval = AeroBehaviorMath.airflowConsumptionInterval(proficiency);
            if (Math.floorMod(player.tickCount, interval) != 0) {
                event.setConsumeAirAmount(0);
            }
        }
    }

    @SubscribeEvent
    public static void drowning(LivingDrownEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = data(player);
        if (!passive(player, data, AIRFLOW)) return;
        float proficiency = data.getProficiency(AIRFLOW);
        if (proficiency >= 0.999f) {
            event.setCanceled(true);
        } else {
            event.setDamageAmount(event.getDamageAmount() * (1.0f - 0.75f * proficiency));
        }
        if (event.isDrowning()) {
            AbilityMutationService.addSkillExp(player, data, AIRFLOW, 0.0003f);
        }
    }

    private static void grantFlight(ServerPlayer player) {
        if (player.getAbilities().instabuild || player.isSpectator()
                || player.getAbilities().mayfly) return;
        player.getAbilities().mayfly = true;
        GRANTED_FLIGHT.add(player.getUUID());
        player.onUpdateAbilities();
    }

    private static void revokeGrantedFlight(ServerPlayer player) {
        if (!GRANTED_FLIGHT.remove(player.getUUID())) return;
        if (!player.getAbilities().instabuild && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
    }

    private static void clearPlayer(Entity entity) {
        ARMOUR_UNTIL.remove(entity.getUUID());
        if (entity instanceof ServerPlayer player) revokeGrantedFlight(player);
        else GRANTED_FLIGHT.remove(entity.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        ARMOUR_UNTIL.clear();
        GRANTED_FLIGHT.clear();
    }
}
