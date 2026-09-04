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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Six-block placed storm core: 80-tick attraction field followed by the legacy burst. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class StormCoreEffect implements DynamicOneShotSkillEffect {
    private static final List<Core> CORES = new ArrayList<>();
    private static final class Core {
        final ServerLevel level;
        final UUID owner;
        final Vec3 position;
        final float proficiency;
        int age;
        Core(ServerPlayer owner, Vec3 position, float proficiency) {
            level = owner.serverLevel(); this.owner = owner.getUUID();
            this.position = position; this.proficiency = proficiency;
        }
    }

    @Override public String getId() { return "storm_core"; }
    @Override public float rawCp(float p) { return lerpf(3000, 2000, p); }
    @Override public float rawOverload(float p) { return lerpf(300, 200, p); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize().scale(6));
        var hit = player.serverLevel().clip(new ClipContext(from, intended, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 position = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : intended;
        CORES.add(new Core(player, position, p));
        player.serverLevel().playSound(null, position.x, position.y, position.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), lerpf(0.01F, 0.005F, p));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Iterator<Core> iterator = CORES.iterator();
        while (iterator.hasNext()) {
            Core core = iterator.next();
            ServerPlayer owner = core.level.getServer().getPlayerList().getPlayer(core.owner);
            if (owner == null || owner.serverLevel() != core.level || !owner.isAlive() || ++core.age >= 100) {
                iterator.remove();
                continue;
            }
            EffectHelper.windBurst(core.level, core.position.x, core.position.y, core.position.z,
                    core.age < 80 ? 5 : 18, core.age < 80 ? 0.45 : 1.25);
            if (core.age > 80) continue;
            AABB area = new AABB(core.position, core.position).inflate(8);
            for (Entity entity : core.level.getEntities(owner, area, Entity::isAlive)) {
                if (entity instanceof Projectile projectile) {
                    if (projectile.getOwner() != owner) projectile.discard();
                    continue;
                }
                if (!(entity instanceof LivingEntity living) || living == owner || owner.isAlliedTo(living)) continue;
                float distance = (float) Math.sqrt(living.distanceToSqr(core.position));
                float power = Math.max(1 - distance / 10F, 0.2F);
                if (core.age < 80) {
                    AcademyDamageHelper.hurt(owner, living, owner.damageSources().playerAttack(owner),
                            DynamicSkillRules.damage("storm_core", power));
                    Vec3 pull = core.position.subtract(living.position());
                    if (pull.lengthSqr() > 1.0e-6) {
                        pull = pull.normalize().scale(0.5F * power);
                        living.push(pull.x, pull.y, pull.z);
                        living.hurtMarked = true;
                    }
                } else {
                    AcademyDamageHelper.hurt(owner, living, owner.damageSources().playerAttack(owner),
                            DynamicSkillRules.damage("storm_core", lerpf(80, 120, core.proficiency) * power));
                }
            }
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CORES.clear(); }
    @Override public int getCooldownTicks(float p) { return Math.round(lerpf(450, 300, Math.clamp(p, 0, 1))); }
}
