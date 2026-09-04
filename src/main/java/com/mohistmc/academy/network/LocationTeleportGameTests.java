package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real dimension-transfer coverage for Location Teleport's entity journal. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class LocationTeleportGameTests {
    private LocationTeleportGameTests() {}

    @GameTest(template = "empty")
    public static void final112UsesPreTeleportDistanceForProficiency(GameTestHelper helper) {
        if (LocationTeleportActionPacket.legacyProficiencyIncrement(199.999f) != .015f
                || LocationTeleportActionPacket.legacyProficiencyIncrement(200f) != .03f
                || LocationTeleportActionPacket.legacyProficiencyIncrement(800f) != .03f) {
            helper.fail("Location Teleport lost the final 1.12.2 near/far proficiency boundary");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crossDimensionGraphTracksReplacementEntitiesAndRestoresRiding(
            GameTestHelper helper) {
        ServerLevel origin = helper.getLevel();
        ServerLevel target = origin.getServer().getLevel(Level.NETHER);
        if (target == null) {
            helper.fail("Nether level is unavailable for the cross-dimension teleport test");
            return;
        }

        Wolf vehicle = EntityType.WOLF.create(origin);
        Wolf rider = EntityType.WOLF.create(origin);
        if (vehicle == null || rider == null) {
            helper.fail("Wolf fixtures could not be created");
            return;
        }
        // The production graph only permits owned tame animals; keep these
        // fixtures persistent so the empty Nether cannot despawn them before
        // its entity index is observed on the next tick.
        vehicle.setPersistenceRequired();
        rider.setPersistenceRequired();
        var owner = helper.makeMockServerPlayerInLevel();
        vehicle.tame(owner);
        rider.tame(owner);
        vehicle.getData(AcademyAttachments.PLAYER_ABILITY).setPlayerLevel(4);
        Vec3 source = Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3)));
        owner.teleportTo(source.x, source.y, source.z);
        vehicle.setPos(source);
        rider.setPos(source.add(0, 0.5, 0));
        if (!origin.addFreshEntity(vehicle) || !origin.addFreshEntity(rider)
                || !rider.startRiding(vehicle, true)) {
            helper.fail("Riding graph fixture could not be installed");
            return;
        }

        Vec3 destination = new Vec3(8.5, 200, 8.5);
        net.minecraft.core.BlockPos destinationPos = net.minecraft.core.BlockPos.containing(destination);
        ChunkPos destinationChunk = new ChunkPos(destinationPos);
        // A GameTest mock player does not maintain the normal server player's
        // destination view ticket. Keep the target entity section resident so
        // this test observes transaction semantics instead of mock-player chunk
        // unloading two ticks later.
        target.getChunkSource().addRegionTicket(TicketType.PORTAL,
                destinationChunk, 3, destinationPos);
        target.getChunkAt(destinationPos);
        LocationTeleportActionPacket.GraphCommitResult result =
                LocationTeleportActionPacket.commitEntityGraphDetailed(
                        List.of(owner, vehicle, rider), target, owner.position(), destination);
        if (!result.committed()) {
            target.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    destinationChunk, 3, destinationPos);
            helper.fail("Cross-dimension entity graph transaction was rejected");
            return;
        }
        Entity trackedVehicle = result.trackedEntities().get(1);
        Entity trackedRider = result.trackedEntities().get(2);
        if (trackedVehicle.level() != target || trackedRider.level() != target
                || trackedVehicle.isRemoved() || trackedRider.isRemoved()
                || !trackedVehicle.isAddedToLevel() || !trackedRider.isAddedToLevel()
                || !trackedVehicle.getUUID().equals(vehicle.getUUID())
                || !trackedRider.getUUID().equals(rider.getUUID())) {
            target.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    destinationChunk, 3, destinationPos);
            helper.fail("Journal did not retain live target replacements immediately after commit");
            return;
        }

        // GameTest's embedded player does not maintain a real connection view,
        // so target.getEntity(UUID) can legitimately exclude an accepted entity
        // whose section stays HIDDEN. addWithUUID + isAddedToLevel are the
        // authoritative insertion boundary; inspect those exact replacements.
        Entity movedVehicle = trackedVehicle;
        Entity movedRider = trackedRider;
        boolean vehicleStatePreserved = movedVehicle instanceof Wolf movedWolf
                    && movedWolf.isTame() && owner.getUUID().equals(movedWolf.getOwnerUUID())
                    && movedWolf.isPersistenceRequired()
                    && movedWolf.getData(AcademyAttachments.PLAYER_ABILITY).getPlayerLevel() == 4;
        if (owner.serverLevel() != target || movedVehicle.isRemoved() || movedRider.isRemoved()
                || !vehicle.isRemoved() || !rider.isRemoved()
                || movedRider.getVehicle() != movedVehicle || !vehicleStatePreserved) {
            target.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    destinationChunk, 3, destinationPos);
            helper.fail("Replacement/riding mismatch: caster=" + (owner.serverLevel() == target)
                    + ", oldVehicleRemoved=" + vehicle.isRemoved()
                    + ", oldRiderRemoved=" + rider.isRemoved() + ", riding="
                    + (movedRider.getVehicle() == movedVehicle)
                    + ", trackedVehicleRemoved=" + trackedVehicle.isRemoved()
                    + ", trackedVehicleReason=" + trackedVehicle.getRemovalReason()
                    + ", trackedRiderRemoved=" + trackedRider.isRemoved()
                    + ", trackedRiderReason=" + trackedRider.getRemovalReason()
                    + ", ownerAttachmentState=" + vehicleStatePreserved);
            return;
        }
        movedRider.stopRiding();
        movedRider.discard();
        movedVehicle.discard();
        owner.teleportTo(origin, source.x, source.y, source.z,
                owner.getYRot(), owner.getXRot());
        target.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                destinationChunk, 3, destinationPos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCrossDimensionGraphRollsBackTheActualReplacement(
            GameTestHelper helper) {
        ServerLevel origin = helper.getLevel();
        ServerLevel target = origin.getServer().getLevel(Level.NETHER);
        if (target == null) {
            helper.fail("Nether level is unavailable for the rollback test");
            return;
        }

        Wolf first = EntityType.WOLF.create(origin);
        if (first == null) {
            helper.fail("Wolf rollback fixture could not be created");
            return;
        }
        Vec3 source = Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5)));
        first.setPos(source);
        if (!origin.addFreshEntity(first)) {
            helper.fail("Wolf rollback fixture could not enter the level");
            return;
        }

        // PLAYER is a createNothing EntityType. This real Entity.teleportTo call
        // fails only after the first wolf has already produced a target clone.
        Entity nonTransferable = new Entity(EntityType.PLAYER, origin) {
            @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
            @Override protected void readAdditionalSaveData(CompoundTag tag) {}
            @Override protected void addAdditionalSaveData(CompoundTag tag) {}
        };
        nonTransferable.setPos(source.add(1, 0, 0));
        Vec3 destination = new Vec3(12.5, 200, 12.5);
        target.getChunkAt(net.minecraft.core.BlockPos.containing(destination));

        if (LocationTeleportActionPacket.commitEntityGraph(
                List.of(first, nonTransferable), target, first.position(), destination)) {
            helper.fail("A graph containing a non-transferable entity unexpectedly committed");
            return;
        }
        Entity restored = origin.getEntity(first.getUUID());
        if (restored == null || restored.isRemoved() || target.getEntity(first.getUUID()) != null
                || restored.position().distanceToSqr(source) > 0.25) {
            helper.fail("Failed transaction left a target clone or did not restore the real entity");
            return;
        }
        restored.discard();
        helper.succeed();
    }
}
