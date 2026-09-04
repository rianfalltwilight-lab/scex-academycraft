package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OneShotSessionLedgerTest {
    @Test void validateDoesNotConsumeButCommitIsOneShot() {
        var ledger = new OneShotSessionLedger<String>();
        UUID owner = UUID.randomUUID();
        UUID nonce = ledger.issue(owner, "normal@0,64,0", 100);
        assertTrue(ledger.validate(owner, nonce, "normal@0,64,0", 50));
        assertTrue(ledger.validate(owner, nonce, "normal@0,64,0", 50));
        assertTrue(ledger.commit(owner, nonce, "normal@0,64,0", 50));
        assertFalse(ledger.commit(owner, nonce, "normal@0,64,0", 50), "replay must fail");
    }

    @Test void rejectsWrongOwnerNonceContextAndExpiredSession() {
        var ledger = new OneShotSessionLedger<String>();
        UUID owner = UUID.randomUUID();
        UUID nonce = ledger.issue(owner, "advanced@overworld", 100);
        assertFalse(ledger.validate(UUID.randomUUID(), nonce, "advanced@overworld", 99));
        assertFalse(ledger.validate(owner, UUID.randomUUID(), "advanced@overworld", 99));
        assertFalse(ledger.validate(owner, nonce, "portable@overworld", 99));
        assertTrue(ledger.validate(owner, nonce, "advanced@overworld", 100));
        assertFalse(ledger.validate(owner, nonce, "advanced@overworld", 101));
    }

    @Test void staleCloseCannotRevokeReplacementScreen() {
        var ledger = new OneShotSessionLedger<String>();
        UUID owner = UUID.randomUUID();
        UUID stale = ledger.issue(owner, "normal", 100);
        UUID current = ledger.issue(owner, "advanced", 200);
        ledger.clear(owner, stale);
        assertTrue(ledger.validate(owner, current, "advanced", 150));
        ledger.clear(owner, current);
        assertFalse(ledger.validate(owner, current, "advanced", 150));
    }

    @Test void clearExpiredDoesNotClearLiveReplacement() {
        var ledger = new OneShotSessionLedger<String>();
        UUID owner = UUID.randomUUID();
        UUID nonce = ledger.issue(owner, "portable", 200);
        ledger.clearExpired(owner, 200);
        assertTrue(ledger.validate(owner, nonce, "portable", 200));
        ledger.clearExpired(owner, 201);
        assertFalse(ledger.validate(owner, nonce, "portable", 201));
    }
}
