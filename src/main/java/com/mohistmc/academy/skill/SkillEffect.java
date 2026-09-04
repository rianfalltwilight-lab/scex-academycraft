package com.mohistmc.academy.skill;

import net.minecraft.server.level.ServerPlayer;

public interface SkillEffect {

    String getId();

    void execute(ServerPlayer player, PlayerAbilityData data);

    /**
     * Transaction result for one-shot dispatch.  Legacy void implementations
     * are successful after their preflight; effects with a fallible spawn or
     * world commit override this so cooldown/advancement are not granted on a
     * canceled action.
     */
    default boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        execute(player, data);
        return true;
    }

    /** Dynamic server-side preflight for one-shot skills. */
    default boolean canActivate(ServerPlayer player, PlayerAbilityData data) { return true; }

    /** Whether generic activation should consume the Skill's one-shot CP/OL values. */
    default boolean appliesBaseResourceCost() { return true; }

    /** Whether generic activation should award its standard proficiency increment. */
    default boolean grantsActivationProficiency() { return true; }

    /** 返回技能冷却 tick 数(基于熟练度),默认 40 tick(2秒),子类可覆盖。 */
    default int getCooldownTicks(float proficiency) {
        return 40;
    }

    /** Most one-shot contexts queried proficiency after their own exp award; rare contexts captured it at start. */
    default boolean cooldownUsesPreActivationProficiency() { return false; }
}
