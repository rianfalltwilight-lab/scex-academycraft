package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.ExtraPaperPlaneEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Throwable paper plane; Perfect Paper optionally reinforces it with CP. */
public final class PaperPlaneItem extends AcademyItem {
    public PaperPlaneItem() { super(new Properties()); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            boolean reinforced = false;
            if (data.hasLearnedSkill("perfect_paper")) {
                float proficiency = data.getProficiency("perfect_paper");
                float cp = 300F - 100F * Math.clamp(proficiency, 0, 1);
                reinforced = DynamicSkillRules.tryPay(data, "perfect_paper", cp, 0);
                if (reinforced) DynamicSkillRules.addExp(serverPlayer, data,
                        "perfect_paper", 0.001F);
            }
            ExtraPaperPlaneEntity plane = new ExtraPaperPlaneEntity(
                    AcademyEntities.PAPER_PLANE.get(), level);
            plane.launch(player, reinforced);
            if (level.addFreshEntity(plane)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                        SoundSource.PLAYERS, 0.5F, reinforced ? 1.3F : 0.9F);
            }
            data.syncTo(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
