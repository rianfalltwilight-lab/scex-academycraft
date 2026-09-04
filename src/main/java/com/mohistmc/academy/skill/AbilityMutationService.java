package com.mohistmc.academy.skill;

import com.mohistmc.academy.api.event.AbilityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/** Owner-aware boundary for authoritative mutations which need public events. No owner is retained. */
public final class AbilityMutationService {
    private AbilityMutationService() {}

    /**
     * Adds the requested proficiency and then publishes the legacy order: changed, added.
     * The Added amount is the intended amount; old/exp expose the actual clamped transition.
     */
    public static boolean addSkillExp(ServerPlayer player, PlayerAbilityData data, String skillId, float amount) {
        if (player == null || data == null || !Float.isFinite(amount) || amount <= 0 || data.isDevMode()) return false;
        if (player.getData(AcademyAttachments.PLAYER_ABILITY) != data) return false;
        Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
        if (skill == null || !data.hasLearnedSkill(skillId)) return false;
        // 1.0.7 advances the independent level gauge by the requested amount,
        // including uses of an already-maxed skill.
        data.addLevelProgress(amount);
        float oldExp = data.getProficiency(skillId);
        data.addProficiency(skillId, amount);
        float exp = data.getProficiency(skillId);
        if (Float.compare(oldExp, exp) == 0) return false;
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillExpChanged(player, skill, oldExp, exp));
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillExpAdded(player, skill, amount, oldExp, exp));
        return true;
    }
}
