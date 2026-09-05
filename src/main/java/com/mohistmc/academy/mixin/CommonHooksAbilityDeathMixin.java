package com.mohistmc.academy.mixin;

import com.mohistmc.academy.skill.ConfirmedAbilityDeath;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs after every public death veto and before vanilla drops/saves the dying player's inventory. */
@Mixin(value = CommonHooks.class, remap = false)
public abstract class CommonHooksAbilityDeathMixin {
    @Inject(method = "onLivingDeath", at = @At("RETURN"), remap = false, require = 1)
    private static void academy$confirmedDeath(LivingEntity entity, DamageSource source,
                                               CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ()) ConfirmedAbilityDeath.accept(entity);
    }
}
