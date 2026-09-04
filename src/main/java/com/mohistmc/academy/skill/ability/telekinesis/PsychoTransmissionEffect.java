package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Picks the first obtainable dropped item intersecting the player's sight line. */
public final class PsychoTransmissionEffect implements SkillEffect {
    private static final float CP_COST = 5.0f;
    private static final float OVERLOAD_COST = 5.0f;

    @Override public String getId() { return "psycho_transmission"; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), CP_COST, OVERLOAD_COST)
                && findTarget(player, data.getProficiency(getId())) != null;
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        ItemEntity target = findTarget(player, data.getProficiency(getId()));
        if (target == null || !DynamicSkillRules.tryPay(data, getId(), CP_COST, OVERLOAD_COST)) return;

        ItemStack stack = target.getItem();
        int before = stack.getCount();
        Vec3 from = player.getEyePosition();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        target.playerTouch(player); // preserves NeoForge pickup vetoes, ownership and inventory semantics
        int after = target.isAlive() ? target.getItem().getCount() : 0;
        int picked = Math.max(0, before - after);
        if (picked == 0) {
            data.refundDynamic(DynamicSkillRules.cp(getId(), CP_COST),
                    DynamicSkillRules.overload(getId(), OVERLOAD_COST));
            return;
        }

        ServerLevel level = player.serverLevel();
        EffectHelper.electricTether(level, from, at);
        EffectHelper.psychoBurst(level, at.x, at.y, at.z, 12, 0.35);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7f, 1.35f);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), 0.005f);
    }

    static ItemEntity findTarget(ServerPlayer player, float proficiency) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize()
                .scale(TelekinesisRules.psychoTransmissionRange(proficiency)));
        var blockHit = player.serverLevel().clip(new ClipContext(from, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : intended;
        AABB search = new AABB(from, to).inflate(0.75);
        UUID playerId = player.getUUID();
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, search, item ->
                        item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay()
                                && (item.getTarget() == null || playerId.equals(item.getTarget()))
                                && hasInventorySpace(player, item.getItem())
                                && item.getBoundingBox().inflate(0.35).clip(from, to).isPresent())
                .stream()
                .min(Comparator.comparingDouble(item -> item.distanceToSqr(player)))
                .orElse(null);
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack incoming) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, incoming)
                    && slot.getCount() < slot.getMaxStackSize()) return true;
        }
        return player.getAbilities().instabuild;
    }
}
