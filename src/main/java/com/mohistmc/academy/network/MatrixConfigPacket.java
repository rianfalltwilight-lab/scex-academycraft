package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.menu.MatrixMenu;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Owner-only mutation of an initialized Matrix SSID and password. */
public record MatrixConfigPacket(BlockPos matrixPos, Optional<String> ssid,
                                 Optional<String> password) implements CustomPacketPayload {
    public static final Type<MatrixConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "matrix_config"));
    public static final StreamCodec<ByteBuf, MatrixConfigPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MatrixConfigPacket::matrixPos,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(NetworkInputLimits.SSID)), MatrixConfigPacket::ssid,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(NetworkInputLimits.PASSWORD)), MatrixConfigPacket::password,
            MatrixConfigPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MatrixConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(player.containerMenu instanceof MatrixMenu menu)
                    || !packet.matrixPos().equals(menu.pos) || !menu.stillValid(player)
                    || packet.ssid().filter(value -> !NetworkInputLimits.validRequired(
                    value, NetworkInputLimits.SSID)).isPresent()
                    || packet.password().filter(value -> value.length() > NetworkInputLimits.PASSWORD).isPresent()
                    || packet.ssid().isEmpty() && packet.password().isEmpty()
                    || !PayloadRateLimiter.allow(player.getUUID(), "matrix_config",
                    level.getGameTime(), 20, 4)
                    || !level.isLoaded(packet.matrixPos())
                    || !level.mayInteract(player, packet.matrixPos())
                    || !(level.getBlockEntity(packet.matrixPos()) instanceof MatrixBlockEntity matrix)
                    || !matrix.canManage(player) || !matrix.isInitialized()) {
                return;
            }

            var state = WirelessSystem.reconcileMatrixNetwork(level, matrix);
            if (!state.active()) return;
            boolean changed = true;
            if (packet.ssid().isPresent()) {
                changed = WirelessSystem.changeSSID(level, matrix, packet.ssid().get());
            }
            if (changed && packet.password().isPresent()) {
                changed = WirelessSystem.changePassword(level, matrix, packet.password().get());
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(changed
                    ? "§a矩阵设置已保存" : "§c矩阵设置保存失败"));
        });
    }
}
