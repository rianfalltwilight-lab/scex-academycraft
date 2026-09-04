package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.menu.DevAdvancedMenu;
import com.mohistmc.academy.world.menu.DevNormalMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Optional;

/**
 * 客户端→服务端：高级开发机控制台命令。
 */
public record ConsoleCommandPacket(BlockPos pos, String command) implements CustomPacketPayload {

    public static final int MAX_COMMAND_LENGTH = 8;

    public static final Type<ConsoleCommandPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "console_command"));

    public static final StreamCodec<ByteBuf, ConsoleCommandPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConsoleCommandPacket::pos,
                    ByteBufCodecs.stringUtf8(MAX_COMMAND_LENGTH), ConsoleCommandPacket::command,
                    ConsoleCommandPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConsoleCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Level level = player.level();
            DevAdvancedMenu advancedMenu = player.containerMenu instanceof DevAdvancedMenu candidate
                    ? candidate : null;
            DevNormalMenu normalMenu = player.containerMenu instanceof DevNormalMenu candidate
                    ? candidate : null;
            boolean matchingAdvanced = advancedMenu != null && advancedMenu.pos != null
                    && packet.pos().equals(advancedMenu.pos) && advancedMenu.stillValid(player);
            boolean matchingNormal = normalMenu != null && normalMenu.pos != null
                    && packet.pos().equals(normalMenu.pos) && normalMenu.stillValid(player);
            if ((!matchingAdvanced && !matchingNormal)
                    || !level.isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) > 64.0
                    || !level.mayInteract(player, packet.pos())) {
                return;
            }
            BlockEntity be = level.getBlockEntity(packet.pos());
            DevAdvancedBlockEntity advanced = matchingAdvanced && be instanceof DevAdvancedBlockEntity dev
                    && advancedMenu.container.getBlockEntity(advancedMenu) == dev ? dev : null;
            DevNormalBlockEntity normal = matchingNormal && be instanceof DevNormalBlockEntity dev
                    && normalMenu.isBoundTo(dev) ? dev : null;
            if (advanced == null && normal == null) return;

            switch (packet.command()) {
                case "learn" -> {
                    DevMachineType type = advanced != null ? DevMachineType.ADVANCED : DevMachineType.NORMAL;
                    int energy = advanced != null ? advanced.getEnergyStored() : normal.getEnergyStored();
                    int maximum = advanced != null ? advanced.getMaxEnergyStored() : normal.getMaxEnergyStored();
                    Optional<BlockPos> sessionPos = Optional.of(packet.pos());
                    // The wireless page is a real server menu, while the 1.0.7
                    // skill tree is a client screen. Close the old container
                    // before returning or its synchronizer remains active and
                    // later hand/inventory changes (notably reset factors) are
                    // never delivered to the client inventory menu.
                    player.closeContainer();
                    LearnSkillPacket.syncToClient(player);
                    java.util.UUID nonce = DevLearningSessionManager.issue(player, type, sessionPos);
                    com.mohistmc.academy.energy.api.block.IWirelessUser user = advanced != null ? advanced : normal;
                    var connection = com.mohistmc.academy.energy.impl.WirelessSystem
                            .getUserConnection(player.serverLevel(), user);
                    String nodeName = connection != null && !connection.isDisposed() && connection.getNode() != null
                            ? connection.getNode().getNodeName() : "";
                    SafePayloadSender.send(player, new OpenDevGuiPacket(
                            type.ordinal(), energy, maximum,
                            sessionPos, nonce, nodeName));
                }
                case "reset" -> {
                    if (advanced == null) return;
                    // Kept only as a compatibility return path from the
                    // wireless container. Actual reset is the authenticated,
                    // timed action inside SkillTreeGui and reads the player's
                    // hand/inventory exactly like 1.0.7.
                    advanced.returnLegacyStagingItems(player);
                    Optional<BlockPos> sessionPos = Optional.of(packet.pos());
                    player.closeContainer();
                    LearnSkillPacket.syncToClient(player);
                    java.util.UUID nonce = DevLearningSessionManager.issue(
                            player, DevMachineType.ADVANCED, sessionPos);
                    var connection = com.mohistmc.academy.energy.impl.WirelessSystem
                            .getUserConnection(player.serverLevel(), advanced);
                    String nodeName = connection != null && !connection.isDisposed() && connection.getNode() != null
                            ? connection.getNode().getNodeName() : "";
                    SafePayloadSender.send(player, new OpenDevGuiPacket(
                            DevMachineType.ADVANCED.ordinal(), advanced.getEnergyStored(),
                            advanced.getMaxEnergyStored(), sessionPos, nonce, nodeName));
                }
                default -> {
                    player.sendSystemMessage(Component.literal("§c未知命令: " + packet.command()));
                    player.sendSystemMessage(Component.literal("§7可用命令: learn, reset"));
                }
            }
        });
    }
}
