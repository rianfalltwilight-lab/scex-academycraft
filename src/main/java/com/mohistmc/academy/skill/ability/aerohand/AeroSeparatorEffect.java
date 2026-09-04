package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Immediate self-centred vacuum pulse matching ExtraAcC's non-charging context. */
public final class AeroSeparatorEffect implements DynamicOneShotSkillEffect {
    @Override public String getId() { return "aero_separator"; }
    @Override public float rawCp(float p) { return lerpf(1200, 1800, p); }
    @Override public float rawOverload(float p) { return lerpf(480, 360, p); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        AeroPassiveRuntime.terminateSustained(player, data);
        Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
        detonate(player, data, centre);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(),
                lerpf(0.002F, 0.001F, data.getProficiency(getId())));
    }

    static int detonate(ServerPlayer player, PlayerAbilityData data, Vec3 centre) {
        ServerLevel level = player.serverLevel();
        float proficiency = data.getProficiency("aero_separator");
        float radius = 3F;
        float damage = DynamicSkillRules.damage("aero_separator", lerpf(40, 60, proficiency));
        EffectHelper.glowBurst(level, centre.x, centre.y, centre.z,
                32, 0.18F, 0xAAEAFBFF, 12, 1.7);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.5F, 0.45F);
        AABB area = new AABB(centre, centre).inflate(radius);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area,
                projectile -> projectile.isAlive() && projectile.getOwner() != player)) projectile.discard();
        int affected = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            boolean hurt = living == player
                    ? AcademyDamageHelper.hurtSelf(player, living, level.damageSources().drown(), damage)
                    : AcademyDamageHelper.hurt(player, living, level.damageSources().drown(), damage);
            if (hurt) {
                living.setAirSupply(Math.min(living.getAirSupply(), 0));
                affected++;
            }
        }
        return affected;
    }

    @Override public int getCooldownTicks(float p) { return Math.round(lerpf(200, 120, Math.clamp(p, 0, 1))); }
}
