package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.ShiftTpEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-authoritative regressions sourced from the final AcademyCraft 1.12.2 code. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TeleporterParityGameTests {
    private static final String EMPTY = "empty";

    private TeleporterParityGameTests() {}

    @GameTest(template = EMPTY)
    public static void shiftTeleportDropsOneRemoteBlockWhenPlacementIsInvalid(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos playerPos = helper.absolutePos(new BlockPos(4, 2, 3));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 3, 7));
        // The ray hits this wall. The adjacent target has ordinary stone below,
        // so a cactus BlockItem reaches useOn but fails its survival rule.
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(wall.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(wall.relative(net.minecraft.core.Direction.NORTH).below(),
                Blocks.STONE.defaultBlockState(), 3);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Blocks.CACTUS, 2));

        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.TELEPORTER);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.setDevMode(true);
        data.learnSkill("shift_tp");
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);

        if (!new ShiftTpEffect().tryRelease(player, data, 1)) {
            helper.fail("final 1.12.2 Shift Teleport rejected its remote-drop branch");
            return;
        }
        int remoteDrops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(wall).inflate(4), item -> item.getItem().is(Blocks.CACTUS.asItem()))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
        if (remoteDrops != 1 || player.getMainHandItem().getCount() != 1
                || !level.getBlockState(wall).is(Blocks.STONE)) {
            helper.fail("Shift Teleport did not preserve the 1.12.2 drop/one-item-consumption outcome");
            return;
        }
        helper.succeed();
    }
}
