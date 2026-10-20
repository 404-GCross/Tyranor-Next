package com.core.siglus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SiglusHostPolicyTest {
    @Test public void disabledSettingKeepsTheUpstreamHostRegardlessOfStoredChain() {
        assertNull(SiglusHostPolicy.requestedChain(false, "v1:s,lanczos"));
        assertNull(SiglusHostPolicy.requestedChain(false, "invalid"));
        assertNull(SiglusHostPolicy.requestedChain(false, null));
    }

    @Test public void invalidOrMissingChainDoesNotConstructTheFilterHost() {
        assertNull(SiglusHostPolicy.requestedChain(true, null));
        assertNull(SiglusHostPolicy.requestedChain(true, "v3:s"));
        assertNull(SiglusHostPolicy.requestedChain(true, "v1:unknown,s"));
    }

    @Test public void emptyOrEntirelyPausedChainKeepsTheUpstreamHost() {
        assertNull(SiglusHostPolicy.requestedChain(true, "v1:"));
        assertNull(SiglusHostPolicy.requestedChain(true, "v1:!s,!lanczos"));
    }

    @Test public void enabledActiveChainSelectsTheFilterHostWithoutChangingItsOrder() {
        assertEquals("v1:s,!lanczos,s", SiglusHostPolicy.requestedChain(true, "v1:s,!lanczos,s").encode());
    }
}
