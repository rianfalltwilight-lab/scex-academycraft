package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerPlayer;

/** Explicit key-toggle contexts for the two ExtraAcC Aerohand sustained skills. */
public final class AeroToggleEffect implements SkillEffect {
    private final String id;

    public AeroToggleEffect(String id) {
        if (!id.equals("offense_armour") && !id.equals("flying"))
            throw new IllegalArgumentException("Unsupported Aerohand toggle: " + id);
        this.id = id;
    }

    @Override public String getId() { return id; }
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        return id.equals("offense_armour")
                ? AeroPassiveRuntime.toggleOffenseArmour(player, data)
                : AeroPassiveRuntime.toggleFlying(player, data);
    }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        if (id.equals("offense_armour") && AeroPassiveRuntime.isOffenseArmourEngaged(player)) return true;
        if (id.equals("flying") && AeroPassiveRuntime.isFlyingActive(player)) return true;
        float proficiency = data.getProficiency(id);
        float overload = 80F - 30F * proficiency;
        float cp = id.equals("offense_armour") ? 600F - 200F * proficiency : 0;
        return DynamicSkillRules.canPay(data, id, cp, overload);
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public boolean managesOwnCooldown() { return true; }
    /** Cooldown begins when the sustained context terminates, not when it starts. */
    @Override public int getCooldownTicks(float proficiency) { return 0; }
}
