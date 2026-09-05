package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative runtime for Aerohand's passive and sustained skills.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AeroPassiveRuntime {
    private static final String ASCENDING_AIR = "ascending_air";
    private static final String AIRFLOW = "airflow";
    private static final String OFFENSE_ARMOUR = "offense_armour";
    private static final String FLYING = "flying";
    private static final double ARMOUR_RADIUS = 2.75;
    private static final ResourceLocation ARMOUR_KNOCKBACK_RESISTANCE =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                    "offense_armour_knockback_resistance");
    private static final Set<UUID> ACTIVE_ARMOUR = new HashSet<>();
    private static final Set<UUID> ACTIVE_FLYING = new HashSet<>();
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
                && data.hasLearnedSkill(skillId) && DynamicSkillRules.enabled(skillId);
    }

    public static boolean isOffenseArmourEngaged(ServerPlayer player) {
        PlayerAbilityData data = data(player);
        return ACTIVE_ARMOUR.contains(player.getUUID()) && passive(player, data, OFFENSE_ARMOUR);
    }

    public static boolean isFlyingActive(ServerPlayer player) {
        PlayerAbilityData data = data(player);
        return ACTIVE_FLYING.contains(player.getUUID()) && passive(player, data, FLYING);
    }

    public static boolean toggleOffenseArmour(ServerPlayer player, PlayerAbilityData data) {
        if (ACTIVE_ARMOUR.contains(player.getUUID())) {
            stopArmour(player, data, true);
            return true;
        }
        float p = data.getProficiency(OFFENSE_ARMOUR);
        if (!DynamicSkillRules.tryPay(data, OFFENSE_ARMOUR,
                600F - 200F * p, 80F - 30F * p)) return false;
        stopFlying(player, data, true);
        ACTIVE_ARMOUR.add(player.getUUID());
        applyArmourKnockbackResistance(player);
        return true;
    }

    public static boolean toggleFlying(ServerPlayer player, PlayerAbilityData data) {
        if (ACTIVE_FLYING.contains(player.getUUID())) {
            stopFlying(player, data, true);
            return true;
        }
        float p = data.getProficiency(FLYING);
        if (!DynamicSkillRules.tryPay(data, FLYING, 0, 80F - 30F * p)) return false;
        stopArmour(player, data, true);
        ACTIVE_FLYING.add(player.getUUID());
        grantFlight(player);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        return true;
    }

    /** Level-five vacuum explicitly terminates both mutually exclusive sustained contexts. */
    public static void terminateSustained(ServerPlayer player, PlayerAbilityData data) {
        stopArmour(player, data, true);
        stopFlying(player, data, true);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = data(player);

        if (!player.isAlive() || !data.isAbilityActive() || AbilityInterferenceService.isInterfered(player)
                || data.getCurrentAbility() != AbilityCategory.AEROHAND) {
            stopArmour(player, data, false);
            stopFlying(player, data, false);
            return;
        }
        // Invalid passives must destroy their paid context, not merely hide its effects.
        // Otherwise interference or unlearning can restore flight/armour without a new cast.
        if (ACTIVE_ARMOUR.contains(player.getUUID()) && !passive(player, data, OFFENSE_ARMOUR))
            stopArmour(player, data, false);
        if (ACTIVE_FLYING.contains(player.getUUID()) && !passive(player, data, FLYING))
            stopFlying(player, data, false);
        if (player.tickCount % 20 == 0 && player.getAirSupply() < player.getMaxAirSupply()
                && passive(player, data, AIRFLOW) && data.getProficiency(AIRFLOW) >= 0.5F
                && DynamicSkillRules.tryPay(data, AIRFLOW, 50F, 0)) {
            player.setAirSupply(player.getMaxAirSupply());
            DynamicSkillRules.addExp(player, data, AIRFLOW, 0.001F);
        }
        boolean armour = isOffenseArmourEngaged(player);
        if (armour) {
            applyArmourKnockbackResistance(player);
            if (!DynamicSkillRules.tryPay(data, OFFENSE_ARMOUR, 1F, 0.5F)) {
                stopArmour(player, data, true);
            } else {
                tickOffenseArmour(player, data);
                DynamicSkillRules.addExp(player, data, OFFENSE_ARMOUR, 0.0001F);
            }
        } else if (isFlyingActive(player)) {
            float cp = 16F - 8F * data.getProficiency(FLYING);
            if (!DynamicSkillRules.tryPay(data, FLYING, cp, 0.5F)) {
                stopFlying(player, data, true);
                return;
            }
            grantFlight(player);
            if (player.getAbilities().flying) {
                player.resetFallDistance();
                if (player.tickCount % 20 == 0) {
                    DynamicSkillRules.addExp(player, data, FLYING, 0.002F);
                }
            }
            float p = data.getProficiency(FLYING);
            if (p >= 0.5F) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    39, p >= 0.999F ? 1 : 0, false, false, false));
        } else if (GRANTED_FLIGHT.contains(player.getUUID())) {
            revokeGrantedFlight(player);
        }
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner != player && (owner == null || !player.isAlliedTo(owner));
    }

    private static void tickOffenseArmour(ServerPlayer player, PlayerAbilityData data) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                39, data.getProficiency(OFFENSE_ARMOUR) >= 0.5F ? 3 : 4,
                false, false, false));
        player.setAirSupply(player.getMaxAirSupply());
        for (Entity entity : player.serverLevel().getEntities(player,
                player.getBoundingBox().inflate(ARMOUR_RADIUS), Entity::isAlive)) {
            if (entity instanceof Projectile projectile) {
                if (hostileProjectile(player, projectile)) {
                    if (!DynamicSkillRules.tryPay(data, OFFENSE_ARMOUR, 10F, 0)) {
                        stopArmour(player, data, true);
                        return;
                    }
                    projectile.discard();
                    DynamicSkillRules.addExp(player, data, OFFENSE_ARMOUR, 0.001F);
                }
                continue;
            }
            if (entity instanceof LivingEntity living && player.isAlliedTo(living)) continue;
            if (entity instanceof LivingEntity living) {
                if (!DynamicSkillRules.tryPay(data, OFFENSE_ARMOUR,
                        100F * living.getBbHeight(), 0)) {
                    stopArmour(player, data, true);
                    return;
                }
                com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, living,
                        player.damageSources().playerAttack(player), DynamicSkillRules.damage(
                                OFFENSE_ARMOUR, 8F + 4F * data.getProficiency(OFFENSE_ARMOUR)));
                DynamicSkillRules.addExp(player, data, OFFENSE_ARMOUR, 0.002F);
            }
            Vec3 away = entity.position().subtract(player.position());
            if (away.lengthSqr() < 1.0e-6) away = new Vec3(0, 0.1, 0);
            away = away.normalize().scale(2.0);
            entity.push(away.x, away.y, away.z);
            entity.hurtMarked = true;
        }
        if (player.tickCount % 5 == 0) {
            EffectHelper.windBurst(player.serverLevel(), player.getX(),
                    player.getY() + player.getBbHeight() * 0.5, player.getZ(), 8, 0.4);
        }
    }

    /** Invoked only after the complete public damage-veto, shield and iframe stages. */
    public static void incomingDamage(AcceptedAbilityDamage event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        PlayerAbilityData data = data(player);
        float original = event.getAmount();
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)
                && passive(player, data, ASCENDING_AIR)) {
            float p = data.getProficiency(ASCENDING_AIR);
            float capped = 10F - 5F * p;
            float saved = original - capped;
            if (saved > 0 && DynamicSkillRules.tryPay(data, ASCENDING_AIR,
                    (40F - 20F * p) * saved, 0)) {
                event.setAmount(capped);
                DynamicSkillRules.addExp(player, data, ASCENDING_AIR, saved * 0.001F);
            }
            return;
        }
        if ((event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.DROWN))
                && passive(player, data, AIRFLOW)) {
            // The released add-on accidentally keyed this curve to Ascending Air; preserve the played result.
            float p = data.getProficiency(ASCENDING_AIR);
            if (DynamicSkillRules.tryPay(data, AIRFLOW, (40F - 20F * p) * original, 0)) {
                event.setAmount(original * (1F - (0.75F + 0.20F * p)));
                DynamicSkillRules.addExp(player, data, AIRFLOW, original * 0.001F);
            }
            return;
        }
        if (!isOffenseArmourEngaged(player)
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        float proficiency = data.getProficiency(OFFENSE_ARMOUR);
        if (!DynamicSkillRules.tryPay(data, OFFENSE_ARMOUR,
                original * 20F, original * 0.5F)) {
            stopArmour(player, data, true);
            return;
        }
        event.setAmount(original * (0.10F - 0.05F * proficiency));
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
        DynamicSkillRules.addExp(player, data, OFFENSE_ARMOUR, original * 0.0005F);
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

    private static void applyArmourKnockbackResistance(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null || attribute.hasModifier(ARMOUR_KNOCKBACK_RESISTANCE)) return;
        attribute.addTransientModifier(new AttributeModifier(ARMOUR_KNOCKBACK_RESISTANCE,
                0.9, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeArmourKnockbackResistance(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute != null) attribute.removeModifier(ARMOUR_KNOCKBACK_RESISTANCE);
    }

    private static void clearPlayer(Entity entity) {
        ACTIVE_ARMOUR.remove(entity.getUUID());
        ACTIVE_FLYING.remove(entity.getUUID());
        if (entity instanceof ServerPlayer player) {
            removeArmourKnockbackResistance(player);
            revokeGrantedFlight(player);
        }
        else GRANTED_FLIGHT.remove(entity.getUUID());
    }

    private static void stopArmour(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        boolean active = ACTIVE_ARMOUR.remove(player.getUUID());
        removeArmourKnockbackResistance(player);
        if (active && cooldown && !data.isDevMode()) data.setCooldown(OFFENSE_ARMOUR, 40);
    }

    private static void stopFlying(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        boolean active = ACTIVE_FLYING.remove(player.getUUID());
        revokeGrantedFlight(player);
        if (active && cooldown && !data.isDevMode()) data.setCooldown(FLYING, 40);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clearPlayer(event.getEntity()); }
    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) { clearPlayer(entity); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        ACTIVE_ARMOUR.clear();
        ACTIVE_FLYING.clear();
        GRANTED_FLIGHT.clear();
    }
}
