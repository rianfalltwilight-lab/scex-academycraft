package com.mohistmc.academy.client.jei;

import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.world.AcademyItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class ImagFusorJeiCategory implements IRecipeCategory<RecipeHolder<ImagFusorRecipe>> {
    private final IDrawable icon;
    ImagFusorJeiCategory(IGuiHelper gui) { icon = gui.createDrawableItemLike(AcademyItems.IMAG_FUSOR.get()); }
    @Override public RecipeType<RecipeHolder<ImagFusorRecipe>> getRecipeType() { return AcademyJeiPlugin.IMAG_FUSING; }
    @Override public Component getTitle() { return Component.translatable("container.academy.imag_fusor"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 116; }
    @Override public int getHeight() { return 48; }
    @Override public void setRecipe(IRecipeLayoutBuilder b, RecipeHolder<ImagFusorRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        b.addInputSlot(8, 16).setStandardSlotBackground().addIngredients(recipe.input());
        // Phase liquid units are consumed into the internal tank and returned
        // as empty units; they are recipe inputs, not reusable catalysts.
        // Keeping this as INPUT also lets JEI's transfer handler fill the
        // machine's non-contiguous liquid-unit slot.
        b.addSlot(RecipeIngredientRole.INPUT, 45, 16).setStandardSlotBackground()
                .addItemStack(new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), Math.max(1, (recipe.phaseLiquid() + 999) / 1000)));
        b.addOutputSlot(90, 16).setOutputSlotBackground().addItemStack(recipe.output());
    }
}
