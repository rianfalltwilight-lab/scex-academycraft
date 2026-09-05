package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.menu.AbilityInterfererMenu;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded, rate-limited control plane for the Ability Interferer menu. */
public record AbilityInterfererConfigPacket(MenuActionToken actionToken, BlockPos pos, int action, int value, String target)
        implements CustomPacketPayload {
    public static final int REQUEST = 0;
    public static final int TOGGLE = 1;
    public static final int SET_RANGE = 2;
    public static final int ADD_WHITELIST = 3;
    public static final int REMOVE_WHITELIST = 4;
    private static final int MAX_TARGET = 36;

    public static final Type<AbilityInterfererConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ability_interferer_config"));
    public static final StreamCodec<ByteBuf, AbilityInterfererConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, AbilityInterfererConfigPacket::actionToken,
                    BlockPos.STREAM_CODEC, AbilityInterfererConfigPacket::pos,
                    ByteBufCodecs.INT, AbilityInterfererConfigPacket::action,
                    ByteBufCodecs.INT, AbilityInterfererConfigPacket::value,
                    ByteBufCodecs.stringUtf8(MAX_TARGET), AbilityInterfererConfigPacket::target,
                    AbilityInterfererConfigPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AbilityInterfererConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof AbilityInterfererMenu menu)
                    || !packet.pos.equals(menu.pos) || !menu.stillValid(player)
                    || packet.action != REQUEST && !menu.acceptAction(packet.actionToken(), player)
                    || packet.action < REQUEST || packet.action > REMOVE_WHITELIST
                    || packet.target == null || packet.target.length() > MAX_TARGET) return;
            long now = player.serverLevel().getGameTime();
            if (!PayloadRateLimiter.allow(player.getUUID(), "ability_interferer_config", now, 5, 6)
                    || !player.serverLevel().isLoaded(packet.pos)
                    || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0
                    || !player.serverLevel().mayInteract(player, packet.pos)) return;
            BlockEntity raw = player.serverLevel().getBlockEntity(packet.pos);
            if (!(raw instanceof AbilityInterfererBlockEntity machine)) return;
            if (packet.action != REQUEST && !machine.canManage(player)) {
                player.sendSystemMessage(Component.translatable("message.academy.interferer.owner_only"));
                sendState(player, menu, machine);
                return;
            }

            boolean accepted = switch (packet.action) {
                case REQUEST -> true;
                case TOGGLE -> {
                    machine.setEnabled(!machine.isEnabled());
                    yield true;
                }
                case SET_RANGE -> machine.setRange(packet.value);
                case ADD_WHITELIST -> addWhitelist(player, machine, packet.target);
                case REMOVE_WHITELIST -> removeWhitelist(machine, packet.target);
                default -> false;
            };
            if (!accepted && packet.action != REQUEST) {
                player.sendSystemMessage(Component.translatable("message.academy.interferer.invalid_config"));
            }
            sendState(player, menu, machine);
        });
    }

    private static boolean addWhitelist(ServerPlayer player,
                                        AbilityInterfererBlockEntity machine, String rawName) {
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > AbilityInterfererBlockEntity.MAX_PLAYER_NAME
                || machine.getWhitelistEntries().size() >= AbilityInterfererBlockEntity.MAX_WHITELIST) {
            return false;
        }
        ServerPlayer online = player.server.getPlayerList().getPlayerByName(name);
        GameProfile profile = online == null ? null : online.getGameProfile();
        return machine.addWhitelist(profile);
    }

    private static boolean removeWhitelist(AbilityInterfererBlockEntity machine, String rawId) {
        try {
            return machine.removeWhitelist(UUID.fromString(rawId));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void sendState(ServerPlayer player, AbilityInterfererMenu menu,
                                  AbilityInterfererBlockEntity machine) {
        SafePayloadSender.send(player,
                AbilityInterfererStatePacket.of(player, machine, menu.containerId));
    }
}
