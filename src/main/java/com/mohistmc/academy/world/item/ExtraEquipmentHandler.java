package com.mohistmc.academy.world.item;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-owned behaviour for ExtraAcC-compatible armour sets. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ExtraEquipmentHandler {
    private ExtraEquipmentHandler() {}

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        boolean abilityDamage = AcademyDamageHelper.isAbilityDamageInProgress();
        List<ItemStack> imaginary = new ArrayList<>();
        float resonanceCoverage = 0;
        float imaginaryCoverage = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            float ratio = coverage(slot);
            if (stack.getItem() instanceof ResonanceArmorItem) resonanceCoverage += ratio;
            if (stack.getItem() instanceof ImagEnergyArmorItem armor) {
                imaginary.add(stack);
                imaginaryCoverage += ratio;
            }
        }

        float remaining = event.getAmount();
        if (abilityDamage && resonanceCoverage > 0) {
            remaining *= 1.0F - Math.min(0.5F, resonanceCoverage * 0.5F);
        }
        if (!imaginary.isEmpty()) {
            remaining -= imaginaryAbsorption(imaginary, event.getAmount(), abilityDamage,
                    event.getSource().is(DamageTypeTags.BYPASSES_ARMOR));
        }
        event.setAmount(Math.max(0, remaining));
    }

    private static float imaginaryAbsorption(List<ItemStack> armor, float incoming,
                                              boolean abilityDamage, boolean bypassesArmor) {
        if (bypassesArmor) {
            if (!abilityDamage) return 0;
            float coverage = 0;
            for (ItemStack stack : armor) {
                if (stack.getItem() instanceof ArmorItem item) coverage += coverage(item.getEquipmentSlot());
            }
            return incoming * Math.min(0.5F, coverage * 0.5F);
        }

        float absorbed = 0;
        float basicRatio = Math.max(1.4F, 7F - incoming / 2F) / 25F;
        for (ItemStack stack : armor) {
            if (!(stack.getItem() instanceof ImagEnergyArmorItem item)) continue;
            float slotCoverage = coverage(item.getEquipmentSlot());
            float poweredRatio = slotCoverage * (abilityDamage ? 0.95F : 0.90F);
            int available = item.getEnergyStored(stack);
            if (available >= Math.max(1, (int) (poweredRatio * 500F))) {
                float pieceAbsorbed = Math.min(incoming * poweredRatio, available / 500F);
                int cost = Math.min(available, Math.max(1, Math.round(pieceAbsorbed * 500F)));
                item.consumeForDamage(stack, cost);
                absorbed += pieceAbsorbed;
            } else {
                float fallback = abilityDamage ? 0.5F + 0.5F * basicRatio : basicRatio;
                absorbed += incoming * slotCoverage * fallback;
            }
        }
        return Math.min(incoming * 0.95F, absorbed);
    }

    private static void drainImaginary(List<ItemStack> armor, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            boolean progressed = false;
            int share = Math.max(1, (remaining + armor.size() - 1) / armor.size());
            for (ItemStack stack : armor) {
                if (!(stack.getItem() instanceof ImagEnergyArmorItem item)) continue;
                int consumed = item.consumeForDamage(stack, Math.min(share, remaining));
                remaining -= consumed;
                progressed |= consumed > 0;
                if (remaining <= 0) return;
            }
            if (!progressed) return;
        }
    }

    @SubscribeEvent
    public static void paperArmorTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof PaperArmorItem armor)) continue;
            float cp = 0.005F - 0.0025F * Math.clamp(data.getProficiency("perfect_paper"), 0, 1);
            if (!data.hasLearnedSkill("perfect_paper")
                    || !DynamicSkillRules.tryPay(data, "perfect_paper", cp, 0)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                player.drop(new ItemStack(Items.PAPER, paperCount(armor.getType())), false);
            } else {
                DynamicSkillRules.addExp(player, data, "perfect_paper", 0.000001F);
            }
        }
    }

    private static float coverage(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0.15F;
            case CHEST -> 0.40F;
            case LEGS -> 0.30F;
            case FEET -> 0.15F;
            default -> 0;
        };
    }

    private static int paperCount(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 5;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 7;
            case BOOTS -> 4;
            default -> 1;
        };
    }
}
