package com.mohistmc.academy.client.jei;

import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.world.AcademyItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MetalFormerJeiCategory implements IRecipeCategory<RecipeHolder<MetalFormingRecipe>> {
    private final IDrawable icon;
    MetalFormerJeiCategory(IGuiHelper gui) { icon = gui.createDrawableItemLike(AcademyItems.METAL_FORMER.get()); }
    @Override public RecipeType<RecipeHolder<MetalFormingRecipe>> getRecipeType() { return AcademyJeiPlugin.METAL_FORMING; }
    @Override public Component getTitle() { return Component.translatable("container.academy.metal_former"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 116; }
    @Override public int getHeight() { return 54; }
    @Override public void setRecipe(IRecipeLayoutBuilder b, RecipeHolder<MetalFormingRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        List<ItemStack> inputs = Arrays.stream(recipe.getIngredients().getFirst().getItems())
                .map(ItemStack::copy)
                .peek(stack -> stack.setCount(recipe.getInputCount()))
                .toList();
        b.addInputSlot(8, 13).setStandardSlotBackground().addItemStacks(inputs);
        var level = Minecraft.getInstance().level;
        ItemStack output = level == null ? recipe.getOutput() : recipe.getOutput(level.registryAccess());
        b.addOutputSlot(90, 13).setOutputSlotBackground().addItemStack(output);
    }

    @Override
    public void draw(RecipeHolder<MetalFormingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        graphics.drawString(Minecraft.getInstance().font, modeLabel(holder.value()), 8, 39, 0xFF404040, false);
    }

    @Override
    public List<Component> getTooltipStrings(RecipeHolder<MetalFormingRecipe> holder, IRecipeSlotsView slots,
                                             double mouseX, double mouseY) {
        if (mouseX >= 8 && mouseX < 108 && mouseY >= 37 && mouseY < 51) {
            return List.of(modeLabel(holder.value()));
        }
        return List.of();
    }

    private static Component modeLabel(MetalFormingRecipe recipe) {
        String modeKey = "gui.academy.metal_former.mode."
                + recipe.getMode().name().toLowerCase(Locale.ROOT);
        return Component.translatable("jei.academy.metal_former.mode", Component.translatable(modeKey));
    }
}
