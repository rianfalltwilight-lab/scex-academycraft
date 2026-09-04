package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PhaseGeneratorVisualGameTests {
    private PhaseGeneratorVisualGameTests() {}

    @GameTest(template = "empty")
    public static void phaseGeneratorExposesLegacyFluidHandlerToAutomation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        helper.getLevel().setBlock(pos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);

        IFluidHandler external = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        if (external == null) {
            helper.fail("Phase generator did not expose its tank through the NeoForge block capability");
            return;
        }
        int rejected = external.fill(new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        int accepted = external.fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = external.drain(250, IFluidHandler.FluidAction.EXECUTE);
        if (rejected != 0 || accepted != 1_000 || drained.getFluid() != AcademyFluids.PHASE_LIQUID.get()
                || drained.getAmount() != 250 || external.getFluidInTank(0).getAmount() != 750) {
            helper.fail("Phase generator external fill/drain contract differs from the legacy IFluidHandler");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void imagFusorExposesLegacySidedInventoryToAutomation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        helper.getLevel().setBlock(pos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof ImagFusorBlockEntity fusor)) {
            helper.fail("Imag fusor fixture did not create a block entity");
            return;
        }

        IItemHandler top = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        IItemHandler bottom = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        IItemHandler side = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.NORTH);
        if (top == null || bottom == null || side == null
                || top.getSlots() != 2 || bottom.getSlots() != 3 || side.getSlots() != 1) {
            helper.fail("Imag fusor sided item capability does not expose the legacy 2/3/1 slot layout");
            return;
        }

        ItemStack crystalRemainder = top.insertItem(
                0, new ItemStack(AcademyItems.CRYSTAL_LOW.get()), false);
        ItemStack phaseUnitRemainder = top.insertItem(
                1, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()), false);
        if (!crystalRemainder.isEmpty() || !phaseUnitRemainder.isEmpty()
                || !fusor.getItems().get(ImagFusorBlockEntity.INPUT_SLOT)
                        .is(AcademyItems.CRYSTAL_LOW.get())
                || !fusor.getItems().get(ImagFusorBlockEntity.FLUID_INPUT_SLOT)
                        .is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())) {
            helper.fail("Top automation could not insert the legacy recipe and phase-unit inputs");
            return;
        }

        fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT,
                new ItemStack(AcademyItems.CRYSTAL_NORMAL.get(), 2));
        ItemStack extracted = bottom.extractItem(0, 1, false);
        if (!extracted.is(AcademyItems.CRYSTAL_NORMAL.get()) || extracted.getCount() != 1
                || fusor.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).getCount() != 1) {
            helper.fail("Bottom automation could not extract the legacy product slot");
            return;
        }
        if (side.insertItem(0,
                new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()), true).isEmpty()) {
            helper.fail("Side automation incorrectly accepted a non-energy item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tankFillSelectsTheLegacyWorldModelFrame(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        helper.getLevel().setBlock(pos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof PhaseGenBlockEntity phase)) {
            helper.fail("Phase generator fixture did not create a block entity");
            return;
        }
        int accepted = phase.getFluidTank().fill(
                new FluidStack(AcademyFluids.PHASE_LIQUID.get(), phase.getTankSize()),
                IFluidHandler.FluidAction.EXECUTE);
        if (accepted != phase.getTankSize()) {
            helper.fail("Phase generator tank rejected its own phase liquid");
            return;
        }
        helper.runAfterDelay(2, () -> {
            var property = helper.getLevel().getBlockState(pos).getBlock()
                    .getStateDefinition().getProperty("working");
            if (!(property instanceof IntegerProperty working)) {
                helper.fail("Phase generator working model property is missing");
                return;
            }
            int frame = helper.getLevel().getBlockState(pos).getValue(working);
            if (frame != 4) {
                helper.fail("Full phase tank selected model frame " + frame + " instead of legacy frame 4");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void imagFusorLeavesIdleFrameAndAnimatesWhileWorking(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        helper.getLevel().setBlock(pos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof ImagFusorBlockEntity fusor)) {
            helper.fail("Imag fusor fixture did not create a block entity");
            return;
        }
        var property = helper.getLevel().getBlockState(pos).getBlock()
                .getStateDefinition().getProperty("working");
        if (!(property instanceof IntegerProperty working)
                || helper.getLevel().getBlockState(pos).getValue(working) != 0) {
            helper.fail("Imag fusor does not start on the legacy idle frame");
            return;
        }
        fusor.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 8_000),
                IFluidHandler.FluidAction.EXECUTE);
        fusor.injectEnergy(2_000);
        fusor.getItems().set(ImagFusorBlockEntity.INPUT_SLOT,
                new ItemStack(AcademyItems.CRYSTAL_LOW.get()));
        fusor.setChanged();
        helper.runAfterDelay(2, () -> {
            int frame = helper.getLevel().getBlockState(pos).getValue(working);
            if (!fusor.isWorking() || frame < 1 || frame > 4) {
                helper.fail("Working imag fusor stayed on idle model frame " + frame);
                return;
            }
            helper.succeed();
        });
    }
}
