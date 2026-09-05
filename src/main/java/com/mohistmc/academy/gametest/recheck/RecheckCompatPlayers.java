package com.mohistmc.academy.gametest.recheck;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/** Real ServerPlayer/login/events with NeoForge's supported negotiated mock connection.
 * This is an EmbeddedChannel fixture, not a socket client or a networking acceptance test.
 * Payload registration, Curios login/sync, and production hurt/die/tick remain enabled.
 */
public final class RecheckCompatPlayers {
    private RecheckCompatPlayers() {}

    public static Session connect(GameTestHelper helper) {
        return connect(helper, new GameProfile(UUID.randomUUID(), "Recheck" + UUID.randomUUID().toString().substring(0, 8)));
    }

    public static Session connect(GameTestHelper helper, GameProfile profile) {
        var cookie = new CommonListenerCookie(profile, 0,
                net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        // Official NeoForge GameTest helper builds NetworkPayloadSetup from actual registered payload IDs/versions.
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        if (!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(connection,
                net.minecraft.network.ConnectionProtocol.PLAY,
                net.minecraft.resources.ResourceLocation.parse("curios:sync_data"))) {
            channel.finishAndReleaseAll();
            throw new IllegalStateException("Curios sync_data was not registered for the negotiated mock fixture");
        }
        com.mojang.logging.LogUtils.getLogger().info("RECHECK_COMPAT_CONNECTION kind=EmbeddedChannel negotiatedMock=true realSocket=false playChannels={}",
                net.neoforged.neoforge.network.registration.ChannelAttributes.getPayloadSetup(connection)
                        .getChannels(net.minecraft.network.ConnectionProtocol.PLAY).size());
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 2.5);
        player.setYRot(0); player.setXRot(0);
        if (player.getClass() != ServerPlayer.class || player.isFakePlayer()
                || helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            throw new IllegalStateException("fixture must register an unmodified real ServerPlayer");
        }
        // The production connection tick drives ServerPlayer.doTick and therefore PlayerTick events.
        helper.getLevel().getServer().getConnection().getConnections().add(connection);
        return new Session(player, connection, channel);
    }

    public static final class Session implements AutoCloseable {
        private final ServerPlayer player;
        private final Connection connection;
        private final EmbeddedChannel channel;
        private boolean closed;
        private Session(ServerPlayer player, Connection connection, EmbeddedChannel channel) { this.player = player; this.connection = connection; this.channel = channel; }
        public ServerPlayer player() { return player; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            // Production PlayerList.remove fires logout before saving inventory/attachments.
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) player.server.getPlayerList().remove(player);
            player.server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        }
    }
}



