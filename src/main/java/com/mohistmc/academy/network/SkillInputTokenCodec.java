package com.mohistmc.academy.network;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
public final class SkillInputTokenCodec {
    private SkillInputTokenCodec() {}
    public static final StreamCodec<ByteBuf, SkillInputToken> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SkillInputToken::session, ByteBufCodecs.VAR_LONG, SkillInputToken::sequence, SkillInputToken::new);
}