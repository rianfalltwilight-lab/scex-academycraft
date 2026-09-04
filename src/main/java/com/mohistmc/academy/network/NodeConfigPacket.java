package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.BaseNodeMenu;

/**
 * 客户端→服务端：更新节点名称和密码。
 */
public record NodeConfigPacket(BlockPos pos, Optional<String> name, Optional<String> password) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_PASSWORD_LENGTH = 64;

    public static final Type<NodeConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "node_config"));

    public static final StreamCodec<ByteBuf, NodeConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NodeConfigPacket::pos,
                    ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH)), NodeConfigPacket::name,
                    ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_PASSWORD_LENGTH)), NodeConfigPacket::password,
                    NodeConfigPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NodeConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!(player.containerMenu instanceof BaseNodeMenu menu)
                        || !packet.pos().equals(menu.pos) || !menu.stillValid(player)) {
                    player.sendSystemMessage(Component.literal("§c节点界面已失效，请重新打开"));
                    return;
                }
                if (packet.name().filter(n -> !NetworkInputLimits.validRequired(n, MAX_NAME_LENGTH)).isPresent()
                        || packet.password().filter(p -> !NetworkInputLimits.validOptional(p, MAX_PASSWORD_LENGTH)).isPresent()) {
                    player.sendSystemMessage(Component.literal("§c节点名或密码格式无效"));
                    return;
                }
                if (!player.level().isLoaded(packet.pos())
                        || player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                        packet.pos().getZ() + 0.5) > 64.0
                        || !player.level().mayInteract(player, packet.pos())) return;
                BlockEntity be = player.level().getBlockEntity(packet.pos());
                if (be instanceof BaseNodeBlockEntity node) {
                    if (!node.canManage(player)) {
                        player.sendSystemMessage(Component.translatable("message.academy.node.owner_only"));
                        return;
                    }
                    packet.name().ifPresent(node::setNodeName);
                    packet.password().ifPresent(node::setPassword);
                    node.setChanged();
                    player.sendSystemMessage(Component.literal("§aNode config updated"));
                }
            }
        });
    }
}
