package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.block.DevNormal;
import com.mohistmc.academy.world.block.DevAdvanced;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens a block developer's wireless page from its authenticated skill tree. */
public record OpenDevNetworkPacket(BlockPos pos, UUID nonce) implements CustomPacketPayload {
    public static final Type<OpenDevNetworkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "open_dev_network"));
    public static final StreamCodec<ByteBuf, OpenDevNetworkPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDevNetworkPacket::pos,
            net.minecraft.core.UUIDUtil.STREAM_CODEC, OpenDevNetworkPacket::nonce,
            OpenDevNetworkPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenDevNetworkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Optional<BlockPos> sessionPos = Optional.of(packet.pos());
            boolean normalSession = DevLearningSessionManager.validate(player, packet.nonce(),
                    DevMachineType.NORMAL, sessionPos);
            boolean advancedSession = DevLearningSessionManager.validate(player, packet.nonce(),
                    DevMachineType.ADVANCED, sessionPos);
            if ((!normalSession && !advancedSession)
                    || !player.serverLevel().isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + .5, packet.pos().getY() + .5,
                    packet.pos().getZ() + .5) > 64
                    || !player.serverLevel().mayInteract(player, packet.pos())) return;
            var state = player.serverLevel().getBlockState(packet.pos());
            net.minecraft.world.MenuProvider provider;
            if (normalSession && state.getBlock() instanceof DevNormal dev) {
                provider = dev.getMenuProvider(state, player.serverLevel(), packet.pos());
            } else if (advancedSession && state.getBlock() instanceof DevAdvanced dev) {
                provider = dev.getMenuProvider(state, player.serverLevel(), packet.pos());
            } else {
                return;
            }
            player.openMenu(provider, packet.pos())
                    .ifPresent(containerId -> SafePayloadSender.send(player,
                            new OpenDevNetworkPagePacket(packet.pos(), containerId)));
        });
    }
}
