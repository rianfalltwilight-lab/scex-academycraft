package com.mohistmc.academy.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A mutation belongs to one open menu and one position in that menu's action stream. */
public record MenuActionToken(int containerId, UUID session, long sequence) {
    public static final StreamCodec<ByteBuf, MenuActionToken> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MenuActionToken::containerId,
            UUIDUtil.STREAM_CODEC, MenuActionToken::session,
            ByteBufCodecs.VAR_LONG, MenuActionToken::sequence,
            MenuActionToken::new);

    /** Per-open action stream. The client becomes ready only after every UUID
     * word and the final ready marker arrive, including signed-short values. */
    public static final class Session {
        public static final int WORD_COUNT = 9;
        private final long[] nonce = new long[2];
        private final boolean server;
        private int receivedWords;
        private boolean readyMarker;
        private long outgoingSequence;
        private long acceptedSequence;

        public Session() { server = false; }
        public Session(UUID nonce) {
            server = true;
            this.nonce[0] = nonce.getMostSignificantBits();
            this.nonce[1] = nonce.getLeastSignificantBits();
            receivedWords = 0xff;
            readyMarker = true;
        }
        public boolean ready() { return readyMarker && receivedWords == 0xff; }
        public int word(int index) {
            checkIndex(index);
            return index == 8 ? (ready() ? 1 : 0)
                    : (int) (nonce[index / 4] >>> ((index % 4) * 16)) & 0xffff;
        }
        public void receiveWord(int index, int value) {
            checkIndex(index);
            if (server) return;
            if (index == 8) { readyMarker = value == 1; return; }
            int half = index / 4, shift = index % 4 * 16;
            nonce[half] = (nonce[half] & ~(0xffffL << shift)) | ((value & 0xffffL) << shift);
            receivedWords |= 1 << index;
        }
        public MenuActionToken next(int containerId) {
            if (!ready()) throw new IllegalStateException("Menu action session is still synchronizing");
            if (outgoingSequence == Long.MAX_VALUE) throw new IllegalStateException("Menu action stream exhausted");
            return new MenuActionToken(containerId, new UUID(nonce[0], nonce[1]), ++outgoingSequence);
        }
        public boolean accept(MenuActionToken token, int containerId) {
            if (!server || !ready() || token == null || token.containerId != containerId
                    || token.sequence <= acceptedSequence
                    || !new UUID(nonce[0], nonce[1]).equals(token.session)) return false;
            acceptedSequence = token.sequence;
            return true;
        }
        private static void checkIndex(int index) {
            if (index < 0 || index >= WORD_COUNT) throw new IndexOutOfBoundsException(index);
        }
    }
}