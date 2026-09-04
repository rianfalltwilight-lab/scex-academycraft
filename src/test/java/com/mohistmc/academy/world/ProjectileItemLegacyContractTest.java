package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectileItemLegacyContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/", path));
    }

    @Test void silbarnHasACompleteServerEntityAndClientRegistrationChain() throws Exception {
        String item = source("world/item/Silbarn.java");
        String entity = source("world/entity/EntitySilbarn.java");
        String registry = source("world/AcademyEntities.java");
        String client = source("listener/ClientModListener.java");
        assertTrue(item.contains("level.addFreshEntity(projectile)"));
        assertTrue(item.contains("!player.getAbilities().instabuild"));
        assertTrue(entity.contains("GRAVITY_DELAY_TICKS = 50"));
        assertTrue(entity.contains("LINEAR_DRAG = 0.8D"));
        assertTrue(entity.contains("IMPACT_LIFETIME_TICKS = 10"));
        assertTrue(entity.contains("MAX_LIFETIME_TICKS"));
        assertTrue(entity.contains("EntityDataSerializers.BOOLEAN"));
        assertTrue(entity.contains("breakByRayBarrage()"));
        assertTrue(entity.contains("SILBARN_FRAGMENT"));
        assertTrue(registry.contains("ENTITIES.register(\"silbarn\""));
        assertTrue(client.contains("AcademyEntities.SILBARN.get()"));
        assertTrue(client.contains("SilbarnRenderer::new"));
    }

    @Test void magneticHookRestoresDamageAnchorRecoveryAndConsumption() throws Exception {
        String item = source("world/item/MagHook.java");
        String entity = source("world/entity/EntityMagHook.java");
        assertTrue(item.contains("if (consumed) stack.shrink(1)"));
        assertTrue(entity.contains("ENTITY_HIT_DAMAGE = 4.0F"));
        assertTrue(entity.contains("fixToBlock(blockHit)"));
        assertTrue(entity.contains("public void playerTouch(Player player)"));
        assertTrue(entity.contains("source.getEntity() instanceof Player"));
        assertTrue(entity.contains("MAX_FLIGHT_TICKS"));
        assertTrue(entity.contains("private Direction anchorFace = Direction.DOWN"));
    }

    @Test void locationTeleportUsesOnlyBoundedTemporaryFullChunkTickets() throws Exception {
        String packet = source("network/LocationTeleportActionPacket.java");
        assertTrue(packet.contains("MAX_DESTINATION_CHUNKS = 16"));
        assertTrue(packet.contains("ChunkStatus.FULL,true"));
        assertTrue(packet.contains("addRegionTicket(LOCATION_TELEPORT_TICKET"));
        assertTrue(packet.contains("removeRegionTicket(LOCATION_TELEPORT_TICKET"));
        assertTrue(packet.contains("finally"));
        assertTrue(packet.contains("insideBorder(target.getWorldBorder(),box)"));
        assertFalse(packet.contains("target.hasChunkAt(pos)"),
                "a legitimate server-saved destination must not fail merely because it is unloaded");
    }

    @Test void locationTeleportRestoresTheLegacyFiveBlockLivingEntitySweep() throws Exception {
        String packet = source("network/LocationTeleportActionPacket.java");
        assertTrue(packet.contains("p.getX()-5") && packet.contains("p.distanceToSqr(entity)<=25"));
        assertTrue(packet.contains("entity.getBbWidth()*entity.getBbWidth()*entity.getBbHeight()<80f"));
        assertTrue(packet.contains("e instanceof ServerPlayer"),
                "nearby players must remain server-authoritative and consent-scoped");
        assertTrue(packet.contains("graph.size()>=64"), "the restored sweep must stay adversarially bounded");
    }
}
