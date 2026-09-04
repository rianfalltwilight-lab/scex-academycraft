package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeleportTransactionTest {
    @Test void cancellationRollsBackAlreadyCommittedEntitiesInReverseOrder() {
        List<String> log = new ArrayList<>();
        boolean result = TeleportTransaction.commit(List.of(1,2,3), value -> {
            log.add("move" + value); return value != 3;
        }, value -> log.add("rollback" + value), () -> log.add("links-success"), () -> log.add("links-rollback"));
        assertFalse(result);
        assertEquals(List.of("move1","move2","move3","rollback3","rollback2","rollback1","links-rollback"), log);
    }

    @Test void crossDimensionSuccessRestoresLinksOnlyAfterEveryMoveVerifies() {
        List<String> log = new ArrayList<>();
        boolean result = TeleportTransaction.commit(List.of("vehicle","rider"), value -> {
            log.add("move-" + value); return true;
        }, value -> log.add("rollback-" + value), () -> log.add("restore-riding"), () -> log.add("restore-origin"));
        assertTrue(result);
        assertEquals(List.of("move-vehicle","move-rider","restore-riding"), log);
    }

    @Test void exceptionFromTeleportHookUsesSameRollbackPath() {
        List<Integer> rolledBack = new ArrayList<>();
        assertFalse(TeleportTransaction.commit(List.of(1,2), value -> {
            if (value == 2) throw new IllegalStateException("plugin cancellation"); return true;
        }, rolledBack::add, () -> fail("must not commit links"), () -> {}));
        assertEquals(List.of(2,1), rolledBack);
    }
}
