package com.mohistmc.academy.client.jei;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.block.gui.ImagFusorGui;
import com.mohistmc.academy.client.block.gui.MetalFomerGui;
import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mohistmc.academy.world.menu.MetalFomerMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Optional;

@JeiPlugin
@OnlyIn(Dist.CLIENT)
public final class AcademyJeiPlugin implements IModPlugin {
    public static final RecipeType<RecipeHolder<MetalFormingRecipe>> METAL_FORMING =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "metal_forming"));
    public static final RecipeType<RecipeHolder<ImagFusorRecipe>> IMAG_FUSING =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "imag_fusing"));
    private static final ISubtypeInterpreter<ItemStack> ENERGY_SUBTYPE = new ISubtypeInterpreter<>() {
        @Override public Object getSubtypeData(ItemStack stack, UidContext context) {
            // Charge is presentation identity in the ingredient list, but not
            // recipe identity: vanilla item ingredients accept every charge.
            return context == UidContext.Ingredient ? energyState(stack) : null;
        }

        @SuppressWarnings({"deprecation", "removal"})
        @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return context == UidContext.Ingredient ? energyState(stack) : "";
        }
    };

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        if (Boolean.getBoolean("academy.extraJeiGate")) {
            var gate = new ExtraJeiVisualGate(runtime);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(gate::tick);
        }
    }
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "jei_plugin");
    }
    @Override public void registerItemSubtypes(ISubtypeRegistration r) {
        // The creative tab intentionally exposes a charged and an empty form
        // of both IF-powered items.  Without a subtype JEI collapses them to
        // one UID and reports both entries as duplicates during real startup.
        r.registerSubtypeInterpreter(AcademyItems.ENERGY_UNIT.get(), ENERGY_SUBTYPE);
        r.registerSubtypeInterpreter(AcademyItems.DEVELOPER_PORTABLE.get(), ENERGY_SUBTYPE);
        // Extra energy tools and armor expose the same two creative variants.
        // Each needs presentation subtypes while recipes remain charge agnostic.
        for (var entry : AcademyItems.ITEMS.getEntries()) {
            var item = entry.get();
            if (item instanceof com.mohistmc.academy.capability.IEnergyItem
                    && item != AcademyItems.ENERGY_UNIT.get() && item != AcademyItems.DEVELOPER_PORTABLE.get()) {
                r.registerSubtypeInterpreter(item, ENERGY_SUBTYPE);
            }
        }
    }
    private static String energyState(ItemStack stack) {
        return ((com.mohistmc.academy.capability.IEnergyItem) stack.getItem()).getEnergyStored(stack) <= 0 ? "empty" : "charged";
    }
    @Override public void registerCategories(IRecipeCategoryRegistration r) {
        var gui = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(new MetalFormerJeiCategory(gui), new ImagFusorJeiCategory(gui));
    }
    @Override public void registerRecipes(IRecipeRegistration r) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        r.addRecipes(METAL_FORMING, level.getRecipeManager().getAllRecipesFor(AcademyRecipeTypes.METAL_FORMING.get()));
        r.addRecipes(IMAG_FUSING, level.getRecipeManager().getAllRecipesFor(AcademyRecipeTypes.IMAG_FUSING.get()));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalyst(AcademyItems.METAL_FORMER.get(), METAL_FORMING);
        r.addRecipeCatalyst(AcademyItems.IMAG_FUSOR.get(), IMAG_FUSING);
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration r) {
        r.addRecipeClickArea(MetalFomerGui.class, 74, 43, 28, 24, METAL_FORMING);
        r.addRecipeClickArea(ImagFusorGui.class, 64, 42, 48, 24, IMAG_FUSING);
        // AcademyCraft's 1.0.7 machine composition extends beyond the vanilla
        // 176px container rectangle. Tell JEI about the real sidebar and
        // information-card bounds so its ingredient list cannot cover them.
        r.addGenericGuiContainerHandler(
                com.mohistmc.academy.client.gui.AcademyBaseUI.class,
                new IGuiContainerHandler<com.mohistmc.academy.client.gui.AcademyBaseUI<?>>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(
                            com.mohistmc.academy.client.gui.AcademyBaseUI<?> screen) {
                        return screen.getJeiExtraAreas();
                    }
                });
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration r) {
        // AcademyMenu adds the 36 player slots before MetalFomerMenu appends its
        // input/output/battery slots.  Only slot 36 is a recipe input; excluding
        // 37 and 38 prevents JEI from moving ingredients into output or power.
        r.addRecipeTransferHandler(
                MetalFomerMenu.class,
                AcademyMenus.METAL_FORMER_MENU.get(),
                METAL_FORMING,
                36, 1,
                0, 36);

        // The fusor's two recipe inputs are deliberately separated by its
        // empty-container output: 36 = full phase unit, 37 = empty unit,
        // 38 = crystal input. A contiguous range would let JEI insert into an
        // output slot, so expose the exact two slots in recipe display order.
        r.addRecipeTransferHandler(new IRecipeTransferInfo<ImagFusorMenu, RecipeHolder<ImagFusorRecipe>>() {
            @Override public Class<? extends ImagFusorMenu> getContainerClass() { return ImagFusorMenu.class; }
            @Override public Optional<MenuType<ImagFusorMenu>> getMenuType() {
                return Optional.of(AcademyMenus.IMAG_FUSOR_MENU.get());
            }
            @Override public RecipeType<RecipeHolder<ImagFusorRecipe>> getRecipeType() { return IMAG_FUSING; }
            @Override public boolean canHandle(ImagFusorMenu menu, RecipeHolder<ImagFusorRecipe> recipe) {
                return menu.slots.size() >= 41;
            }
            @Override public List<Slot> getRecipeSlots(ImagFusorMenu menu, RecipeHolder<ImagFusorRecipe> recipe) {
                return List.of(menu.slots.get(38), menu.slots.get(36));
            }
            @Override public List<Slot> getInventorySlots(ImagFusorMenu menu, RecipeHolder<ImagFusorRecipe> recipe) {
                return List.copyOf(menu.slots.subList(0, 36));
            }
        });
    }
}
