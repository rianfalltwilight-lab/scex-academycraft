package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.WindGenBase;
import com.mohistmc.academy.world.block.WindGenFan;
import com.mohistmc.academy.world.block.WindGenMain;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import com.mohistmc.academy.world.item.EnergyUnit;
import com.mohistmc.academy.world.menu.WindGenBaseMenu;
import com.mohistmc.academy.world.menu.WindGenMainMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real player-flow regressions for the 1.12.2 wind generator. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class WindGeneratorGameTests {
    private static final String EMPTY = "empty";

    private WindGeneratorGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void playerPlacedCrossChunkTurbineShiftClicksOneFanGeneratesAndCharges(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;

        // Put the centre at the positive edge of a real chunk along the
        // north/south head axis.  The placement transaction must still create
        // and retain both horizontal proxies.
        BlockPos templateOrigin = helper.absolutePos(BlockPos.ZERO);
        // Keep the cross-chunk fixture inside the 64x64 empty template.  With
        // no margin, an origin whose Z is already 15 modulo 16 selects
        // relative Z=0 and the north proxy lands at relative Z=-1.  GameTest
        // encloses the template with an unreplaceable barrier wall, so the
        // transactional placement correctly rejects that out-of-bounds proxy.
        int relativeX = 16 + Math.floorMod(15 - Math.floorMod(templateOrigin.getX(), 16), 16);
        int relativeZ = 16 + Math.floorMod(15 - Math.floorMod(templateOrigin.getZ(), 16), 16);
        BlockPos basePos = helper.absolutePos(new BlockPos(relativeX, 1, relativeZ));
        level.setBlock(basePos.below(), Blocks.STONE.defaultBlockState(), 3);

        if (!placeOnTop(player, new ItemStack(AcademyItems.WINDGEN_BASE.get()), basePos.below())
                || !level.getBlockState(basePos).is(AcademyBlocks.WINDGEN_BASE.get())
                || !level.getBlockState(basePos.above()).is(AcademyBlocks.WIND_GEN_BASE_SUB.get())) {
            helper.fail("player placement did not create the two-block wind base"); return;
        }

        for (int i = 0; i < WindGenBase.MIN_PILLARS; i++) {
            BlockPos support = basePos.above(1 + i);
            if (!placeOnTop(player, new ItemStack(AcademyItems.WINDGEN_PILLAR.get()), support)
                    || !level.getBlockState(support.above()).is(AcademyBlocks.WINDGEN_PILLAR.get())) {
                helper.fail("player placement failed at wind pillar " + i); return;
            }
        }

        BlockPos mainPos = basePos.above(2 + WindGenBase.MIN_PILLARS);
        player.setYRot(0.0f);
        if (!placeOnTop(player, new ItemStack(AcademyItems.WINDGEN_MAIN.get()), mainPos.below())
                || !level.getBlockState(mainPos).is(AcademyBlocks.WINDGEN_MAIN.get())) {
            helper.fail("player placement did not create the wind head"); return;
        }
        var mainState = level.getBlockState(mainPos);
        var proxies = WindGenMain.proxyPositions(mainPos, mainState);
        if (proxies.stream().anyMatch(pos -> !level.getBlockState(pos).is(AcademyBlocks.WINDGEN_FAN.get())
                || level.getBlockState(pos).getValue(WindGenFan.FACING) != mainState.getValue(WindGenMain.FACING))
                || proxies.stream().map(pos -> level.getChunkAt(pos).getPos()).distinct().count() < 2) {
            helper.fail("cross-chunk wind head proxies were incomplete or had the wrong facing"); return;
        }
        // The reusable empty GameTest structure is shorter than the official
        // 15x15 rotor plane.  Remove only fixture-boundary blocks in that
        // plane so this remains a real clear-sky turbine test.
        clearLegacy112RotorPlane(level, mainPos, mainState.getValue(WindGenMain.FACING));

        WindGenBaseBlockEntity base = (WindGenBaseBlockEntity) level.getBlockEntity(basePos);
        WindGenMainBlockEntity main = (WindGenMainBlockEntity) level.getBlockEntity(mainPos);
        if (base == null || main == null) {
            helper.fail("placed wind structure lost its block entities"); return;
        }

        // Jade and automation may target either visible half of the old
        // two-block base.  Both queries must resolve to the same storage.
        var lowerEnergy = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                basePos, Direction.UP);
        var upperEnergy = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                basePos.above(), Direction.UP);
        if (lowerEnergy == null || upperEnergy == null
                || lowerEnergy.receiveEnergy(40, false) != 40
                || upperEnergy.getEnergyStored() != 40) {
            helper.fail("wind base proxy did not expose the main block's authoritative energy"); return;
        }
        base.setEnergy(0);

        // Exercise the same player-first menu ranges used by a real shift-click.
        player.setPos(basePos.getX() + 0.5, basePos.getY() + 0.5, basePos.getZ() + 1.5);
        ItemStack battery = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        battery.setDamageValue(EnergyUnit.MAX_ENERGY);
        player.getInventory().setItem(9, battery);
        WindGenBaseMenu baseMenu = new WindGenBaseMenu(1, player.getInventory(), menuPos(basePos));
        if (baseMenu.quickMoveStack(player, 0).isEmpty()
                || !baseMenu.getSlot(36).getItem().is(AcademyItems.ENERGY_UNIT.get())) {
            helper.fail("wind base shift-click did not insert its energy item"); return;
        }

        player.setPos(mainPos.getX() + 0.5, mainPos.getY() + 0.5, mainPos.getZ() + 1.5);
        // A malformed old-world stack is intentional: the menu must move one
        // fan only and leave the overflow in the player inventory.
        player.getInventory().setItem(9, new ItemStack(AcademyItems.WINDGEN_FAN.get(), 3));
        WindGenMainMenu mainMenu = new WindGenMainMenu(2, player.getInventory(), menuPos(mainPos));
        if (mainMenu.quickMoveStack(player, 0).isEmpty()
                || !mainMenu.getSlot(36).getItem().is(AcademyItems.WINDGEN_FAN.get())
                || mainMenu.getSlot(36).getItem().getCount() != 1
                || player.getInventory().getItem(9).getCount() != 2) {
            helper.fail("wind head shift-click did not enforce the one-fan legacy slot"); return;
        }

        helper.runAfterDelay(12, () -> {
            ItemStack installedBattery = base.getItems().getFirst();
            if (!main.isStructureComplete() || !main.isFanInstalled() || !main.isWorking()
                    || !level.getBlockState(basePos).getValue(WindGenBase.ENABLE)
                    || EnergyItemHelper.getEnergy(installedBattery) <= 0) {
                helper.fail("player-built turbine did not generate and charge after fan insertion"); return;
            }

            Direction facing = level.getBlockState(mainPos).getValue(WindGenMain.FACING);
            BlockPos obstruction = WindGenMainBlockEntity.fanPosition(mainPos, facing).above();
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            helper.runAfterDelay(12, () -> {
                if (main.isWorking() || main.isVisualFanVisible() || base.isWorking()) {
                    helper.fail("15x15 rotor obstruction did not stop generation and rendering"); return;
                }
                int chargedWhileStopped = EnergyItemHelper.getEnergy(installedBattery);
                helper.runAfterDelay(3, () -> {
                    if (EnergyItemHelper.getEnergy(installedBattery) != chargedWhileStopped) {
                        helper.fail("obstructed turbine continued charging after its refresh interval"); return;
                    }
                    level.destroyBlock(obstruction, false);
                    helper.runAfterDelay(12, () -> {
                        if (!main.isWorking() || !main.isVisualFanVisible() || !base.isWorking()
                                || EnergyItemHelper.getEnergy(installedBattery) <= chargedWhileStopped) {
                            helper.fail("turbine did not resume after its 15x15 rotor plane was cleared"); return;
                        }
                        int chargedBeforeBreak = EnergyItemHelper.getEnergy(installedBattery);
                        level.destroyBlock(basePos.above(4), false);
                        helper.runAfterDelay(3, () -> {
                            if (base.isWorking()
                                    || EnergyItemHelper.getEnergy(installedBattery) != chargedBeforeBreak) {
                                helper.fail("broken pillar did not stop wind generation on the next server tick"); return;
                            }
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    private static FriendlyByteBuf menuPos(BlockPos pos) {
        return new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
    }

    private static void clearLegacy112RotorPlane(net.minecraft.server.level.ServerLevel level,
                                                  BlockPos mainPos,
                                                  Direction facing) {
        BlockPos center = WindGenMainBlockEntity.fanPosition(mainPos, facing);
        for (int vertical = -7; vertical <= 7; vertical++) {
            for (int lateral = -7; lateral <= 7; lateral++) {
                if (vertical == 0 && lateral == 0) continue;
                BlockPos target = facing.getAxis() == Direction.Axis.X
                        ? center.offset(0, vertical, lateral)
                        : center.offset(lateral, vertical, 0);
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static boolean placeOnTop(ServerPlayer player, ItemStack held, BlockPos support) {
        player.setPos(support.getX() + 0.5, support.getY() + 1.5, support.getZ() + 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support),
                net.minecraft.core.Direction.UP, support, false);
        InteractionResult result = held.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return result.consumesAction();
    }
}
