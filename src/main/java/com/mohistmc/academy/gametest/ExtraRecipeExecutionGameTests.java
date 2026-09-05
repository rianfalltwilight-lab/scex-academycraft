package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.ImagFusorRecipeInput;
import com.mohistmc.academy.crafting.MetalFormerRecipes;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Actual menu extraction and scheduled block-entity ticks for all 29 add-on recipes.
 * Fixtures encode the audited legacy ingredients (exac IDs map to academy;
 * plateIron maps to the reconstruction's reinforced plate, matter_unit to its
 * empty unit). They do not read recipe JSON or assemble a result into a slot.
 * These server GameTests do not certify JEI rendering or human playability.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ExtraRecipeExecutionGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ExtraRecipeExecutionGameTests() {}

    @GameTest(template = "empty")
    public static void craftAirJet(GameTestHelper helper) {
        craft(helper, "air_jet", 1, List.of(" P ", "PEP", "UMU"),
                Map.of('P', "academy:reinforced_iron_plate", 'E', "academy:energy_convert_component", 'U', "minecraft:piston", 'M', "academy:matter_unit_none"));
    }

    @GameTest(template = "empty")
    public static void craftAvalon(GameTestHelper helper) {
        craft(helper, "avalon", 1, List.of("GGG", "GRG", "EIE"),
                Map.of('G', "minecraft:gold_block", 'R', "academy:resonance_component", 'E', "academy:energy_unit_group", 'I', "academy:info_component"));
    }

    @GameTest(template = "empty")
    public static void craftDropItemMagnet(GameTestHelper helper) {
        craft(helper, "drop_item_magnet", 1, List.of("C C", "CPC", "RER"),
                Map.of('C', "academy:constraint_plate", 'P', "academy:reinforced_iron_plate", 'R', "academy:reso_crystal", 'E', "academy:energy_convert_component"));
    }

    @GameTest(template = "empty")
    public static void craftElectricalibur(GameTestHelper helper) {
        craft(helper, "electricalibur", 1, List.of("RMR", "PGP", "ESE"),
                Map.of('R', "academy:reso_crystal", 'M', "academy:magnetic_coil", 'P', "academy:constraint_plate", 'G', "minecraft:golden_sword", 'E', "academy:energy_unit_group", 'S', "academy:resonance_component"));
    }

    @GameTest(template = "empty")
    public static void craftEnergyUnitGroup(GameTestHelper helper) {
        craft(helper, "energy_unit_group", 1, List.of(" D ", "EPE", "EEE"),
                Map.of('D', "academy:data_chip", 'E', "academy:energy_unit", 'P', "academy:reinforced_iron_plate"));
    }

    @GameTest(template = "empty")
    public static void craftImagBoots(GameTestHelper helper) {
        craft(helper, "imag_boots", 1, List.of("RHR", "RCR", "EOE"),
                Map.of('R', "academy:reso_crystal", 'H', "academy:reso_boots", 'C', "academy:calc_chip", 'E', "academy:energy_unit_group", 'O', "academy:optical_chip"));
    }

    @GameTest(template = "empty")
    public static void craftImagChestplate(GameTestHelper helper) {
        craft(helper, "imag_chestplate", 1, List.of("RHR", "RCR", "EOE"),
                Map.of('R', "academy:reso_crystal", 'H', "academy:reso_chestplate", 'C', "academy:calc_chip", 'E', "academy:energy_unit_group", 'O', "academy:optical_chip"));
    }

    @GameTest(template = "empty")
    public static void craftImagHelmet(GameTestHelper helper) {
        craft(helper, "imag_helmet", 1, List.of("RHR", "RCR", "EOE"),
                Map.of('R', "academy:reso_crystal", 'H', "academy:reso_helmet", 'C', "academy:calc_chip", 'E', "academy:energy_unit_group", 'O', "academy:optical_chip"));
    }

    @GameTest(template = "empty")
    public static void craftImagLeggings(GameTestHelper helper) {
        craft(helper, "imag_leggings", 1, List.of("RHR", "RCR", "EOE"),
                Map.of('R', "academy:reso_crystal", 'H', "academy:reso_leggings", 'C', "academy:calc_chip", 'E', "academy:energy_unit_group", 'O', "academy:optical_chip"));
    }

    @GameTest(template = "empty")
    public static void craftLasorComponent(GameTestHelper helper) {
        craft(helper, "lasor_component", 1, List.of("ORE"),
                Map.of('O', "academy:optical_chip", 'R', "academy:resonance_component", 'E', "minecraft:emerald"));
    }

    @GameTest(template = "empty")
    public static void craftLasorGun(GameTestHelper helper) {
        craft(helper, "lasor_gun", 1, List.of("LCP", "EEP"),
                Map.of('L', "academy:lasor_component", 'C', "academy:calc_chip", 'P', "academy:reinforced_iron_plate", 'E', "academy:energy_unit_group"));
    }

    @GameTest(template = "empty")
    public static void craftOpticalChip(GameTestHelper helper) {
        craft(helper, "optical_chip", 1, List.of(" S ", "SCS", " S "),
                Map.of('S', "academy:imag_silicon_ingot", 'C', "academy:calc_chip"));
    }

    @GameTest(template = "empty")
    public static void craftPaperBoots(GameTestHelper helper) {
        craft(helper, "paper_boots", 1, List.of("P P", "P P"),
                Map.of('P', "minecraft:paper"));
    }

    @GameTest(template = "empty")
    public static void craftPaperChestplate(GameTestHelper helper) {
        craft(helper, "paper_chestplate", 1, List.of("P P", "PPP", "PPP"),
                Map.of('P', "minecraft:paper"));
    }

    @GameTest(template = "empty")
    public static void craftPaperHelmet(GameTestHelper helper) {
        craft(helper, "paper_helmet", 1, List.of("PPP", "P P"),
                Map.of('P', "minecraft:paper"));
    }

    @GameTest(template = "empty")
    public static void craftPaperLeggings(GameTestHelper helper) {
        craft(helper, "paper_leggings", 1, List.of("PPP", "P P", "P P"),
                Map.of('P', "minecraft:paper"));
    }

    @GameTest(template = "empty")
    public static void craftPaperPlane(GameTestHelper helper) {
        craft(helper, "paper_plane", 4, List.of("PPP", " P "),
                Map.of('P', "minecraft:paper"));
    }

    @GameTest(template = "empty")
    public static void craftRayTwister(GameTestHelper helper) {
        craft(helper, "ray_twister", 1, List.of(" R ", " O ", "PEP"),
                Map.of('R', "academy:resonance_component", 'O', "academy:optical_chip", 'P', "academy:reinforced_iron_plate", 'E', "academy:energy_convert_component"));
    }

    @GameTest(template = "empty")
    public static void craftResoBoots(GameTestHelper helper) {
        craft(helper, "reso_boots", 1, List.of("C C", "CRC"),
                Map.of('C', "academy:constraint_plate", 'R', "academy:reso_crystal"));
    }

    @GameTest(template = "empty")
    public static void craftResoChestplate(GameTestHelper helper) {
        craft(helper, "reso_chestplate", 1, List.of("C C", "CRC", "CCC"),
                Map.of('C', "academy:constraint_plate", 'R', "academy:reso_crystal"));
    }

    @GameTest(template = "empty")
    public static void craftResoHelmet(GameTestHelper helper) {
        craft(helper, "reso_helmet", 1, List.of("CRC", "C C"),
                Map.of('C', "academy:constraint_plate", 'R', "academy:reso_crystal"));
    }

    @GameTest(template = "empty")
    public static void craftResoLeggings(GameTestHelper helper) {
        craft(helper, "reso_leggings", 1, List.of("CRC", "C C", "C C"),
                Map.of('C', "academy:constraint_plate", 'R', "academy:reso_crystal"));
    }

    @GameTest(template = "empty")
    public static void craftTeleporter(GameTestHelper helper) {
        craft(helper, "teleporter", 1, List.of(" D ", "PEP", "ORO"),
                Map.of('D', "academy:data_chip", 'P', "academy:reinforced_iron_plate", 'E', "academy:energy_convert_component", 'O', "minecraft:ender_eye", 'R', "academy:resonance_component"));
    }

    private static void craft(GameTestHelper helper, String outputPath, int outputCount,
                              List<String> pattern, Map<Character, String> ingredients) {
        String recipeId = "academy:extra_" + outputPath;
        check(helper, ExtraRecipeManifest.CRAFTING.contains(recipeId), "unlisted crafting fixture " + recipeId);
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[ExtraRecipe]"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        player.getInventory().clearContent();
        var menu = new CraftingMenu(41, player.getInventory(), ContainerLevelAccess.create(level, pos));
        player.containerMenu = menu;
        try {
            List<ItemStack> grid = new ArrayList<>();
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    char symbol = y < pattern.size() && x < pattern.get(y).length() ? pattern.get(y).charAt(x) : ' ';
                    ItemStack input = symbol == ' ' ? ItemStack.EMPTY : new ItemStack(item(ingredients.get(symbol)));
                    // Charged components still match, but crafting must not create free output energy.
                    if (EnergyItemHelper.isEnergyItem(input)) EnergyItemHelper.setEnergy(input, Integer.MAX_VALUE);
                    grid.add(input);
                }
            }
            var selected = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                    CraftingInput.of(3, 3, grid), level).orElseThrow();
            check(helper, selected.id().equals(ResourceLocation.parse(recipeId)),
                    recipeId + " resolves to competing recipe " + selected.id());
            fill(menu, grid, false);
            assertOutput(helper, menu.getSlot(0).getItem(), "academy:" + outputPath, outputCount, recipeId + " preview");

            int firstIngredient = 0;
            while (grid.get(firstIngredient).isEmpty()) firstIngredient++;
            menu.getSlot(firstIngredient + 1).set(ItemStack.EMPTY);
            check(helper, menu.getSlot(0).getItem().isEmpty(), recipeId + " accepts a missing ingredient");
            menu.getSlot(firstIngredient + 1).set(new ItemStack(Items.DIRT));
            check(helper, menu.getSlot(0).getItem().isEmpty(), recipeId + " accepts the wrong ingredient");

            fill(menu, grid, true);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            assertOutput(helper, menu.getCarried(), "academy:" + outputPath, outputCount, recipeId + " picked result");
            assertEmptyEnergy(helper, menu.getCarried(), recipeId);
            for (int i = 0; i < grid.size(); i++) {
                ItemStack input = grid.get(i);
                int before = input.isEmpty() ? 0 : Math.min(2, input.getMaxStackSize());
                ItemStack remaining = menu.getSlot(i + 1).getItem();
                check(helper, remaining.getCount() == Math.max(0, before - 1),
                        recipeId + " wrong ingredient debit in grid slot " + i);
                if (!remaining.isEmpty()) {
                    check(helper, ItemStack.isSameItemSameComponents(input, remaining),
                            recipeId + " changed remaining ingredient components in grid slot " + i);
                }
            }

            menu.setCarried(ItemStack.EMPTY);
            fill(menu, grid, false);
            player.getInventory().clearContent();
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            int received = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    check(helper, stack.is(item("academy:" + outputPath)), recipeId + " unexpected crafting remainder");
                    assertEmptyEnergy(helper, stack, recipeId);
                    received += stack.getCount();
                }
            }
            check(helper, received == outputCount, recipeId + " shift-click result count " + received);
            for (int i = 1; i <= 9; i++) check(helper, menu.getSlot(i).getItem().isEmpty(),
                    recipeId + " shift-click left or duplicated ingredients at " + i);
            check(helper, menu.getSlot(0).getItem().isEmpty(), recipeId + " result persisted after input exhaustion");
            LOGGER.info("EXTRA_RECIPE_EXECUTION_PASS {} crafting-menu pickup+shift, wrong/missing input, exact debit, empty energy", recipeId);
            helper.succeed();
        } finally {
            menu.setCarried(ItemStack.EMPTY);
            menu.clearCraftingContent();
            player.getInventory().clearContent();
            player.containerMenu = player.inventoryMenu;
        }
    }

    private static void fill(CraftingMenu menu, List<ItemStack> grid, boolean doubles) {
        menu.beginPlacingRecipe();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack input = grid.get(i);
            menu.getSlot(i + 1).set(input.isEmpty() ? ItemStack.EMPTY :
                    input.copyWithCount(doubles ? Math.min(2, input.getMaxStackSize()) : 1));
        }
        menu.finishPlacingRecipe(null);
    }


    @GameTest(template = "empty", timeoutTicks = 220)
    public static void fuseCpPotion(GameTestHelper helper) {
        fuse(helper, "cp_potion", "minecraft:glass_bottle", 1, "academy:cp_potion");
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void fuseFuseConstraintIngot(GameTestHelper helper) {
        fuse(helper, "fuse_constraint_ingot", "minecraft:iron_ingot", 1, "academy:constraint_ingot");
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void fuseFuseCrystalLow(GameTestHelper helper) {
        fuse(helper, "fuse_crystal_low", "minecraft:redstone", 2, "academy:crystal_low");
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void fuseFuseImagSilicon(GameTestHelper helper) {
        fuse(helper, "fuse_imag_silicon", "minecraft:sand", 1, "academy:imag_silicon_ingot");
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void fuseFuseResoCrystal(GameTestHelper helper) {
        fuse(helper, "fuse_reso_crystal", "minecraft:diamond", 1, "academy:reso_crystal");
    }

    private static void fuse(GameTestHelper helper, String recipePath, String inputId, int inputCount, String outputId) {
        String recipeId = "academy:extra_" + recipePath;
        check(helper, ExtraRecipeManifest.IMAG_FUSOR.contains(recipeId), "unlisted fusion fixture " + recipeId);
        var machine = placeFusor(helper, 2);
        int supplied = inputCount == 2 ? 3 : 1;
        machine.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(item(inputId), supplied));
        machine.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT,
                new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 2));
        machine.injectEnergy(2000);
        var found = helper.getLevel().getRecipeManager().getRecipeFor(AcademyRecipeTypes.IMAG_FUSING.get(),
                new ImagFusorRecipeInput(machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT)),
                helper.getLevel()).orElseThrow();
        check(helper, found.id().equals(ResourceLocation.parse(recipeId)), recipeId + " resolved to " + found.id());
        check(helper, found.value().inputCount() == inputCount && found.value().phaseLiquid() == 1000,
                recipeId + " ingredient/liquid quantities differ from the legacy fixture");
        helper.runAfterDelay(20, () -> {
            check(helper, machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).getCount() == supplied,
                    recipeId + " consumed input before completion");
            check(helper, machine.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty(),
                    recipeId + " completed before productive duration");
            check(helper, machine.getFluidAmount() == 2000, recipeId + " consumed liquid before completion");
        });
        helper.runAfterDelay(170, () -> {
            assertFusionResult(helper, machine, recipeId, outputId, supplied - inputCount, 1000);
            LOGGER.info("EXTRA_RECIPE_EXECUTION_PASS {} scheduled-fusor-ticks, input-debit={}, liquid-debit=1000, energy-debit=1440",
                    recipeId, inputCount);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void metalEtchedCobblestoneRejectsWrongModeAndConsumesOne(GameTestHelper helper) {
        String recipeId = ExtraRecipeManifest.METAL_FORMING.getFirst();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.METAL_FORMER.get().defaultBlockState(), 3);
        var machine = (MetalFomerBlockEntity) helper.getLevel().getBlockEntity(pos);
        check(helper, machine != null, recipeId + " machine did not appear");
        machine.getItems().set(MetalFomerBlockEntity.SLOT_IN, new ItemStack(Items.COBBLESTONE));
        machine.injectEnergy(MetalFomerBlockEntity.MAX_ENERGY);
        helper.runAfterDelay(80, () -> {
            check(helper, machine.getItems().get(MetalFomerBlockEntity.SLOT_IN).getCount() == 1
                    && machine.getItems().get(MetalFomerBlockEntity.SLOT_OUT).isEmpty()
                    && machine.getEnergy() == MetalFomerBlockEntity.MAX_ENERGY,
                    recipeId + " consumed resources in the wrong mode");
            while (machine.getMode() != MetalFormerRecipes.Mode.ETCH) machine.cycleMode(1);
        });
        helper.runAfterDelay(200, () -> {
            assertOutput(helper, machine.getItems().get(MetalFomerBlockEntity.SLOT_OUT),
                    "academy:etched_cobblestone", 1, recipeId);
            check(helper, machine.getItems().get(MetalFomerBlockEntity.SLOT_IN).isEmpty(), recipeId + " failed to debit input");
            double expected = MetalFomerBlockEntity.MAX_ENERGY -
                    MetalFomerBlockEntity.WORK_TICKS * MetalFomerBlockEntity.CONSUME_PER_TICK;
            check(helper, Math.abs(machine.getEnergy() - expected) < 1e-6, recipeId + " incorrect energy debit");
            LOGGER.info("EXTRA_RECIPE_EXECUTION_PASS {} scheduled-metal-former-ticks, wrong-mode rejection, exact input and energy debit", recipeId);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fusionRejectsOneRedstoneLowFluidNoEnergyAndBlockedOutputThenRecovers(GameTestHelper helper) {
        var redstone = placeFusor(helper, 1);
        var fluid = placeFusor(helper, 3);
        var energy = placeFusor(helper, 5);
        var blocked = placeFusor(helper, 7);
        redstone.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.REDSTONE));
        fluid.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT));
        energy.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        blocked.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.DIAMOND));
        blocked.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.DIRT));
        for (var machine : List.of(redstone, energy, blocked)) {
            machine.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT,
                    new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 2));
        }
        for (var machine : List.of(redstone, fluid, blocked)) machine.injectEnergy(2000);
        fluid.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 999), IFluidHandler.FluidAction.EXECUTE);

        helper.runAfterDelay(160, () -> {
            for (var machine : List.of(redstone, fluid, energy, blocked)) {
                check(helper, machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).getCount() == 1,
                        "blocked fusion consumed input at " + machine.getBlockPos());
                check(helper, machine.getProcessingTime() == 0, "blocked fusion accumulated progress");
            }
            check(helper, redstone.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                    && redstone.getEnergy() == 2000 && redstone.getFluidAmount() == 2000,
                    "one redstone produced output or consumed energy/liquid");
            check(helper, fluid.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                    && fluid.getEnergy() == 2000 && fluid.getFluidAmount() == 999,
                    "insufficient fluid consumed resources");
            check(helper, energy.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                    && energy.getEnergy() == 0 && energy.getFluidAmount() == 2000,
                    "unpowered fusion consumed resources");
            check(helper, blocked.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).is(Items.DIRT)
                    && blocked.getEnergy() == 2000 && blocked.getFluidAmount() == 2000,
                    "blocked output consumed or overwrote resources");
            redstone.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.REDSTONE, 3));
            fluid.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 1), IFluidHandler.FluidAction.EXECUTE);
            energy.injectEnergy(2000);
            blocked.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        });
        helper.runAfterDelay(340, () -> {
            assertFusionResult(helper, redstone, "one-redstone-recovery", "academy:crystal_low", 1, 1000);
            assertFusionResult(helper, fluid, "low-fluid-recovery", "academy:constraint_ingot", 0, 0);
            assertFusionResult(helper, energy, "no-energy-recovery", "academy:cp_potion", 0, 1000);
            assertFusionResult(helper, blocked, "blocked-output-recovery", "academy:reso_crystal", 0, 1000);
            LOGGER.info("EXTRA_RECIPE_BOUNDARY_PASS one-redstone, 999mB, zero-energy, blocked-output; no premature debit and recovery");
            helper.succeed();
        });
    }

    private static ImagFusorBlockEntity placeFusor(GameTestHelper helper, int x) {
        var pos = helper.absolutePos(new BlockPos(x, 2, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        var machine = (ImagFusorBlockEntity) helper.getLevel().getBlockEntity(pos);
        check(helper, machine != null, "Imag Fusor block entity was not created");
        return machine;
    }

    private static void assertFusionResult(GameTestHelper helper, ImagFusorBlockEntity machine,
                                           String label, String outputId, int inputLeft, int fluidLeft) {
        assertOutput(helper, machine.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT), outputId, 1, label);
        check(helper, machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).getCount() == inputLeft, label + " wrong input debit");
        check(helper, machine.getFluidAmount() == fluidLeft, label + " wrong liquid debit");
        check(helper, Math.abs(machine.getEnergy() - 560) < 1e-6, label + " wrong energy debit");
        check(helper, machine.getItems().get(ImagFusorBlockEntity.FLUID_INPUT_SLOT).isEmpty(), label + " fluid unit not processed");
        if (fluidLeft != 0) {
            assertOutput(helper, machine.getItems().get(ImagFusorBlockEntity.EMPTY_UNIT_SLOT),
                    "academy:matter_unit_none", 2, label + " empty containers");
        }
    }

    private static Item item(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Unknown fixture item " + id);
        return BuiltInRegistries.ITEM.get(key);
    }

    private static void assertOutput(GameTestHelper helper, ItemStack output, String id, int count, String label) {
        check(helper, output.is(item(id)) && output.getCount() == count,
                label + " output expected " + id + " x" + count + ", got " + output);
    }

    private static void assertEmptyEnergy(GameTestHelper helper, ItemStack output, String label) {
        if (EnergyItemHelper.isEnergyItem(output)) check(helper, EnergyItemHelper.getEnergy(output) == 0,
                label + " created a charged output from crafting");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}