package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Expanding six-to-nine-block air shell that damages, repels and clears projectiles. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AirWallEffect implements DynamicOneShotSkillEffect {
    private static final List<Ring> RINGS = new ArrayList<>();

    private static final class Ring {
        final ServerLevel level;
        final UUID owner;
        final Vec3 centre;
        final float proficiency;
        final float maximum;
        float radius;
        int age;

        Ring(ServerPlayer player, float proficiency) {
            level = player.serverLevel();
            owner = player.getUUID();
            centre = player.position();
            this.proficiency = proficiency;
            maximum = lerpf(6, 9, proficiency);
        }
    }

    @Override public String getId() { return "air_wall"; }
    @Override public float rawCp(float p) { return lerpf(500, 300, p); }
    @Override public float rawOverload(float p) { return lerpf(90, 60, p); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        RINGS.add(new Ring(player, p));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 1.0F, 0.5F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), lerpf(0.02F, 0.01F, p));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Iterator<Ring> iterator = RINGS.iterator();
        while (iterator.hasNext()) {
            Ring ring = iterator.next();
            ServerPlayer owner = ring.level.getServer().getPlayerList().getPlayer(ring.owner);
            if (owner == null || owner.serverLevel() != ring.level || !owner.isAlive()) {
                iterator.remove();
                continue;
            }
            ring.age++;
            ring.radius += 0.2F;
            if (ring.radius > ring.maximum) {
                iterator.remove();
                continue;
            }
            EffectHelper.shockwaveRing(ring.level, ring.centre.x, ring.centre.y + 0.8,
                    ring.centre.z, Math.max(4, Math.round(ring.radius * 3)), ring.radius);
            if ((ring.age & 1) != 0) continue;
            float r = ring.radius;
            AABB area = new AABB(ring.centre.x - r, ring.centre.y - 0.5, ring.centre.z - r,
                    ring.centre.x + r, ring.centre.y + 2.5, ring.centre.z + r);
            for (Entity entity : ring.level.getEntities(owner, area, Entity::isAlive)) {
                if (entity instanceof Projectile projectile) {
                    projectile.discard();
                    continue;
                }
                if (!(entity instanceof LivingEntity living) || living == owner || owner.isAlliedTo(living)) continue;
                double horizontal = Math.hypot(living.getX() - ring.centre.x,
                        living.getZ() - ring.centre.z);
                if (horizontal < r - 0.4 || horizontal > r + 0.4) continue;
                if (AcademyDamageHelper.hurt(owner, living, owner.damageSources().playerAttack(owner),
                        DynamicSkillRules.damage("air_wall", lerpf(10, 15, ring.proficiency)))) {
                    Vec3 away = new Vec3(living.getX() - ring.centre.x, 0,
                            living.getZ() - ring.centre.z);
                    if (away.lengthSqr() > 1.0e-6) {
                        away = away.normalize();
                        living.push(away.x, 0, away.z);
                        living.hurtMarked = true;
                    }
                }
            }
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { RINGS.clear(); }
    @Override public int getCooldownTicks(float p) { return Math.round(lerpf(60, 40, Math.clamp(p, 0, 1))); }
}
