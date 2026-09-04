package com.mohistmc.academy.world.item;

import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.EntityMagHook;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class MagHook extends AcademyItem {
    public MagHook() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean consumed = !player.getAbilities().instabuild;
            EntityMagHook hook = new EntityMagHook(AcademyEntities.MAG_HOOK.get(), level);
            hook.launch(player, consumed);
            if (level.addFreshEntity(hook)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F,
                        0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
                if (consumed) stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
