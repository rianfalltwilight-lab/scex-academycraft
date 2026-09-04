package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.AbilityInterferer;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server behavioral checks; client layout still requires the real client gate. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AbilityInterfererGameTests {
    private AbilityInterfererGameTests() {}

    @GameTest(template = "empty")
    public static void paidPulseSuppressesOnlyNonWhitelistedSurvivalPlayers(GameTestHelper helper) {
        AbilityInterferenceService.clearAll();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(pos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof AbilityInterfererBlockEntity machine)
                || !(machine instanceof IWirelessReceiver)) {
            helper.fail("ability interferer block entity/receiver was not registered");
            return;
        }
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        // GameTestHelper's mock factory reuses a fixed profile on this runtime.
        // A second call therefore cannot represent a distinct whitelist identity.
        ServerPlayer target = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "[InterfereTarget]"));
        owner.setGameMode(GameType.SURVIVAL);
        target.setGameMode(GameType.SURVIVAL);
        owner.setPos(pos.getX() + 1.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        target.setPos(pos.getX() + 2.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        machine.assignOwnerOnPlacement(owner);
        machine.setEnergy(1_000);
        machine.setEnabled(true);
        machine.serverTick();

        if (machine.getEnergyStored() != 900 || !machine.isPulseActive(helper.getLevel().getGameTime())) {
            helper.fail("range-10 pulse did not debit exactly 100 IF");
            return;
        }
        if (AbilityInterferenceService.isInterfered(owner)
                || !AbilityInterferenceService.isInterfered(target)) {
            helper.fail("owner exemption or survival suppression failed");
            return;
        }
        if (!machine.addWhitelist(target.getGameProfile())
                || AbilityInterferenceService.isInterfered(target)) {
            helper.fail("bounded whitelist did not immediately exempt target");
            return;
        }
        if (!machine.removeWhitelist(target.getUUID())) {
            helper.fail("whitelist removal failed");
            return;
        }
        target.setGameMode(GameType.CREATIVE);
        if (AbilityInterferenceService.isInterfered(target)) {
            helper.fail("creative player was interfered");
            return;
        }
        target.setGameMode(GameType.SURVIVAL);
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (AbilityInterferenceService.isInterfered(target)) {
            helper.fail("removing the machine left a phantom interference source");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void insufficientEnergyAutoDisablesAndUpdatesModel(GameTestHelper helper) {
        AbilityInterferenceService.clearAll();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlock(pos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
        AbilityInterfererBlockEntity machine =
                (AbilityInterfererBlockEntity) helper.getLevel().getBlockEntity(pos);
        machine.setEnergy(100);
        machine.setEnabled(true);
        machine.serverTick();
        if (machine.getEnergyStored() != 0 || !machine.isEnabled()
                || helper.getLevel().getBlockState(pos).getValue(AbilityInterferer.STATUS) != 1) {
            helper.fail("first prepaid interval/model state was incorrect");
            return;
        }
        helper.runAfterDelay(12, () -> {
            if (machine.isEnabled()
                    || helper.getLevel().getBlockState(pos).getValue(AbilityInterferer.STATUS) != 0) {
                helper.fail("energy-starved interferer did not auto-disable");
            } else {
                helper.succeed();
            }
        });
    }
}
