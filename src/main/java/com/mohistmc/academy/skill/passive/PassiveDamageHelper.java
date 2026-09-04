package com.mohistmc.academy.skill.passive;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.config.DynamicSkillRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative application of passive modifiers to ability damage. */
public final class PassiveDamageHelper {
    private PassiveDamageHelper() {}

    /**
     * One audited equivalent of 1.0.7 MDDamageHelper.attack: damage first,
     * radiation mark second. This ordering deliberately leaves the creating
     * hit unamplified and lets the mark affect later damage from any source.
     */
    public static boolean meltdownerAttack(ServerPlayer player, PlayerAbilityData data, Entity target,
                                           String skillId, float damage) {
        boolean hurt = AcademyDamageHelper.hurt(player, target,
                player.damageSources().playerAttack(player), DynamicSkillRules.damage(skillId, damage));
        RadiationIntensifyRuntime.mark(player, data, target);
        return hurt;
    }

    public static CritResult teleporter(ServerPlayer player, PlayerAbilityData data, Entity target,
                                        String skillId, float damage) {
        damage=com.mohistmc.academy.config.DynamicSkillRules.damage(skillId,damage);
        if (!data.isAbilityActive()) return new CritResult(damage, -1);
        float dim = data.hasLearnedSkill("dim_folding_theorem") ? data.getProficiency("dim_folding_theorem") : -1;
        float fluct = data.hasLearnedSkill("space_fluct") ? data.getProficiency("space_fluct") : -1;
        for (int tier = 0; tier < 3; tier++) {
            if (player.getRandom().nextFloat() < PassiveSkillMath.teleportCritProbability(tier, dim, fluct)) {
                if (!data.isDevMode()) {
                    if (dim >= 0) com.mohistmc.academy.skill.AbilityMutationService.addSkillExp(player,data,"dim_folding_theorem", (tier + 1) * 0.005f);
                    if (fluct >= 0) com.mohistmc.academy.skill.AbilityMutationService.addSkillExp(player,data,"space_fluct", 0.0001f);
                }
                com.mohistmc.academy.advancement.LegacyAdvancementBridge.teleporterCritical(player);
                float multiplier = PassiveSkillMath.teleportCritMultiplier(tier);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "item.academy.factor_teleporter.crithit", multiplier));
                if (target != null && target.level() == player.level()) {
                    PacketDistributor.sendToPlayer(player, new com.mohistmc.academy.network.TeleporterCriticalPacket(
                            target.getId(), (byte) tier));
                }
                return new CritResult(damage * multiplier, tier);
            }
        }
        return new CritResult(damage, -1);
    }

    public record CritResult(float damage, int tier) {}
}
