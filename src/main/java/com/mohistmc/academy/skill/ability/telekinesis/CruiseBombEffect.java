package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.ability.aerohand.AeroBehaviorMath;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 巡航水弹 —— consumes one water bucket and keeps a bounded group of water
 * orbs around the caster. Each orb independently acquires one nearby creature
 * or hostile projectile before it is spent; no fake explosion or fire damage
 * is involved.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class CruiseBombEffect implements SkillEffect {
    private static final double ACQUIRE_RANGE = 4.0;
    private static final int ACQUIRE_INTERVAL = 5;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final ResourceKey<Level> dimension;
        private final long expiresAt;
        private final float proficiency;
        private final int maximumOrbs;
        private int remainingOrbs;
        private long nextSummon;

        private Session(ResourceKey<Level> dimension, long expiresAt,
                        float proficiency, int orbs, long now) {
            this.dimension = dimension;
            this.expiresAt = expiresAt;
            this.proficiency = proficiency;
            this.maximumOrbs = orbs;
            this.remainingOrbs = 0;
            this.nextSummon = now;
        }
    }

    @Override
    public String getId() {
        return "cruise_bomb";
    }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        if (SESSIONS.containsKey(player.getUUID())) return true;
        boolean hasWater = player.getAbilities().instabuild || findWaterBucket(player) >= 0;
        if (!hasWater) {
            player.displayClientMessage(Component.translatable(
                    "item.academy.factor_telekinesis.cruise_bomb.desc"), true);
        }
        return hasWater && DynamicSkillRules.canPay(data, getId(), 200F, 20F);
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public boolean managesOwnCooldown() { return true; }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        if (SESSIONS.containsKey(player.getUUID())) {
            terminate(player, data, true);
            return true;
        }
        if (!canActivate(player, data) || !DynamicSkillRules.tryPay(data, getId(), 200F, 20F)) return false;
        execute(player, data);
        return true;
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        int waterSlot = findWaterBucket(player);
        if (waterSlot < 0 && !player.getAbilities().instabuild) return;
        if (!player.getAbilities().instabuild) {
            player.getInventory().setItem(waterSlot, new ItemStack(Items.BUCKET));
        }

        float proficiency = data.getProficiency(getId());
        int orbs = AeroBehaviorMath.cruiseBombOrbCount(proficiency);
        long expiresAt = player.serverLevel().getGameTime()
                + AeroBehaviorMath.cruiseBombDurationTicks(proficiency);
        SESSIONS.put(player.getUUID(), new Session(player.level().dimension(),
                expiresAt, proficiency, orbs, player.serverLevel().getGameTime()));

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.25f);
        player.serverLevel().sendParticles(ParticleTypes.SPLASH,
                player.getX(), player.getY() + player.getBbHeight() * 0.65, player.getZ(),
                orbs * 5, 0.7, 0.65, 0.7, 0.08);
    }

    private static int findWaterBucket(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.WATER_BUCKET)) return slot;
        }
        return -1;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        long now = player.serverLevel().getGameTime();
        if (!valid(player, data, session, now)
                || !DynamicSkillRules.tryPay(data, "cruise_bomb",
                2F - session.proficiency, 0.05F)) {
            terminate(player, data, true);
            return;
        }
        DynamicSkillRules.addExp(player, data, "cruise_bomb", 0.00002F);

        if (session.remainingOrbs < session.maximumOrbs && now >= session.nextSummon) {
            if (!DynamicSkillRules.tryPay(data, "cruise_bomb", 50F, 0)) {
                terminate(player, data, true);
                return;
            }
            session.remainingOrbs++;
            session.nextSummon = now + 10;
            DynamicSkillRules.addExp(player, data, "cruise_bomb", 0.0001F);
        }

        renderOrbit(player, session, now);
        if (session.remainingOrbs <= 0 || Math.floorMod(now, ACQUIRE_INTERVAL) != 0) return;
        Optional<Entity> target = findTarget(player);
        if (target.isEmpty()) return;
        if (!DynamicSkillRules.tryPay(data, "cruise_bomb", 50F, 0)) {
            terminate(player, data, true);
            return;
        }
        if (strike(player, data, session, target.get())) {
            session.remainingOrbs--;
        }
    }

    private static boolean valid(ServerPlayer player, PlayerAbilityData data,
                                 Session session, long now) {
        return player.isAlive() && !player.isRemoved()
                && !AbilityInterferenceService.isInterfered(player) && now < session.expiresAt
                && session.dimension.equals(player.level().dimension())
                && data.isAbilityActive()
                && data.getCurrentAbility() == AbilityCategory.TELEKINESIS
                && data.hasLearnedSkill("cruise_bomb");
    }

    private static void renderOrbit(ServerPlayer player, Session session, long now) {
        ServerLevel level = player.serverLevel();
        double phase = now * 0.16;
        for (int orb = 0; orb < session.remainingOrbs; orb++) {
            double angle = phase + Math.PI * 2.0 * orb / session.maximumOrbs;
            double radius = 1.0 + 0.12 * Math.sin(phase * 0.7 + orb);
            double x = player.getX() + Math.cos(angle) * radius;
            double y = player.getY() + 1.0 + 0.25 * Math.sin(angle * 1.7);
            double z = player.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.SPLASH, x, y, z,
                    2, 0.07, 0.07, 0.07, 0.015);
            if ((now + orb) % 3 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, x, y, z,
                        1, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }

    private static Optional<Entity> findTarget(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(ACQUIRE_RANGE);
        return player.serverLevel().getEntities(player, area,
                        entity -> targetable(player, entity))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr));
    }

    private static boolean targetable(ServerPlayer player, Entity entity) {
        if (!entity.isAlive() || entity == player) return false;
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != player && (owner == null || !player.isAlliedTo(owner));
        }
        return entity instanceof LivingEntity living && !player.isAlliedTo(living)
                && AcademyDamageHelper.allowsTarget(living) && player.hasLineOfSight(living);
    }

    private static boolean strike(ServerPlayer player, PlayerAbilityData data,
                                  Session session, Entity target) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position().add(0, 1.0, 0);
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 path = end.subtract(start);
        int steps = Math.max(3, Math.min(18, (int) Math.ceil(path.length() * 1.5)));
        for (int step = 1; step <= steps; step++) {
            Vec3 point = start.add(path.scale(step / (double) steps));
            level.sendParticles(ParticleTypes.SPLASH, point.x, point.y, point.z,
                    2, 0.06, 0.06, 0.06, 0.02);
        }

        boolean affected;
        if (target instanceof Projectile projectile) {
            projectile.discard();
            affected = true;
        } else if (target instanceof LivingEntity living) {
            float damage = DynamicSkillRules.damage("cruise_bomb",
                    AeroBehaviorMath.cruiseBombDamage(session.proficiency));
            affected = AcademyDamageHelper.hurt(player, living,
                    player.damageSources().playerAttack(player), damage);
        } else {
            affected = false;
        }
        if (!affected) return false;

        level.playSound(null, end.x, end.y, end.z,
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7f, 1.25f);
        level.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z,
                14, 0.35, 0.35, 0.35, 0.08);
        DynamicSkillRules.addExp(player, data, "cruise_bomb", 0.0002F);
        return true;
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return 40;
    }

    private static void terminate(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        if (SESSIONS.remove(player.getUUID()) != null && cooldown && !data.isDevMode()) {
            data.setCooldown("cruise_bomb", 40);
        }
    }

    private static void clear(Entity entity) { SESSIONS.remove(entity.getUUID()); }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }
}
