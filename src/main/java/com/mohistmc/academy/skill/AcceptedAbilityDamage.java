package com.mohistmc.academy.skill;

import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Internal settlement for a hit accepted by the outer player gates, all Incoming listeners,
 * shield blocking and hurt cooldown. Runs before armour; this is not a public NeoForge event.
 */
public final class AcceptedAbilityDamage {
    private final ServerPlayer player;
    private final DamageSource source;
    private float amount;
    private boolean canceled;

    public AcceptedAbilityDamage(ServerPlayer player, DamageSource source, float amount) {
        this.player = player;
        this.source = source;
        this.amount = amount;
    }

    public net.minecraft.world.entity.LivingEntity getEntity() { return player; }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }

    public static AcceptedAbilityDamage settle(ServerPlayer player, DamageSource source, float amount) {
        var hit = new AcceptedAbilityDamage(player, source, amount);
        // A fully blocked shield reaches actuallyHurt with zero damage, and must not activate/pay defenses.
        if (!(amount > 0) || !Float.isFinite(amount)) return hit;
        ServerPlayer attacker = AcademyDamageHelper.hostileAbilityAttacker(player, source);
        if (attacker != null)
            hit.setAmount(TelekinesisPassiveHandler.mitigateAbilityDamage(attacker, player, hit.getAmount()));
        TelekinesisPassiveHandler.incomingDamage(hit);
        AeroPassiveRuntime.incomingDamage(hit);
        PassiveSkillEventHandler.damage(hit);
        if (!hit.isCanceled()) com.mohistmc.academy.skill.ability.meltdowner.LightShieldEffect.damage(hit);
        if (!hit.isCanceled()) com.mohistmc.academy.world.item.ExtraEquipmentHandler.incomingDamage(hit);
        return hit;
    }
}
