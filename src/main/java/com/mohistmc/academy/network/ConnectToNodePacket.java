package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.AcademyMenu;

/**
 * 客户端→服务端：连接机器到指定节点。
 */
public record ConnectToNodePacket(MenuActionToken actionToken, BlockPos machinePos, BlockPos nodePos, Optional<String> password) implements CustomPacketPayload {

    private record Attempt(int failures, long retryAt) {}
    private record AttemptKey(UUID player, BlockPos node) {}
    private static final Map<AttemptKey, Attempt> FAILED_AUTH = new HashMap<>();
    private static final int MAX_PASSWORD_LENGTH = 64;
    public static void clearAll() { FAILED_AUTH.clear(); }

    public static final Type<ConnectToNodePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "connect_to_node"));

    public static final StreamCodec<ByteBuf, ConnectToNodePacket> STREAM_CODEC =
            StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, ConnectToNodePacket::actionToken,
                    BlockPos.STREAM_CODEC, ConnectToNodePacket::machinePos,
                    BlockPos.STREAM_CODEC, ConnectToNodePacket::nodePos,
                    ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_PASSWORD_LENGTH)), ConnectToNodePacket::password,
                    ConnectToNodePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectToNodePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                if (!(player.containerMenu instanceof AcademyMenu menu)
                        || !packet.machinePos().equals(menu.pos) || !menu.stillValid(player) || !menu.acceptAction(packet.actionToken(), player)) return;
                if (packet.password().isPresent() && packet.password().get().length() > MAX_PASSWORD_LENGTH) return;
                if (!PayloadRateLimiter.allow(player.getUUID(), "machine_node_connect",
                        level.getGameTime(), 20, 8)) return;
                AttemptKey attemptKey = new AttemptKey(player.getUUID(), packet.nodePos().immutable());
                Attempt previousAttempt = FAILED_AUTH.get(attemptKey);
                if (previousAttempt != null && level.getGameTime() < previousAttempt.retryAt()) return;

                if (!level.isLoaded(packet.machinePos()) || !level.isLoaded(packet.nodePos())
                        || player.distanceToSqr(packet.machinePos().getX() + 0.5, packet.machinePos().getY() + 0.5,
                        packet.machinePos().getZ() + 0.5) > 64.0) {
                    return;
                }
                if (!level.mayInteract(player, packet.machinePos())) {
                    // The player must control the open machine.  The remote
                    // node itself follows the final-1.12.2 public/password
                    // access contract; applying spawn/claim interaction again
                    // at that coordinate made valid wireless links disappear
                    // on protected servers even though the player never edits
                    // the node block or inventory here.
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c没有权限操作当前机器"));
                    return;
                }

                // 获取节点
                BlockEntity nodeBe = level.getBlockEntity(packet.nodePos());
                if (!(nodeBe instanceof com.mohistmc.academy.energy.api.block.IWirelessNode iNode)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c未找到无线节点"));
                    return;
                }
                double nodeRange = Math.max(0.0, Math.min(256.0, iNode.getRange()));
                if (packet.machinePos().distSqr(packet.nodePos()) > nodeRange * nodeRange) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c机器超出节点信号范围"));
                    return;
                }
                // 获取机器
                BlockEntity machineBe = level.getBlockEntity(packet.machinePos());
                if (machineBe == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c未找到机器"));
                    return;
                }

                String pass = packet.password().orElse("");
                // A protected node must authenticate every request. Previously a caller could
                // bypass authentication by supplying any non-empty (but incorrect) password.
                boolean needAuth = !iNode.getPassword().isEmpty();

                if (machineBe instanceof IWirelessGenerator gen) {
                    boolean ok = WirelessSystem.linkGenerator(level, iNode, gen, needAuth, pass);
                    if (ok) {
                        FAILED_AUTH.remove(attemptKey);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a发电机已连接到节点"));
                    } else {
                        recordFailure(attemptKey, previousAttempt, level.getGameTime(), needAuth);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c连接失败，请检查密码和距离"));
                    }
                } else if (machineBe instanceof IWirelessReceiver rec) {
                    boolean ok = WirelessSystem.linkReceiver(level, iNode, rec, needAuth, pass);
                    if (ok) {
                        FAILED_AUTH.remove(attemptKey);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a机器已连接到节点"));
                    } else {
                        recordFailure(attemptKey, previousAttempt, level.getGameTime(), needAuth);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c连接失败，请检查密码和距离"));
                    }
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该机器不支持无线连接"));
                }
            }
        });
    }

    private static void recordFailure(AttemptKey key, Attempt previous, long now, boolean authenticatedNode) {
        if (!authenticatedNode) return;
        int failures = previous == null ? 1 : Math.min(previous.failures() + 1, 8);
        long delay = Math.min(20L << Math.min(failures - 1, 4), 20L * 30L);
        FAILED_AUTH.put(key, new Attempt(failures, now + delay));
    }

    public static void forgetPlayer(UUID playerId) {
        FAILED_AUTH.keySet().removeIf(key -> key.player().equals(playerId));
    }
}
