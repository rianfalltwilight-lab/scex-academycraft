package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Final 1.12.2 Shift Teleport: hold to preview, then place/drop and strike the path. */
public final class ShiftTpEffect implements ChargingSkillEffect {
    private record Placement(BlockHitResult hit, BlockPos target, Vec3 lineEnd) {}

    @Override public String getId() { return "shift_tp"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId()) && player.getMainHandItem().getItem() instanceof BlockItem;
    }

    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float cp = lerpf(260, 320, exp), overload = lerpf(40, 30, exp);
        Placement placement = placement(player, exp);
        return placement != null && player.getMainHandItem().getItem() instanceof BlockItem
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        float exp = data.getProficiency(getId());
        Placement placement = placement(player, exp);
        if (placement == null) return false;
        ItemStack live = player.getMainHandItem();
        if (!(live.getItem() instanceof BlockItem blockItem)) return false;

        ServerLevel level = player.serverLevel();
        ItemStack before = live.copy();
        float cp = lerpf(260, 320, exp), overload = lerpf(40, 30, exp);
        if (!DynamicSkillRules.tryPay(data, getId(), cp, overload)) return false;

        boolean placed = false;
        if (mayPlace(player, placement)) {
            BlockState oldState = level.getBlockState(placement.target);
            InteractionResult result = blockItem.useOn(
                    new UseOnContext(player, InteractionHand.MAIN_HAND, placement.hit));
            placed = result.consumesAction() && level.getBlockState(placement.target) != oldState;
            if (!placed) {
                // A block-specific survival rule can still reject placement
                // after the generic preflight. Restore the held stack before
                // taking the final 1.12.2 remote-drop branch.
                player.setItemInHand(InteractionHand.MAIN_HAND, before.copy());
            }
        }

        if (!placed) {
            ItemStack drop = before.copy();
            drop.setCount(1);
            Vec3 dropPos = placement.hit().getLocation();
            ItemEntity entity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, drop);
            if (!level.addFreshEntity(entity)) {
                // Do not delete a player's block if another mod vetoes the
                // remote entity spawn. The official outcome remains an item,
                // with the caster position as a recoverable fallback.
                player.drop(drop, false);
            }
        }

        // BlockItem#useOn normally shrinks on a successful placement. Normalize
        // both placement and remote-drop branches to the final 1.12.2 rule:
        // exactly one block is consumed in survival, none in creative.
        player.setItemInHand(InteractionHand.MAIN_HAND,
                remainingAfterOneUse(before, player.getAbilities().instabuild));

        Vec3 origin = player.position();
        int attacked = 0;
        for (Entity target : level.getEntities(player,
                new AABB(origin, placement.lineEnd).inflate(1), target -> target != player && target.isAlive()
                        && (target instanceof LivingEntity || target instanceof EnderDragonPart))) {
            if (target.getBoundingBox().clip(origin, placement.lineEnd).isEmpty()) continue;
            AcademyDamageHelper.hurt(player, target, player.damageSources().playerAttack(player),
                    PassiveDamageHelper.teleporter(player, data, target, getId(), lerpf(15, 35, exp)).damage());
            attacked++;
        }

        com.mohistmc.academy.network.SafePayloadSender.send(player,
                new com.mohistmc.academy.network.TeleporterTrailPacket(player.getX(),player.getY()-.5,player.getZ(),
                        placement.lineEnd.x,placement.lineEnd.y,placement.lineEnd.z,
                        com.mohistmc.academy.network.TeleporterTrailPacket.SHIFT));
        level.playSound(null, origin.x, origin.y, origin.z,
                AcademySounds.TP_TP_SHIFT, SoundSource.PLAYERS, .5f, 1);
        DynamicSkillRules.addExp(player, data, getId(), (1 + attacked) * .002f);
        return true;
    }

    private static ItemStack remainingAfterOneUse(ItemStack before, boolean creative) {
        ItemStack remaining = before.copy();
        if (!creative) remaining.shrink(1);
        return remaining;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(100, 60, proficiency); }

    private Placement placement(ServerPlayer player, float exp) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) return null;
        double range = lerpf(25, 35, exp);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        ServerLevel level = player.serverLevel();
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            BlockPos remote = BlockPos.containing(end);
            hit = new BlockHitResult(end, Direction.DOWN, remote, false);
        }
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        BlockPos target = context.getClickedPos();
        return new Placement(hit, target, Vec3.atCenterOf(target));
    }

    private boolean mayPlace(ServerPlayer player, Placement placement) {
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof BlockItem
                && DynamicSkillRules.destroysBlocks(level, getId())
                && level.isLoaded(placement.target)
                && level.getWorldBorder().isWithinBounds(placement.target)
                && level.mayInteract(player, placement.target)
                && player.mayUseItemAt(placement.target, placement.hit.getDirection(), stack)
                && level.getBlockState(placement.target).canBeReplaced();
    }
}
