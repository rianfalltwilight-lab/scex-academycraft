package com.mohistmc.academy.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.Optional;

public record ImagFusorRecipe(Ingredient input, int phaseLiquid, ItemStack output)
        implements Recipe<ImagFusorRecipeInput> {
    public ImagFusorRecipe {
        if (phaseLiquid <= 0) throw new IllegalArgumentException("phase_liquid must be positive");
        output = output.copy();
    }
    @Override public boolean matches(ImagFusorRecipeInput in, Level level) { return input.test(in.input()); }
    @Override public ItemStack assemble(ImagFusorRecipeInput in, HolderLookup.Provider provider) { return output.copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider provider) { return output.copy(); }
    @Override public RecipeSerializer<?> getSerializer() { return AcademyRecipeSerializers.IMAG_FUSOR.get(); }
    @Override public RecipeType<?> getType() { return AcademyRecipeTypes.IMAG_FUSOR.get(); }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    public static final class Serializer implements RecipeSerializer<ImagFusorRecipe> {
        private static final MapCodec<ImagFusorRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.fieldOf("input").forGetter(ImagFusorRecipe::input),
                // MohistMC upstream 00e9cf09 uses phaseLiquid. The optional
                // snake-case field is decode-only compatibility for rebuild
                // 0.0.4-0.0.10 datapacks.
                com.mojang.serialization.Codec.INT.optionalFieldOf("phaseLiquid")
                        .forGetter(recipe -> Optional.of(recipe.phaseLiquid())),
                com.mojang.serialization.Codec.INT.optionalFieldOf("phase_liquid")
                        .forGetter(recipe -> Optional.empty()),
                ItemStack.CODEC.fieldOf("output").forGetter(ImagFusorRecipe::output)
        ).apply(i, (input, currentAmount, legacyAmount, output) -> new ImagFusorRecipe(
                input,
                currentAmount.orElseGet(() -> legacyAmount.orElseThrow(
                        () -> new IllegalArgumentException("phaseLiquid is required"))),
                output)));
        private static final StreamCodec<RegistryFriendlyByteBuf, ImagFusorRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ImagFusorRecipe::input,
                ByteBufCodecs.VAR_INT, ImagFusorRecipe::phaseLiquid,
                ItemStack.STREAM_CODEC, ImagFusorRecipe::output,
                ImagFusorRecipe::new);
        @Override public MapCodec<ImagFusorRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ImagFusorRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
