package com.mohistmc.academy.world.block.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared authorization policy for player-owned AcademyCraft machines. */
public final class MachineOwnership {
    private MachineOwnership() {}

    /** Result of an explicit, administrator-authorized legacy owner migration. */
    public enum MigrationResult {
        CLAIMED,
        ALREADY_OWNED,
        NOT_PROTECTED_MACHINE
    }

    /** Owners and permission-level-2 administrators may mutate protected state. */
    public static boolean canManage(UUID owner, Player actor) {
        if (actor == null) return false;
        return canManage(owner, actor.getUUID(),
                actor instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2));
    }

    /** Pure policy seam used by behavior tests without constructing a game server. */
    public static boolean canManage(UUID owner, UUID actor, boolean administrator) {
        return actor != null && (administrator || owner != null && owner.equals(actor));
    }

    /**
     * Old rebuild saves and command-placed blocks can have no owner.  They are
     * deliberately not assigned to the first visitor: only an administrator
     * can migrate such a block by interacting with it.
     */
    public static boolean canClaimLegacy(UUID owner, Player actor) {
        return canClaimLegacyByPolicy(owner, actor instanceof ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(2));
    }

    /** Pure migration policy seam; the live overload derives administrator status server-side. */
    public static boolean canClaimLegacyByPolicy(UUID owner, boolean administrator) {
        return owner == null && administrator;
    }

    /** Machines whose owner controls inventory, topology, configuration, or protected placement. */
    public static boolean isProtectedMachine(BlockEntity machine) {
        return machine instanceof BaseNodeBlockEntity
                || machine instanceof MatrixBlockEntity
                || machine instanceof AbilityInterfererBlockEntity
                || machine instanceof WindGenMainBlockEntity;
    }

    /** Returns the authoritative owner, or {@code null} for a legacy unowned protected machine. */
    public static UUID ownerOf(BlockEntity machine) {
        if (machine instanceof BaseNodeBlockEntity node) return node.getOwnerUUID();
        if (machine instanceof MatrixBlockEntity matrix) return matrix.getOwnerUUID();
        if (machine instanceof AbilityInterfererBlockEntity interferer) return interferer.getOwner();
        if (machine instanceof WindGenMainBlockEntity wind) return wind.getOwnerUUID();
        return null;
    }

    /**
     * Assign an owner only when a protected machine is genuinely ownerless.
     *
     * <p>Authorization belongs to the command or migration caller. This method
     * never overwrites an existing owner and therefore cannot transfer a live
     * player's machine as a side effect of an upgrade scan.</p>
     */
    public static MigrationResult migrateLegacyOwner(BlockEntity machine, ServerPlayer targetOwner) {
        if (!isProtectedMachine(machine)) return MigrationResult.NOT_PROTECTED_MACHINE;
        if (ownerOf(machine) != null || targetOwner == null) return MigrationResult.ALREADY_OWNED;

        if (machine instanceof BaseNodeBlockEntity node) {
            node.setOwnerUUID(targetOwner.getUUID());
        } else if (machine instanceof MatrixBlockEntity matrix) {
            matrix.setOwnerUUID(targetOwner.getUUID());
        } else if (machine instanceof AbilityInterfererBlockEntity interferer) {
            interferer.assignOwnerForMigration(targetOwner);
        } else if (machine instanceof WindGenMainBlockEntity wind) {
            wind.setOwnerUUID(targetOwner.getUUID());
        }
        if (machine.getLevel() != null && !machine.getLevel().isClientSide()) {
            machine.getLevel().sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(),
                    machine.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        return MigrationResult.CLAIMED;
    }
}
