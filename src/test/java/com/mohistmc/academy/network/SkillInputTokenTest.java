package com.mohistmc.academy.network;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class SkillInputTokenTest {
    @Test void onlyLatestAcceptedInputMayAdvanceTheStream() {
        var id = UUID.randomUUID(); var ledger = new SkillInputToken.Ledger(id);
        assertTrue(ledger.accept(new SkillInputToken(id, 9)));
        assertFalse(ledger.accept(new SkillInputToken(id, 8)));
        assertFalse(ledger.accept(new SkillInputToken(id, 9)));
        assertTrue(ledger.accept(new SkillInputToken(id, 10)));
    }
    @Test void forgedHighSequenceCannotBlockAValidLaterInput() {
        var id = UUID.randomUUID(); var ledger = new SkillInputToken.Ledger(id);
        assertFalse(ledger.accept(new SkillInputToken(UUID.randomUUID(), Long.MAX_VALUE)));
        assertTrue(ledger.accept(new SkillInputToken(id, 1)));
    }
    @Test void nonpositiveAndAbsentInputsCannotAuthorizeActions() {
        var id = UUID.randomUUID(); var ledger = new SkillInputToken.Ledger(id);
        assertFalse(ledger.accept(null));
        assertFalse(ledger.accept(new SkillInputToken(id, 0)));
        assertFalse(ledger.accept(new SkillInputToken(id, -1)));
        assertFalse(ledger.accept(SkillInputToken.missing(1)));
        assertTrue(ledger.accept(new SkillInputToken(id, 1)));
    }
    @Test void aReopenedSessionMayStartAtOneWithoutAcceptingAnOldToken() {
        var old = new SkillInputToken(UUID.randomUUID(), 100);
        var freshId = UUID.randomUUID(); var fresh = new SkillInputToken.Ledger(freshId);
        assertFalse(fresh.accept(old));
        assertTrue(fresh.accept(new SkillInputToken(freshId, 1)));
    }
}