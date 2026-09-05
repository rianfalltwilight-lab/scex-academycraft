package com.mohistmc.academy.network;

import java.util.UUID;

/** A start/one-shot input is meaningful in exactly one server-issued gameplay session. */
public record SkillInputToken(UUID session, long sequence) {
    public static final UUID ABSENT = new UUID(0, 0);
    public static SkillInputToken missing(long sequence) { return new SkillInputToken(ABSENT, sequence); }
    public static final class Ledger {
        private final UUID session;
        private long accepted;
        public Ledger(UUID session) { this.session = session; }
        public boolean isFresh(SkillInputToken token) {
            return token != null && session.equals(token.session) && token.sequence > 0 && token.sequence > accepted;
        }
        public boolean accept(SkillInputToken token) {
            if (token == null || !session.equals(token.session) || token.sequence <= 0 || token.sequence <= accepted) return false;
            accepted = token.sequence; return true;
        }
        public UUID session() { return session; }
        public long acceptedSequence() { return accepted; }
    }
}