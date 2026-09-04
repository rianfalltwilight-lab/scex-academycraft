package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Owner-only, bounded mine result. Highlights are client-local; the server tracks no marker entities. */
public record MineDetectResultPacket(List<Entry> entries, float range) implements CustomPacketPayload {
    public static final int MAX_RESULTS = 8400;
    public record Entry(BlockPos pos, int harvestLevel) {}
    public static final Type<MineDetectResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "mine_detect_result"));
    public static final StreamCodec<ByteBuf, MineDetectResultPacket> STREAM_CODEC = StreamCodec.ofMember((p,b)->{
        int count=Math.min(MAX_RESULTS,p.entries.size()); b.writeShort(count); b.writeFloat(safeRange(p.range));
        for(int i=0;i<count;i++){Entry e=p.entries.get(i);b.writeLong(e.pos.asLong());b.writeByte(Math.max(0,Math.min(3,e.harvestLevel)));}
    },b->{int count=b.readUnsignedShort();if(count>MAX_RESULTS)throw new IllegalArgumentException("Too many mine-detect entries: "+count);float range=safeRange(b.readFloat());List<Entry> entries=new ArrayList<>(count);for(int i=0;i<count;i++)entries.add(new Entry(BlockPos.of(b.readLong()),Math.min(3,b.readUnsignedByte())));return new MineDetectResultPacket(List.copyOf(entries),range);});
    private static float safeRange(float range){return Float.isFinite(range)?Math.clamp(range,0.0f,28.0f):0.0f;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(MineDetectResultPacket packet, IPayloadContext context){context.enqueueWork(()->com.mohistmc.academy.client.ClientPacketBridge.mineDetect(packet));}
}
