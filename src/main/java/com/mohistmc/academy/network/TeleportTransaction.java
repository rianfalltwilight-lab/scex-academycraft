package com.mohistmc.academy.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Small all-or-nothing journal used by Location Teleport after all destinations are prevalidated. */
public final class TeleportTransaction {
    private TeleportTransaction() {}

    public static <T> boolean commit(List<T> planned, Predicate<T> moveAndVerify,
                                     Consumer<T> rollback, Runnable successLinks, Runnable rollbackLinks) {
        List<T> committed = new ArrayList<>(planned.size());
        try {
            for (T entry : planned) {
                committed.add(entry);
                if (!moveAndVerify.test(entry)) throw new TeleportRejectedException();
            }
            successLinks.run();
            return true;
        } catch (RuntimeException rejected) {
            for (int index = committed.size() - 1; index >= 0; index--) {
                try { rollback.accept(committed.get(index)); } catch (RuntimeException ignored) { }
            }
            try { rollbackLinks.run(); } catch (RuntimeException ignored) { }
            return false;
        }
    }

    private static final class TeleportRejectedException extends RuntimeException { }
}
