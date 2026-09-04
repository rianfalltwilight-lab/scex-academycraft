package com.mohistmc.academy.network;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.PlayerAbilityData.TeleportLocation;
import io.netty.buffer.ByteBuf;import java.util.*;
import net.minecraft.network.codec.*;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record LocationTeleportSyncPacket(List<TeleportLocation> locations) implements CustomPacketPayload{
 public static final Type<LocationTeleportSyncPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"location_teleport_sync"));
 private static final StreamCodec<ByteBuf,String> STR=ByteBufCodecs.stringUtf8(64);
 public static final StreamCodec<ByteBuf,LocationTeleportSyncPacket> STREAM_CODEC=StreamCodec.ofMember((p,b)->{int n=Math.min(32,p.locations.size());b.writeByte(n);for(int i=0;i<n;i++){TeleportLocation l=p.locations.get(i);STR.encode(b,l.name());STR.encode(b,l.dimension());b.writeDouble(l.x());b.writeDouble(l.y());b.writeDouble(l.z());}},b->{int n=b.readUnsignedByte();if(n>32)throw new io.netty.handler.codec.DecoderException("too many teleport locations");List<TeleportLocation> out=new ArrayList<>(n);for(int i=0;i<n;i++)out.add(new TeleportLocation(STR.decode(b),STR.decode(b),b.readDouble(),b.readDouble(),b.readDouble()));if(b.isReadable())throw new io.netty.handler.codec.DecoderException("trailing teleport location bytes");return new LocationTeleportSyncPacket(List.copyOf(out));});
 public Type<? extends CustomPacketPayload> type(){return TYPE;} public static void handle(LocationTeleportSyncPacket p,IPayloadContext c){c.enqueueWork(()->com.mohistmc.academy.client.ClientPacketBridge.locationTeleport(p));}
}
