package com.core.siglus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SiglusSurfaceBindingTest {
    @Test public void preparationFailureResizesTheCurrentDirectWindowWithoutReconnecting() {
        SiglusSurfaceBinding binding = new SiglusSurfaceBinding();
        binding.surfaceCreated();
        binding.directConnected();
        assertFalse(binding.requiresConnection(false));
        assertTrue(binding.requiresConnection(true));
    }

    @Test public void aRecreatedWindowRequiresAcknowledgementBeforeItCanResume() {
        SiglusSurfaceBinding binding = new SiglusSurfaceBinding();
        binding.surfaceCreated();
        binding.directConnected();
        binding.surfaceDestroyed();
        binding.surfaceCreated();
        assertTrue(binding.requiresConnection(false));
        assertFalse(binding.isDirectCurrent());
        // Posting or failing an async connection does not claim ownership.
        assertTrue(binding.requiresConnection(false));
        binding.directConnected();
        assertTrue(binding.isDirectCurrent());
        assertFalse(binding.requiresConnection(false));
    }

    @Test public void timeoutOrShutdownCannotResumeAnUnboundWindow() {
        SiglusSurfaceBinding binding = new SiglusSurfaceBinding();
        assertFalse(binding.isDirectCurrent());
        binding.surfaceCreated();
        assertFalse(binding.isDirectCurrent());
        binding.directConnected();
        binding.clear();
        assertTrue(binding.requiresConnection(false));
        assertFalse(binding.isDirectCurrent());
    }
}
