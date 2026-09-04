package com.mohistmc.academy.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure-Java one-shot authorization ledger; Minecraft adapters supply canonical context values. */
final class OneShotSessionLedger<C> {
    private record Entry<C>(UUID nonce, C context, long expiresAt) {}
    private final Map<UUID, Entry<C>> entries = new HashMap<>();

    synchronized UUID issue(UUID owner, C context, long expiresAt) {
        UUID nonce = UUID.randomUUID();
        entries.put(owner, new Entry<>(nonce, context, expiresAt));
        return nonce;
    }

    synchronized boolean validate(UUID owner, UUID nonce, C context, long now) {
        Entry<C> entry = entries.get(owner);
        return entry != null && entry.nonce.equals(nonce) && Objects.equals(entry.context, context)
                && now <= entry.expiresAt;
    }

    synchronized boolean commit(UUID owner, UUID nonce, C context, long now) {
        Entry<C> entry = entries.get(owner);
        return validate(owner, nonce, context, now) && entries.remove(owner, entry);
    }

    synchronized void clear(UUID owner, UUID nonce) {
        Entry<C> entry = entries.get(owner);
        if (entry != null && entry.nonce.equals(nonce)) entries.remove(owner, entry);
    }

    synchronized void clear(UUID owner) { entries.remove(owner); }

    synchronized void clearAll() { entries.clear(); }

    synchronized void clearExpired(UUID owner, long now) {
        Entry<C> entry = entries.get(owner);
        if (entry != null && now > entry.expiresAt) entries.remove(owner, entry);
    }
}
