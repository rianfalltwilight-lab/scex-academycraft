package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Release-gate contracts for expensive or compatibility-sensitive abilities.
 *
 * <p>These deliberately combine black-box codec checks with narrow source contracts. A full
 * server GameTest remains authoritative for world behavior, while these tests cheaply prevent
 * removal of the bounds that make that behavior safe on arbitrary modpacks.</p>
 */
class PerformanceCompatibilityContractTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/", relative));
    }

    @Test void mineDetectFinal112PayloadIsBoundedAndOwnerOnly() throws Exception {
        // Final 1.12.2 limit: short count + range + (packed BlockPos + harvest level) * 8400.
        assertEquals(75_606, 2 + Float.BYTES + (Long.BYTES + 1) * 8_400);

        String effect = source("skill/ability/electromaster/MineDetectEffect.java");
        String packet = source("network/MineDetectResultPacket.java");
        assertTrue(effect.contains("MAX_ORES = 8400"));
        assertTrue(effect.contains("!level.hasChunkAt(pos)"), "scan must not force-load chunks");
        assertTrue(effect.contains("Mth.floor(centerX-range)")&&effect.contains("Mth.ceil(centerX+range)")
                        &&effect.contains("dx*dx+dy*dy+dz*dz>rangeSq"),
                "fractional legacy radius and exact player-coordinate sphere must not be truncated");
        assertTrue(effect.contains("SafePayloadSender.send(player,"),
                "results must be sent only to the invoking owner");
        assertTrue(packet.contains("MAX_RESULTS=8400") || packet.contains("MAX_RESULTS = 8400"),
                "decoder must reject more than the final 1.12.2 limit of 8400 entries");
        assertTrue(packet.contains("readUnsignedShort()"), "entry count must use a bounded short");
        assertTrue(packet.contains("writeFloat") && packet.contains("readFloat"),
                "legacy sends the interpolated range as a Float");
        assertTrue(packet.contains("count>MAX_RESULTS") || packet.contains("count > MAX_RESULTS"),
                "oversized payloads must be rejected rather than silently leaving trailing bytes");
    }

    @Test void plasmaUsesTheBoundedVanillaExplosionAndNeverLoadsMissingChunks() throws Exception {
        String plasma = source("entity/PlasmaOrbEntity.java");
        assertTrue(plasma.contains("!serverLevel.hasChunkAt(BlockPos.containing(next))"));
        assertTrue(plasma.contains("level.hasChunksAt(min, max)"));
        assertTrue(plasma.contains("DynamicSkillRules.destroysBlocks(level, \"plasma_cannon\")"),
                "the global legacy block-destruction gate must control the explosion interaction");
        assertTrue(plasma.contains("Level.ExplosionInteraction.BLOCK"));
        assertTrue(plasma.contains("Level.ExplosionInteraction.NONE"));
        assertTrue(plasma.contains("shouldBeSaved() { return false; }"),
                "a context-owned plasma body must not survive a restart without its charging context");
    }

    @Test void everyLegacyTerrainSkillUsesTheGlobalDimensionAwareGate() throws Exception {
        assertTrue(source("skill/ability/MagManipEffect.java").contains("ACConfig.Server.mayDestroyBlocks(p.serverLevel())"));
        assertTrue(source("skill/ability/electromaster/RailgunEffect.java").contains("destroysBlocks(level, getId())"));
        assertTrue(source("skill/ability/meltdowner/MeltdownerEffect.java").contains("destroysBlocks(level,getId())"));
        assertTrue(source("skill/ability/meltdowner/AbstractMineRayEffect.java").contains("destroysBlocks(level,getId())"));
        assertTrue(source("skill/ability/vecmanip/GroundShockEffect.java").contains("destroysBlocks(level, getId())"));
        assertTrue(source("skill/ability/vecmanip/DirBlastEffect.java").contains("destroysBlocks(level, getId())"));
        assertTrue(source("skill/ability/vecmanip/StormWingEffect.java").contains("destroysBlocks(level, getId())"));
        assertTrue(source("skill/ability/teleporter/ShiftTpEffect.java").contains("destroysBlocks(level, getId())"));
    }

    @Test void electromasterMetalTargetsRemainConfigAndTagDriven() throws Exception {
        String targets = source("skill/ability/electromaster/ElectromasterMetalTargets.java");
        String movement = source("skill/ability/electromaster/MagMovementEffect.java");
        String manipulation = source("skill/ability/MagManipEffect.java");
        assertTrue(targets.contains("ACConfig.Server.normalMetalBlocks()"));
        assertTrue(targets.contains("ACConfig.Server.weakMetalBlocks()"));
        assertTrue(targets.contains("ACConfig.Server.metalEntities()"));
        assertTrue(targets.contains("TagKey.create(Registries.BLOCK"));
        assertTrue(targets.contains("TagKey.create(Registries.ENTITY_TYPE"));
        assertTrue(movement.contains("ElectromasterMetalTargets.isNormal"));
        assertTrue(movement.contains("ElectromasterMetalTargets.isMetalEntity"));
        assertTrue(manipulation.contains("ElectromasterMetalTargets.isAny"));
        assertTrue(manipulation.contains("instanceof DoorBlock")&&manipulation.contains("mag_manip_immovable"),
                "legacy rejects doors and BlockMulti targets even when configured as metal");
    }

    @Test void magManipAbortDropsTheCarrierAndSuccessOwnsItsFinalSound() throws Exception {
        String effect=source("skill/ability/MagManipEffect.java");
        String carrier=source("world/entity/MagManipBlockEntity.java");
        String renderer=source("client/render/MagManipBlockRenderer.java");
        assertTrue(effect.contains("onChargingAbort")&&effect.contains("dropFromHold(p)"));
        assertTrue(effect.indexOf("x.throwFrom")<effect.indexOf("AcademySounds.EM_MAG_MANIP"));
        assertTrue(carrier.contains("dropFromHold")&&!carrier.contains("!p.isAlliedTo(e)"));
        assertTrue(carrier.contains("distSq<4?distSq/4:1")&&!carrier.substring(
                carrier.indexOf("dropFromHold"),carrier.indexOf("recoverMaterial")).contains("Vec3.ZERO"),
                "held material eases toward the two-block anchor and preserves its motion when dropped");
        assertTrue(effect.contains("throwTarget(p)")&&effect.contains("nearest.getEyeHeight()*.6"));
        assertTrue(renderer.contains("rotationDegrees")&&renderer.contains("RenderType.lines"),
                "carried material needs both legacy rotation and surround arcs");
    }

    @Test void magneticMovementRemainsHeldAtTheAnchorUntilReleaseOrExhaustion() throws Exception {
        String movement = source("skill/ability/electromaster/MagMovementEffect.java");
        assertTrue(movement.contains("Reaching one block"));
        assertFalse(movement.contains("return player.position().distanceToSqr(target) > 1.0"));
        assertTrue(movement.contains("dist < 1.0e-6")&&movement.contains("electromaster/mag_movement"),
                "non-zero target distance keeps legacy normalized pull and all valid terminations award it");
    }

    @Test void jetKeepsLegacyEightTickVectorAcrossItsSixteenMovementUpdates() throws Exception {
        String jet = source("skill/ability/meltdowner/JetEngineRuntime.java");
        assertTrue(jet.contains("TRAVEL_DIVISOR = 8"));
        assertTrue(jet.contains("LIFETIME_TICKS = 16"));
        assertTrue(jet.contains("next >= LIFETIME_TICKS"));
        assertTrue(jet.contains("noCollision"), "each movement step must be collision checked");
    }

    @Test void railgunTreatsUnknownLivingEntitiesGenericallyAndHonorsWalls() throws Exception {
        String railgun = source("skill/ability/electromaster/RailgunEffect.java");
        assertTrue(railgun.contains("getEntitiesOfClass(LivingEntity.class"),
                "modded LivingEntity subclasses must be eligible without a class allowlist");
        assertFalse(railgun.contains("instanceof Monster"));
        assertTrue(railgun.contains("state.is(Blocks.BEDROCK)"));
        assertTrue(railgun.contains("event.isCanceled()"));
        assertTrue(railgun.contains("traceBarrier(level, player"));
        assertTrue(railgun.contains("lookVec.scale(stopDistance)"),
                "entity ray must be clipped to the authoritative wall distance");
        assertTrue(railgun.contains("MAX_INCREMENT = 50.0"),
                "legacy RangedRayDamage traversed 50 increments");
        assertTrue(railgun.contains("RADIUS = 2.0"),
                "legacy Railgun constructed RangedRayDamage with radius two");
        assertTrue(railgun.contains("BEAM_RANGE = 45.0"),
                "legacy presentation beam remained 45 blocks long");
    }

    @Test void coinTossKeepsTheLegacyQteKinematicsAndRestartRefund() throws Exception {
        String coin = source("world/entity/CoinEntity.java");
        String item = source("world/item/Coin.java");
        assertTrue(item.contains("getDeltaMovement().y + 0.92D"));
        assertTrue(item.contains("player.getX()")&&item.contains("player.getY()")&&item.contains("player.getZ()"));
        assertTrue(coin.contains("setLifetime(120)")&&coin.contains("-0.06D"));
        assertTrue(coin.contains("setPos(thrower.getX(),getY(),thrower.getZ())"));
        assertTrue(coin.contains("refundOnLoad = true")&&coin.contains("new ItemEntity"),
                "a persisted toss must refund the consumed coin like 1.0.7 readFromNBT");
    }

    @Test void transientChargeBallsCannotSurviveRestartOrLiveForever() throws Exception {
        String ball = source("entity/MdBallEntity.java");
        assertTrue(ball.contains("shouldBeSaved() { return false; }"));
        assertTrue(ball.contains("HELD_LIFETIME = 2_333_333"));
        assertTrue(ball.contains("tickCount >= lifetime()"));
        assertTrue(ball.contains("discard(); return;"));
    }

    @Test void persistedRuntimeNumbersAndLocationsFailClosed() throws Exception {
        String data = source("skill/PlayerAbilityData.java");
        String location = source("network/LocationTeleportActionPacket.java");
        String plasma = source("entity/PlasmaOrbEntity.java");
        assertTrue(data.contains("Float.isFinite"));
        assertTrue(data.contains("Double.isFinite"));
        assertTrue(data.contains("sanitizeResources"));
        assertTrue(location.contains("PlayerAbilityData.sanitizeLocation"));
        assertTrue(plasma.contains("Float.isFinite"));
        assertTrue(plasma.contains("radius <= 32") || plasma.contains("radius>32"));
    }

    @Test void arcGenWorldSideEffectsArePermissionCheckedAndRateBounded() throws Exception {
        String arc = source("skill/ability/electromaster/ArcGenEffect.java");
        assertTrue(arc.contains("LAST_CAST"));
        assertTrue(arc.contains("level.mayInteract(player, pos)"));
        assertTrue(arc.contains("BlockEvent.EntityPlaceEvent"));
        assertTrue(arc.contains("BlockSnapshot.create"));
        assertTrue(arc.contains("level.addFreshEntity(fish)"), "NeoForge posts the cancellable entity-join event here");
        assertTrue(arc.contains("ServerStoppedEvent"));
        assertTrue(arc.contains("ClipContext.Fluid.ANY")&&arc.contains("ClipContext.Block.COLLIDER"),
                "legacy accepted both exact colliders and water; coarse fixed stepping is not equivalent");
        assertFalse(arc.contains("canStun"), "the 1.0.7 canStunEnemy local was dead code");
    }

    @Test void currentChargingKeepsTheLegacyExternalEnergyCompatibilityEdge() throws Exception {
        String charging = source("skill/ability/electromaster/ChargingEffect.java");
        assertTrue(charging.contains("held.getCapability(Capabilities.EnergyStorage.ITEM)"));
        assertTrue(charging.contains("level.getCapability("));
        assertTrue(charging.contains("Capabilities.EnergyStorage.BLOCK"));
        assertTrue(charging.contains("ExternalEnergyConversion.ifToFe(requested)"));
        assertTrue(charging.contains("receiver.receive(requested, true)"));
        assertTrue(charging.contains("receiver.receive(simulated, false)"));
        assertTrue(charging.contains("player.getLookAngle().scale(15.0)")
                &&charging.indexOf("if (supported)")<charging.lastIndexOf("visualEnd.x"),
                "block mode keeps the full miss arc but shows its surround only on supported receivers");
    }

    @Test void serializationAndServerStopAreDefenceInDepthBoundaries() throws Exception {
        String data = source("skill/PlayerAbilityData.java");
        String codec = source("skill/PlayerAbilityDataCodec.java");
        String charging = source("skill/SkillChargingManager.java");
        String cleanup = source("network/NetworkRuntimeCleanup.java");
        assertTrue(data.substring(data.indexOf("toSyncTag()"), data.indexOf("toSyncTag()") + 220)
                .contains("sanitizeForSerialization()"));
        assertTrue(codec.substring(codec.indexOf("CompoundTag write"), codec.indexOf("CompoundTag write") + 220)
                .contains("sanitizeForSerialization()"));
        assertTrue(charging.contains("clearAll() { STATES.clear(); }"));
        assertTrue(cleanup.contains("ServerStoppedEvent"));
        assertTrue(cleanup.contains("PayloadRateLimiter.clearAll()"));
        assertTrue(cleanup.contains("LocationTeleportActionPacket.clearAll()"));
    }
}
