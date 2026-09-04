package com.mohistmc.academy.skill.ability.meltdowner;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LightShieldResourceLedgerTest {
    @Test void twoAbsorptionsDebitCurrentAuthorityExactlyOnceEach() {
        var attachment = new FakeAccount();
        attachment.setCurrentCp(500);
        attachment.setCurrentOverload(10);

        // The ledger receives the current authoritative attachment for every debit.
        assertTrue(LightShieldResourceLedger.tryDebit(attachment, 50, 5));
        assertTrue(LightShieldResourceLedger.tryDebit(attachment, 50, 5));

        assertEquals(400, attachment.getCurrentCp(), 0.0001);
        assertEquals(20, attachment.getCurrentOverload(), 0.0001);
    }

    @Test void failedDebitDoesNotPartiallyMutateResources() {
        var attachment = new FakeAccount();
        attachment.setCurrentCp(49);
        attachment.setCurrentOverload(10);
        assertFalse(LightShieldResourceLedger.tryDebit(attachment, 50, 5));
        assertEquals(49, attachment.getCurrentCp(), 0.0001);
        assertEquals(10, attachment.getCurrentOverload(), 0.0001);
    }

    private static final class FakeAccount implements LightShieldResourceLedger.ResourceAccount {
        private float cp = 2000, overload;
        public boolean isDevMode() { return false; }
        public float getCurrentCp() { return cp; }
        public void setCurrentCp(float value) { cp = Math.max(0, Math.min(value, getMaxCp())); }
        public float getCurrentOverload() { return overload; }
        public void setCurrentOverload(float value) { overload = Math.max(0, Math.min(value, getMaxOverload())); }
        public void addOverload(float value) { setCurrentOverload(overload + value); }
        public float getMaxCp() { return 2000; }
        public float getMaxOverload() { return 500; }
    }
}
