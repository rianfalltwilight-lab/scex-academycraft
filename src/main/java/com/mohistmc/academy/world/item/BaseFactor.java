package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

public class BaseFactor extends AcademyItem {

    private final AbilityCategory category;

    public BaseFactor(Properties properties, AbilityCategory category) {
        // 1.0.7 induction factors were deliberately non-stackable.  Keeping
        // that invariant also lets a developer session lock one exact factor
        // without silently accepting a changed stack half way through.
        super(properties.stacksTo(1));
        this.category = category;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return super.use(level, player, hand);
        }

        ItemStack stack = player.getItemInHand(hand);

        // The release gameplay path is the developer's stimulation process,
        // as it was in 1.0.7.  A direct right click used to bypass its energy,
        // timing and server-owned session checks entirely.  Retain the old
        // creative-only shortcut solely as an explicit test aid.
        if (!player.isCreative()) {
            player.sendSystemMessage(Component.literal("请在能力开发机中开始能力诱导。"));
            return InteractionResultHolder.fail(stack);
        }

        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        AbilityCategory oldCategory = data.getCurrentAbility();
        int oldLevel = data.getPlayerLevel();
        if (data.hasAbility()) {
            player.sendSystemMessage(Component.literal("[测试用/临时] 已更换职业为: ")
                    .append(Component.translatable(category.getTranslationKey())));
            data.reset();
        }

        data.setCurrentAbility(category);
        data.setPlayerLevel(1);
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, category));
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, data.getPlayerLevel()));
        if(player instanceof net.minecraft.server.level.ServerPlayer sp)
            com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(sp,data);
        data.syncTo(player);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext p_333372_, List<Component> components, TooltipFlag tooltipFlag) {
        String key = getDescriptionId();
        Component tag = Component.translatable(key);
        if (!key.equalsIgnoreCase(tag.getString())) {
            components.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public Component getName(ItemStack p_41458_) {
        return Component.translatable("item.academy.induction_factor");
    }
}
