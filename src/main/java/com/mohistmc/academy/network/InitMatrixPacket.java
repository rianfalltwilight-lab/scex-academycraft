package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.MatrixMenu;
import com.mohistmc.academy.world.AcademyItems;

/**
 * 客户端→服务端：初始化矩阵网络。
 */
public record InitMatrixPacket(MenuActionToken actionToken, BlockPos pos, String ssid, String password) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int MAX_SSID_LENGTH = NetworkInputLimits.SSID;
    private static final int MAX_PASSWORD_LENGTH = NetworkInputLimits.PASSWORD;

    public static final Type<InitMatrixPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "init_matrix"));

    public static final StreamCodec<ByteBuf, InitMatrixPacket> STREAM_CODEC =
            StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, InitMatrixPacket::actionToken,
                    BlockPos.STREAM_CODEC, InitMatrixPacket::pos,
                    ByteBufCodecs.stringUtf8(MAX_SSID_LENGTH), InitMatrixPacket::ssid,
                    ByteBufCodecs.stringUtf8(MAX_PASSWORD_LENGTH), InitMatrixPacket::password,
                    InitMatrixPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InitMatrixPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!NetworkInputLimits.validRequired(packet.ssid(), MAX_SSID_LENGTH)
                        || !NetworkInputLimits.validOptional(packet.password(), MAX_PASSWORD_LENGTH)) return;
                ServerLevel level = player.serverLevel();
                if (!(player.containerMenu instanceof MatrixMenu menu)
                        || !packet.pos().equals(menu.pos) || !menu.stillValid(player) || !menu.acceptAction(packet.actionToken(), player)) return;
                if (!level.isLoaded(packet.pos())
                        || player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                        packet.pos().getZ() + 0.5) > 64.0
                        || !level.mayInteract(player, packet.pos())) return;
                BlockEntity be = level.getBlockEntity(packet.pos());
                if (be instanceof MatrixBlockEntity matrix) {
                    // 检查所有者权限
                    if (!matrix.canManage(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c只有矩阵所有者才能初始化！"));
                        return;
                    }
                    var networkState = WirelessSystem.reconcileMatrixNetwork(level, matrix);
                    if (networkState.active()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                networkState == WirelessSystem.MatrixNetworkState.RECOVERED
                                        ? "§a矩阵网络已从存档状态恢复！" : "§c该矩阵已经初始化过了！"));
                        return;
                    }
                    if (matrix.isInitialized()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c矩阵网络恢复失败，请稍后重试！"));
                        return;
                    }
                    int coreLevel = matrix.initializationCoreLevel();
                    if (!matrix.hasInitializationMaterials()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§c初始化需要 1 个矩阵核心和 3 个约束金属板"));
                        return;
                    }

                    String oldSsid = matrix.getSSID();
                    String oldPassword = matrix.getPassword();
                    int oldCapacity = matrix.getCapacity();
                    double oldBandwidth = matrix.getBandwidth();
                    double oldRange = matrix.getRange();
                    boolean created = false;
                    try {
                        // WirelessNet snapshots matrix characteristics during creation. Stage
                        // them first, but do not publish the initialized flag until creation wins.
                        matrix.setSSID(packet.ssid());
                        matrix.setPassword(packet.password());
                        matrix.applyCoreLevel(coreLevel);
                        created = WirelessSystem.createNetwork(level, matrix, packet.ssid(), packet.password());
                        if (created) {
                            matrix.setInitialized(true);
                            // 1.12 treats the core and three plates as installed machine
                            // components. They remain in the four slots, keep the shields
                            // visible, and are returned with the stateful machine on break.
                            matrix.setChanged();
                        }
                    } catch (RuntimeException failure) {
                        WirelessSystem.removeNetwork(level, matrix);
                        restore(matrix, oldSsid, oldPassword, oldCapacity, oldBandwidth, oldRange);
                        LOGGER.error("Matrix network initialization rolled back at {}", packet.pos(), failure);
                        return;
                    }
                    if (created) {
                        level.sendBlockUpdated(packet.pos(), matrix.getBlockState(), matrix.getBlockState(), 3);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a矩阵初始化成功！网络: " + packet.ssid()));
                    } else {
                        restore(matrix, oldSsid, oldPassword, oldCapacity, oldBandwidth, oldRange);
                        level.sendBlockUpdated(packet.pos(), matrix.getBlockState(), matrix.getBlockState(), 3);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c网络创建失败，矩阵未初始化，可修改后重试"));
                    }
                }
            }
        });
    }


    private static void restore(MatrixBlockEntity matrix, String ssid, String password,
                                int capacity, double bandwidth, double range) {
        matrix.setInitialized(false);
        matrix.setSSID(ssid);
        matrix.setPassword(password);
        matrix.setCapacity(capacity);
        matrix.setBandwidth(bandwidth);
        matrix.setRange(range);
    }
}
