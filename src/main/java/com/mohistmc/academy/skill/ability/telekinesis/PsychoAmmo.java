package com.mohistmc.academy.skill.ability.telekinesis;

import java.util.function.Predicate;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.PsychoProjectileEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Upstream hand priority and a synchronous admission transaction before consuming ammunition. */
final class PsychoAmmo {
    private PsychoAmmo() {}

    static ItemStack find(ServerPlayer player, Predicate<ItemStack> accepts) {
        if (player.getAbilities().instabuild) return ItemStack.EMPTY;
        if (accepts.test(player.getOffhandItem())) return player.getOffhandItem();
        if (accepts.test(player.getMainHandItem())) return player.getMainHandItem();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (accepts.test(stack)) return stack;
        }
        return null;
    }

    static boolean launch(ServerPlayer player, ItemStack ammo, ItemStack returned, float proficiency, boolean needle) {
        var level = player.serverLevel();
        boolean consume = !player.getAbilities().instabuild;
        ItemStack before = ammo.copy();
        var projectile = (needle ? AcademyEntities.PSYCHO_NEEDLE : AcademyEntities.PSYCHO_STONE).get().create(level);
        if (projectile == null) return false;
        projectile.prepare(player, returned, proficiency);
        if (!level.addFreshEntity(projectile) || projectile.isRemoved() || projectile.level() != level
                || !player.isAlive() || player.level() != level || (consume && !stillOwned(player, ammo, before))) {
            projectile.discard();
            return false;
        }
        if (consume) ammo.shrink(1);
        projectile.arm();
        return true;
    }

    private static boolean stillOwned(ServerPlayer player, ItemStack ammo, ItemStack before) {
        if (ammo.isEmpty() || !ItemStack.matches(ammo, before)) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i) == ammo) return true;
        return false;
    }
}
