package com.mohistmc.academy.skill.passive;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Non-persistent runtime contexts. Entries are removed by lifecycle events. */
public final class VecDeviationRuntime {
    private static final Map<UUID, Context> ACTIVE = new ConcurrentHashMap<>();
    private VecDeviationRuntime() {}

    public static boolean toggle(UUID playerId) {
        if (ACTIVE.remove(playerId) != null) return false;
        ACTIVE.put(playerId, new Context());
        return true;
    }
    public static boolean isActive(UUID playerId) { return ACTIVE.containsKey(playerId); }
    /** True only on the first encounter during the current activation. */
    public static boolean visit(UUID playerId, UUID entityId) {
        Context context = ACTIVE.get(playerId);
        return context != null && context.visited.add(entityId);
    }
    public static void stop(UUID playerId) { ACTIVE.remove(playerId); }
    public static void clear() { ACTIVE.clear(); }

    private static final class Context {
        private final Set<UUID> visited = ConcurrentHashMap.newKeySet();
    }
}
