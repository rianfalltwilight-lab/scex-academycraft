package com.mohistmc.academy.world.effect;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Vanilla client lightning animation/sound, without a server entity or world mutation. */
public final class VisualLightning {
    private VisualLightning() {}

    public static void send(ServerLevel level, Vec3 position) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.setPos(position);
        bolt.setVisualOnly(true);
        // setVisualOnly alone does not suppress modern copper/lightning-rod callbacks.
        // Reserve the ordinary unique entity id but never add/tick the bolt on the server.
        var packet = new ClientboundAddEntityPacket(bolt.getId(), bolt.getUUID(),
                position.x, position.y, position.z, 0, 0, EntityType.LIGHTNING_BOLT,
                0, Vec3.ZERO, 0);
        for (var player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(bolt.blockPosition()), false)) {
            player.connection.send(packet);
        }
    }
}
