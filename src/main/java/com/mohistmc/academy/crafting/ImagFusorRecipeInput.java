package com.mohistmc.academy.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record ImagFusorRecipeInput(ItemStack input) implements RecipeInput {
    @Override public ItemStack getItem(int index) { return index == 0 ? input : ItemStack.EMPTY; }
    @Override public int size() { return 1; }
}
