package com.mohistmc.academy.client.jei;

import com.mohistmc.academy.capability.IEnergyItem;
import com.mohistmc.academy.gametest.ExtraRecipeManifest;
import com.mohistmc.academy.world.AcademyItems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Optional real JEI runtime and rendered-page acceptance driver. Not auto-loaded. */
public final class ExtraJeiVisualGate {
    private final IJeiRuntime runtime;
    private int ticks, index, age, subtypeFailures;
    private boolean checked, opened, captured, finished;
    private volatile boolean screenshotDone;
    private final StringBuilder evidence = new StringBuilder();
    public ExtraJeiVisualGate(IJeiRuntime runtime) { this.runtime=runtime; }

    public void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if (++ticks>20*240) throw new IllegalStateException("JEI gate timeout");
            if(mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
            if(!checked) { checkEnergyVariants(); runtime.getIngredientFilter().setFilterText("@academy"); checked=true; }
            if(index>=ExtraRecipeManifest.ALL.size()) {
                finish(subtypeFailures==0?"PASS":"FAIL energy variants="+subtypeFailures);
                return;
            }
            String id=ExtraRecipeManifest.ALL.get(index);
            if(!opened) {
                if(ExtraRecipeManifest.CRAFTING.contains(id)) show(RecipeTypes.CRAFTING,id);
                else if(ExtraRecipeManifest.IMAG_FUSOR.contains(id)) show(AcademyJeiPlugin.IMAG_FUSING,id);
                else show(AcademyJeiPlugin.METAL_FORMING,id);
                opened=true; age=0; return;
            }
            if(++age<25) return;
            if(mc.screen==null || !mc.screen.getClass().getName().startsWith("mezz.jei."))
                throw new IllegalStateException("JEI recipe screen absent for "+id+": "+mc.screen);
            String name=String.format("extra-jei-%02d-%s.png",index+1,id.substring(id.indexOf(':')+1));
            if(!captured) {
                screenshotDone=false; captured=true;
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->screenshotDone=true);
                return;
            }
            if(!screenshotDone) return;
            if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)))
                throw new IllegalStateException("missing screenshot "+name);
            evidence.append("RENDER ").append(id).append(" screen=").append(mc.screen.getClass().getName())
                    .append(" screenshot=").append(name).append('\n');
            mc.setScreen(null);
            index++; opened=false; captured=false;
        } catch(Throwable failure) { finish("FAIL "+failure); }
    }

    private <R extends RecipeHolder<?>> void show(RecipeType<R> type,String id) {
        var manager=runtime.getRecipeManager();
        R holder=manager.createRecipeLookup(type).get()
                .filter(recipe->recipe.id().equals(ResourceLocation.parse(id))).findFirst()
                .orElseThrow(()->new IllegalStateException("JEI runtime recipe absent "+id));
        var category=manager.getRecipeCategory(type);
        if(category==null) throw new IllegalStateException("JEI category absent "+type);
        runtime.getRecipesGui().showRecipes(category,List.of(holder),List.of());
        evidence.append("LOOKUP ").append(id).append(" category=").append(type.getUid()).append('\n');
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void checkEnergyVariants() {
        var ingredients=runtime.getIngredientManager();
        for(var entry:AcademyItems.ITEMS.getEntries()) {
            if(!(entry.get() instanceof IEnergyItem energy)) continue;
            ItemStack full=new ItemStack(entry.get());
            ItemStack empty=full.copy(); energy.setEnergy(empty,0); energy.setEnergy(full,energy.getMaxEnergyStored(full));
            var helper=ingredients.getIngredientHelper(full);
            String fullId=helper.getUniqueId(full,UidContext.Ingredient);
            String emptyId=helper.getUniqueId(empty,UidContext.Ingredient);
            boolean distinct=!fullId.equals(emptyId);
            boolean recipeEqual=helper.getUniqueId(full,UidContext.Recipe).equals(helper.getUniqueId(empty,UidContext.Recipe));
            boolean foundFull=ingredients.getAllItemStacks().stream().anyMatch(stack->stack.is(full.getItem())
                    && helper.getUniqueId(stack,UidContext.Ingredient).equals(fullId));
            boolean foundEmpty=ingredients.getAllItemStacks().stream().anyMatch(stack->stack.is(empty.getItem())
                    && helper.getUniqueId(stack,UidContext.Ingredient).equals(emptyId));
            ingredients.getAllItemStacks().stream().filter(stack->stack.is(full.getItem())).forEach(stack->
                    evidence.append("ENTRY ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                            .append(" energy=").append(energy.getEnergyStored(stack)).append(" uid=")
                            .append(helper.getUniqueId(stack,UidContext.Ingredient)).append('\n'));
            boolean pass=distinct&&recipeEqual&&foundFull&&foundEmpty;
            if(!pass) subtypeFailures++;
            evidence.append("ENERGY ").append(BuiltInRegistries.ITEM.getKey(entry.get()))
                    .append(" distinct=").append(distinct).append(" recipeEqual=").append(recipeEqual)
                    .append(" presentFull=").append(foundFull).append(" presentEmpty=").append(foundEmpty)
                    .append(" fullUID=").append(fullId).append(" emptyUID=").append(emptyId).append('\n');
        }
    }

    private void finish(String status) {
        finished=true;
        Minecraft mc=Minecraft.getInstance();
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-extra-jei-result.txt"),
                    status+"\nRendered="+index+"/"+ExtraRecipeManifest.ALL.size()+"\n"+evidence,
                    StandardOpenOption.CREATE_NEW);
        } catch(Exception failure) { throw new IllegalStateException("cannot save JEI evidence",failure); }
        mc.stop();
    }
}