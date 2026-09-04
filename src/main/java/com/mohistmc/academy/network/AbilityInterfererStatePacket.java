package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ClientPacketBridge;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Private menu state; whitelist identities are sent only to the player viewing this menu. */
public record AbilityInterfererStatePacket(BlockPos pos, int containerId, int energy, int range,
                                            boolean enabled, boolean owner,
                                            List<Entry> whitelist) implements CustomPacketPayload {
    public record Entry(UUID id, String name) {}

    public static final Type<AbilityInterfererStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ability_interferer_state"));
    private static final StreamCodec<ByteBuf, String> NAME =
            ByteBufCodecs.stringUtf8(AbilityInterfererBlockEntity.MAX_PLAYER_NAME);

    public static final StreamCodec<ByteBuf, AbilityInterfererStatePacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buffer) -> {
                BlockPos.STREAM_CODEC.encode(buffer, packet.pos);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.containerId);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.energy);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.range);
                buffer.writeBoolean(packet.enabled);
                buffer.writeBoolean(packet.owner);
                int count = Math.min(AbilityInterfererBlockEntity.MAX_WHITELIST,
                        packet.whitelist == null ? 0 : packet.whitelist.size());
                buffer.writeByte(count);
                for (int index = 0; index < count; index++) {
                    Entry entry = packet.whitelist.get(index);
                    buffer.writeLong(entry.id.getMostSignificantBits());
                    buffer.writeLong(entry.id.getLeastSignificantBits());
                    NAME.encode(buffer, entry.name);
                }
            }, buffer -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
                int containerId = ByteBufCodecs.VAR_INT.decode(buffer);
                int energy = ByteBufCodecs.VAR_INT.decode(buffer);
                int range = ByteBufCodecs.VAR_INT.decode(buffer);
                boolean enabled = buffer.readBoolean();
                boolean owner = buffer.readBoolean();
                int count = buffer.readUnsignedByte();
                List<Entry> whitelist = new ArrayList<>(Math.min(count,
                        AbilityInterfererBlockEntity.MAX_WHITELIST));
                for (int index = 0; index < count; index++) {
                    UUID id = new UUID(buffer.readLong(), buffer.readLong());
                    String name = NAME.decode(buffer);
                    if (index < AbilityInterfererBlockEntity.MAX_WHITELIST) {
                        whitelist.add(new Entry(id, name));
                    }
                }
                return new AbilityInterfererStatePacket(pos, containerId, energy, range,
                        enabled, owner, List.copyOf(whitelist));
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static AbilityInterfererStatePacket of(ServerPlayer viewer,
                                                   AbilityInterfererBlockEntity machine,
                                                   int containerId) {
        List<Entry> entries = machine.getWhitelistEntries().stream()
                .map(entry -> new Entry(entry.id(), entry.name())).toList();
        return new AbilityInterfererStatePacket(machine.getBlockPos(), containerId,
                machine.getEnergyStored(), machine.getRange(), machine.isEnabled(),
                machine.canManage(viewer), entries);
    }

    public static void handle(AbilityInterfererStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketBridge.abilityInterferer(packet));
    }
}
