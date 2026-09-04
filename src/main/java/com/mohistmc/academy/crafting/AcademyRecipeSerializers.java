package com.mohistmc.academy.crafting;

import com.mohistmc.academy.AcademyCraft;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AcademyRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, AcademyCraft.MODID);

    public static final Supplier<RecipeSerializer<MetalFormingRecipe>> METAL_FORMING =
            SERIALIZERS.register("metal_forming", MetalFormingRecipe.Serializer::new);
    /** Current MohistMC 1.21.1 upstream id (00e9cf09). */
    public static final Supplier<RecipeSerializer<ImagFusorRecipe>> IMAG_FUSOR =
            SERIALIZERS.register("imag_fusor", ImagFusorRecipe.Serializer::new);
    /** Legacy rebuild id accepted so existing datapacks continue to decode. */
    public static final Supplier<RecipeSerializer<ImagFusorRecipe>> IMAG_FUSING =
            SERIALIZERS.register("imag_fusing", ImagFusorRecipe.Serializer::new);
}
