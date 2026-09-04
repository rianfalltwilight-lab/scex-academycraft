package com.mohistmc.academy.command;

import com.mohistmc.academy.world.block.entity.MachineOwnership;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Explicit migration tooling for ownerless blocks created by older rebuilds,
 * commands, structure files, or damaged saves. No command force-loads chunks
 * and no operation overwrites a non-null owner.
 */
public final class MachineOwnershipMigrationCommands {
    private static final int REQUIRED_PERMISSION = 2;
    private static final int DEFAULT_RADIUS = 16;
    private static final int MAX_RADIUS = 128;

    private MachineOwnershipMigrationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var scan = Commands.literal("scan")
                .executes(context -> scan(context.getSource(), DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> scan(context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius"))));

        var claim = Commands.literal("claim")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> claimOne(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> claimOne(context,
                                        EntityArgument.getPlayer(context, "player")))));

        var claimNearby = Commands.literal("claim_nearby")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> claimNearby(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> claimNearby(context,
                                        EntityArgument.getPlayer(context, "player")))));

        dispatcher.register(Commands.literal("acmigrate")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> usage(context.getSource()))
                .then(Commands.literal("ownership")
                        .executes(context -> usage(context.getSource()))
                        .then(scan)
                        .then(claim)
                        .then(claimNearby)));
    }

    private static int usage(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.academy.migrate.usage"));
        return 0;
    }

    private static int scan(CommandSourceStack source, int radius) {
        BlockPos center = BlockPos.containing(source.getPosition());
        List<BlockEntity> machines = loadedProtectedMachines(source.getLevel(), center, radius);
        long ownerless = machines.stream().filter(machine -> MachineOwnership.ownerOf(machine) == null).count();
        source.sendSuccess(() -> Component.translatable("commands.academy.migrate.scan",
                radius, machines.size(), ownerless), false);
        return (int) ownerless;
    }

    private static int claimOne(CommandContext<CommandSourceStack> context, ServerPlayer owner)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockEntity machine = context.getSource().getLevel().getBlockEntity(pos);
        MachineOwnership.MigrationResult result = MachineOwnership.migrateLegacyOwner(machine, owner);
        return reportClaim(context.getSource(), result, pos, owner);
    }

    private static int claimNearby(CommandContext<CommandSourceStack> context, ServerPlayer owner) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        List<BlockEntity> machines = loadedProtectedMachines(context.getSource().getLevel(), center, radius);
        int claimed = 0;
        int alreadyOwned = 0;
        for (BlockEntity machine : machines) {
            MachineOwnership.MigrationResult result = MachineOwnership.migrateLegacyOwner(machine, owner);
            if (result == MachineOwnership.MigrationResult.CLAIMED) claimed++;
            else if (result == MachineOwnership.MigrationResult.ALREADY_OWNED) alreadyOwned++;
        }
        int claimedCount = claimed;
        int ownedCount = alreadyOwned;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.academy.migrate.claim_nearby", claimedCount, owner.getDisplayName(),
                ownedCount, radius), true);
        return claimed;
    }

    private static int reportClaim(CommandSourceStack source,
                                   MachineOwnership.MigrationResult result,
                                   BlockPos pos, ServerPlayer owner) {
        if (result == MachineOwnership.MigrationResult.CLAIMED) {
            source.sendSuccess(() -> Component.translatable("commands.academy.migrate.claimed",
                    pos.toShortString(), owner.getDisplayName()), true);
            return 1;
        }
        if (result == MachineOwnership.MigrationResult.ALREADY_OWNED) {
            source.sendFailure(Component.translatable("commands.academy.migrate.already_owned",
                    pos.toShortString()));
        } else {
            source.sendFailure(Component.translatable("commands.academy.migrate.not_protected",
                    pos.toShortString()));
        }
        return 0;
    }

    static List<BlockEntity> loadedProtectedMachines(ServerLevel level, BlockPos center, int radius) {
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        List<BlockEntity> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity machine : chunk.getBlockEntities().values()) {
                    BlockPos pos = machine.getBlockPos();
                    if (Math.abs(pos.getX() - center.getX()) <= radius
                            && Math.abs(pos.getY() - center.getY()) <= radius
                            && Math.abs(pos.getZ() - center.getZ()) <= radius
                            && MachineOwnership.isProtectedMachine(machine)) {
                        result.add(machine);
                    }
                }
            }
        }
        result.sort(Comparator.comparing(BlockEntity::getBlockPos));
        return result;
    }
}
