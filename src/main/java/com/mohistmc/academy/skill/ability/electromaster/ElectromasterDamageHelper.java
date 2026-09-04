package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;

/** Shared observable behavior of AcademyCraft 1.0.7's package-private EMDamageHelper. */
final class ElectromasterDamageHelper {
    private ElectromasterDamageHelper() {}

    static boolean attack(ServerPlayer attacker, Entity target, DamageSource source, float damage) {
        boolean hurt = AcademyDamageHelper.hurt(attacker, target, source, damage);
        if (target instanceof Creeper creeper && attacker.serverLevel().random.nextFloat() < .3f) {
            powerWithoutLightningDamage(attacker.serverLevel(), creeper);
            LegacyAdvancementBridge.electromasterChargedCreeper(attacker);
        }
        return hurt;
    }

    /** Creeper.thunderHit is the public powered-state bridge; neutralize its unrelated fire/damage. */
    private static void powerWithoutLightningDamage(ServerLevel level, Creeper creeper) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        int fireTicks = creeper.getRemainingFireTicks();
        bolt.setVisualOnly(true);
        bolt.setDamage(0);
        creeper.thunderHit(level, bolt);
        creeper.setRemainingFireTicks(fireTicks);
    }
}
