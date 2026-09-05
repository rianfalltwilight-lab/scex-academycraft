package com.mohistmc.academy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;
import com.mohistmc.academy.skill.AbilityDamageFrames;
import java.util.Stack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge 21.1: there is no public hook after shield/iframe and before armour. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAbilityBoundaryMixin {
    @Shadow protected float lastHurt;
    @Shadow(remap = false) protected Stack<DamageContainer> damageContainers;

    @WrapMethod(method = "hurt")
    private boolean academy$scope(DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(source, amount);
        var frame = AbilityDamageFrames.enter(player, damageContainers.size(), source);
        try {
            boolean accepted = original.call(source, amount);
            AbilityDamageFrames.completed(frame, lastHurt, player.invulnerableTime);
            return accepted;
        } finally {
            // Incoming cancellation and exceptions can leave NeoForge's pushed container behind.
            // Nested reflected calls have their own frame and cannot consume their caller's container.
            while (damageContainers.size() > frame.containerDepth) damageContainers.pop();
            AbilityDamageFrames.leave(frame);
        }
    }

    @Inject(method = "hurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;lastHurt:F", opcode = Opcodes.PUTFIELD, ordinal = 1),
            require = 1)
    private void academy$beforeNormalBranchWrites(DamageSource source, float amount,
                                                   CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerPlayer player) {
            // This is after all Incoming/shield callbacks. The iframe-difference branch does not
            // write either field before actuallyHurt, so its nested accepted hits need no rollback.
            AbilityDamageFrames.current(player).captureVanillaWrites(lastHurt, player.invulnerableTime);
        }
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            cancellable = true, require = 2, order = 900)
    private void academy$settleAcceptedHit(DamageSource source, float originalAmount,
                                           CallbackInfoReturnable<Boolean> result) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        DamageContainer container = damageContainers.peek();
        var hit = AcceptedAbilityDamage.settle(player, source, container.getNewDamage());
        container.setNewDamage(hit.getAmount());
        if (hit.isCanceled()) {
            var frame = AbilityDamageFrames.current(player);
            if (frame.vanillaWrites) {
                lastHurt = frame.priorLastHurt;
                player.invulnerableTime = frame.priorInvulnerabilityTicks;
            }
            damageContainers.pop();
            // Full reflection remains a canceled whole hit: no downstream knockback or hurt animation.
            result.setReturnValue(false);
        } else {
            AbilityDamageFrames.current(player).acceptedBody = true;
        }
    }

    @ModifyArg(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            index = 1, require = 2, order = 1000)
    private float academy$settledAmount(float amount) {
        return (Object) this instanceof ServerPlayer ? damageContainers.peek().getNewDamage() : amount;
    }
}
