package com.mohistmc.academy.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread fixed-window limiter for inexpensive UI requests and sync amplification control. */
public final class PayloadRateLimiter {
    private record Key(UUID player, String channel) {}
    private record Window(long started, int used) {}
    private static final Map<Key, Window> WINDOWS = new HashMap<>();

    private PayloadRateLimiter() {}

    public static boolean allow(UUID player, String channel, long now, long windowTicks, int permits) {
        if (windowTicks < 1 || permits < 1) return false;
        Key key = new Key(player, channel);
        Window old = WINDOWS.get(key);
        if (old == null || now < old.started || now - old.started >= windowTicks) {
            WINDOWS.put(key, new Window(now, 1));
            return true;
        }
        if (old.used >= permits) return false;
        WINDOWS.put(key, new Window(old.started, old.used + 1));
        return true;
    }

    public static void forget(UUID player) {
        WINDOWS.keySet().removeIf(key -> key.player.equals(player));
    }
    public static void clearAll() { WINDOWS.clear(); }
}
