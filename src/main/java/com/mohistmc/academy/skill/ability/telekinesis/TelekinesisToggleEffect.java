package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerPlayer;

/** Explicit key-toggle contexts for Psycho Harden and Liquid Shadow. */
public final class TelekinesisToggleEffect implements SkillEffect {
    private final String id;

    public TelekinesisToggleEffect(String id) {
        if (!id.equals("psycho_harden") && !id.equals("liquid_shadow"))
            throw new IllegalArgumentException("Unsupported Telekinesis toggle: " + id);
        this.id = id;
    }

    @Override public String getId() { return id; }
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        return id.equals("psycho_harden")
                ? TelekinesisPassiveHandler.togglePsychoHarden(player, data)
                : TelekinesisPassiveHandler.toggleLiquidShadow(player);
    }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        if (id.equals("psycho_harden") && TelekinesisPassiveHandler.isHardened(player)) return true;
        if (id.equals("liquid_shadow") && TelekinesisPassiveHandler.hasLiquidShadow(player)) return true;
        float p = data.getProficiency(id);
        float cp = id.equals("psycho_harden") ? 1500F - 500F * p : 2000F - 1000F * p;
        float overload = id.equals("psycho_harden") ? 100F - 50F * p : 300F - 100F * p;
        return DynamicSkillRules.canPay(data, id, cp, overload)
                && (id.equals("psycho_harden") || TelekinesisPassiveHandler.hasWaterBucket(player));
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public boolean managesOwnCooldown() { return true; }
    @Override public int getCooldownTicks(float proficiency) { return 0; }
}
