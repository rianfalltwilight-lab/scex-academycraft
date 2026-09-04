package com.mohistmc.academy.world.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Clean-room 1.21 armor attributes matching the add-on's three special materials. */
final class ExtraArmorMaterials {
    static final Holder<ArmorMaterial> RESONANCE = material("iron", defenses(0, 0, 0, 0), 25, 1.0F);
    static final Holder<ArmorMaterial> IMAGINARY = material("iron", defenses(0, 0, 0, 0), 0, 0.0F);
    static final Holder<ArmorMaterial> PAPER = material("leather", defenses(2, 5, 6, 2), 0, 4.0F);

    private ExtraArmorMaterials() {}

    /** Arguments follow the legacy array order: boots, leggings, chest, helmet. */
    private static Map<ArmorItem.Type, Integer> defenses(int boots, int leggings, int chest, int helmet) {
        EnumMap<ArmorItem.Type, Integer> result = new EnumMap<>(ArmorItem.Type.class);
        result.put(ArmorItem.Type.BOOTS, boots);
        result.put(ArmorItem.Type.LEGGINGS, leggings);
        result.put(ArmorItem.Type.CHESTPLATE, chest);
        result.put(ArmorItem.Type.HELMET, helmet);
        result.put(ArmorItem.Type.BODY, 0);
        return result;
    }

    private static Holder<ArmorMaterial> material(String vanillaTexture,
                                                   Map<ArmorItem.Type, Integer> defense,
                                                   int enchantability, float toughness) {
        ArmorMaterial value = new ArmorMaterial(defense, enchantability,
                SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace(vanillaTexture))),
                toughness, 0.0F);
        return Holder.direct(value);
    }
}
