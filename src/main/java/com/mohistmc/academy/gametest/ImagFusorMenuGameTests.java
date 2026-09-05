package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Exercises player insertion through real menu clicks, independently of automation/recipe assembly. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ImagFusorMenuGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ImagFusorMenuGameTests() {}

    @GameTest(template = "empty")
    public static void fusionIngredientsEnterByClickAndShift(GameTestHelper helper) {
        Fixture f = fixture(helper);
        List<ItemStack> inputs = List.of(new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.SAND), new ItemStack(Items.DIAMOND),
                new ItemStack(AcademyItems.CRYSTAL_LOW.get()), new ItemStack(AcademyItems.CRYSTAL_NORMAL.get()));
        var failures = new ArrayList<String>();
        try {
            for (ItemStack input : inputs) {
                String id = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
                for (boolean shift : new boolean[]{false, true}) {
                    f.menu.container.clearContent();
                    f.player.getInventory().clearContent();
                    f.menu.setCarried(ItemStack.EMPTY);
                    if (shift) {
                        f.player.getInventory().setItem(9, input.copy());
                        f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
                    } else {
                        f.menu.setCarried(input.copy());
                        f.menu.clicked(slot(f, ImagFusorBlockEntity.INPUT_SLOT), 0, ClickType.PICKUP, f.player);
                    }
                    ItemStack actual = f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT);
                    boolean accepted = ItemStack.isSameItemSameComponents(actual, input)
                            && actual.getCount() == input.getCount();
                    LOGGER.info("FUSOR_MENU_OBSERVATION {} path={} supplied={} inserted={}", id,
                            shift ? "shift-click" : "cursor-click", input.getCount(), actual.getCount());
                    if (!accepted) failures.add(id + " rejected " + (shift ? "shift-click" : "cursor-click"));
                    if (accepted) check(helper, f.menu.getCarried().isEmpty()
                            && f.player.getInventory().getItem(9).isEmpty(), id + " insertion duplicated the source");
                }
            }
        } finally { close(f); }
        check(helper, failures.isEmpty(), "Recipe ingredients cannot enter fusion menu: " + failures);
        LOGGER.info("FUSOR_MENU_INSERTION_PASS five Extra and two base inputs, cursor+shift, exact source debit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fusionMenuRejectsInvalidAndOutputInsertion(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            ItemStack dirt = new ItemStack(Items.DIRT, 2);
            f.menu.setCarried(dirt.copy());
            f.menu.clicked(slot(f, ImagFusorBlockEntity.INPUT_SLOT), 0, ClickType.PICKUP, f.player);
            check(helper, f.menu.getCarried().getCount() == 2
                    && f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).isEmpty(), "recipe-less cursor item accepted");
            f.menu.setCarried(ItemStack.EMPTY);
            f.player.getInventory().setItem(9, dirt.copy());
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            check(helper, f.player.getInventory().getItem(9).getCount() == 2
                    && f.machine.getItems().stream().allMatch(ItemStack::isEmpty), "recipe-less shift item accepted or lost");
            f.player.getInventory().clearContent();
            for (int machineSlot : new int[]{ImagFusorBlockEntity.OUTPUT_SLOT, ImagFusorBlockEntity.EMPTY_UNIT_SLOT}) {
                f.menu.setCarried(new ItemStack(Items.REDSTONE, 2));
                f.menu.clicked(slot(f, machineSlot), 0, ClickType.PICKUP, f.player);
                check(helper, f.menu.getCarried().getCount() == 2 && f.machine.getItems().get(machineSlot).isEmpty(),
                        "output slot " + machineSlot + " accepted cursor input");
            }
        } finally { close(f); }
        LOGGER.info("FUSOR_MENU_REJECTION_PASS no-recipe cursor+shift inputs and both output slots rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void singleRedstoneWaitsForSecondMenuInput(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.menu.setCarried(new ItemStack(Items.REDSTONE));
        f.menu.clicked(slot(f, ImagFusorBlockEntity.INPUT_SLOT), 0, ClickType.PICKUP, f.player);
        check(helper, f.menu.getCarried().isEmpty() && f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).is(Items.REDSTONE),
                "one valid redstone must enter the menu before its recipe count is complete");
        f.menu.setCarried(new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()));
        f.menu.clicked(slot(f, ImagFusorBlockEntity.FLUID_INPUT_SLOT), 0, ClickType.PICKUP, f.player);
        check(helper, f.menu.getCarried().isEmpty(), "phase liquid unit did not enter through menu");
        f.machine.injectEnergy(2000);
        helper.runAfterDelay(20, () -> {
            check(helper, f.machine.getProcessingTime() == 0 && f.machine.getEnergy() == 2000
                    && f.machine.getFluidAmount() == 1000 && f.machine.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                    && f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).getCount() == 1,
                    "single redstone started processing or debited resources");
            f.menu.setCarried(new ItemStack(Items.REDSTONE));
            f.menu.clicked(slot(f, ImagFusorBlockEntity.INPUT_SLOT), 0, ClickType.PICKUP, f.player);
            check(helper, f.menu.getCarried().isEmpty()
                    && f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).getCount() == 2,
                    "second cursor redstone did not merge into input");
        });
        helper.runAfterDelay(190, () -> {
            try {
                ItemStack output = f.machine.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT);
                check(helper, output.is(AcademyItems.CRYSTAL_LOW.get()) && output.getCount() == 1
                        && f.machine.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).isEmpty()
                        && f.machine.getFluidAmount() == 0 && f.machine.getEnergy() == 560,
                        "second redstone did not unlock one real fusion with exact input/liquid/energy debit");
                f.menu.clicked(slot(f, ImagFusorBlockEntity.OUTPUT_SLOT), 0, ClickType.PICKUP, f.player);
                check(helper, f.menu.getCarried().is(AcademyItems.CRYSTAL_LOW.get())
                        && f.menu.getCarried().getCount() == 1 && f.machine.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty(),
                        "completed crystal could not be extracted once through menu");
                LOGGER.info("FUSOR_MENU_COUNT_PASS one redstone waits, second merges, scheduled fusion and real output extraction");
                helper.succeed();
            } finally { close(f); }
        });
    }

    private record Fixture(ServerPlayer player, ImagFusorBlockEntity machine, ImagFusorMenu menu) {}

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        var machine = (ImagFusorBlockEntity) level.getBlockEntity(pos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[FusorMenu]"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
        ImagFusorMenu menu;
        try { menu = new ImagFusorMenu(71, player.getInventory(), data); }
        finally { data.release(); }
        player.containerMenu = menu;
        check(helper, menu.stillValid(player), "fusion menu fixture is invalid");
        return new Fixture(player, machine, menu);
    }

    private static int slot(Fixture f, int machineSlot) {
        for (int i = 0; i < f.menu.slots.size(); i++) {
            Slot slot = f.menu.slots.get(i);
            if (slot.container == f.menu.container && slot.getSlotIndex() == machineSlot) return i;
        }
        throw new IllegalArgumentException("Missing fusion slot " + machineSlot);
    }

    private static void close(Fixture f) {
        f.menu.setCarried(ItemStack.EMPTY);
        f.menu.removed(f.player);
        f.player.containerMenu = f.player.inventoryMenu;
        f.player.getInventory().clearContent();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
