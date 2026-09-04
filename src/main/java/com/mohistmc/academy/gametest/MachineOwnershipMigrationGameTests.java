package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral coverage for the administrator-only owner migration command. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MachineOwnershipMigrationGameTests {
    private MachineOwnershipMigrationGameTests() {}

    @GameTest(template = "empty")
    public static void adminCanMigrateOwnerlessLoadedMachinesWithoutOverwritingOwners(GameTestHelper helper) {
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos matrixPos = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos interfererPos = helper.absolutePos(new BlockPos(6, 1, 2));
        BlockPos windPos = helper.absolutePos(new BlockPos(8, 1, 2));
        BlockPos existingPos = helper.absolutePos(new BlockPos(10, 1, 2));
        var level = helper.getLevel();
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(matrixPos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        level.setBlock(interfererPos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
        level.setBlock(windPos, AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState(), 3);
        level.setBlock(existingPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);

        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
        MatrixBlockEntity matrix = (MatrixBlockEntity) level.getBlockEntity(matrixPos);
        AbilityInterfererBlockEntity interferer =
                (AbilityInterfererBlockEntity) level.getBlockEntity(interfererPos);
        WindGenMainBlockEntity wind = (WindGenMainBlockEntity) level.getBlockEntity(windPos);
        BaseNodeBlockEntity existing = (BaseNodeBlockEntity) level.getBlockEntity(existingPos);
        if (node == null || matrix == null || interferer == null || wind == null || existing == null) {
            helper.fail("ownership migration fixture did not create every protected block entity");
            return;
        }
        UUID existingOwner = UUID.randomUUID();
        existing.setOwnerUUID(existingOwner);

        var player = helper.makeMockServerPlayerInLevel();
        var commands = level.getServer().getCommands();
        var ordinary = player.createCommandSourceStack().withPermission(0)
                .withPosition(Vec3.atCenterOf(nodePos));
        commands.performPrefixedCommand(ordinary,
                "acmigrate ownership claim " + coordinates(nodePos));
        if (node.getOwnerUUID() != null) {
            helper.fail("ordinary player executed the administrator migration command");
            return;
        }

        var admin = player.createCommandSourceStack().withPermission(4)
                .withPosition(Vec3.atCenterOf(nodePos));
        commands.performPrefixedCommand(admin, "acmigrate ownership scan 12");
        commands.performPrefixedCommand(admin,
                "acmigrate ownership claim " + coordinates(nodePos));
        commands.performPrefixedCommand(admin, "acmigrate ownership claim_nearby 12");

        UUID migratedOwner = player.getUUID();
        if (!migratedOwner.equals(node.getOwnerUUID())
                || !migratedOwner.equals(matrix.getOwnerUUID())
                || !migratedOwner.equals(interferer.getOwner())
                || !migratedOwner.equals(wind.getOwnerUUID())
                || !existingOwner.equals(existing.getOwnerUUID())) {
            helper.fail("migration did not assign all ownerless machines or overwrote a live owner");
            return;
        }
        helper.succeed();
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
