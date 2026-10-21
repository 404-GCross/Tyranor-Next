package com.core.siglus;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Exercises the same admission, ownership and intent models used by both real owners. */
public final class SiglusWindowControlTest {
    private static final class Command implements SiglusLatestControl.Command {
        final Runnable operation, release;
        boolean ran, discarded;
        Command(Runnable operation, Runnable release) { this.operation = operation; this.release = release; }
        @Override public void run() { ran = true; try { operation.run(); } finally { release.run(); } }
        @Override public void discard() { discarded = true; release.run(); }
    }

    @Test public void inputSaturationCannotDropTheIndependentProducerControl() {
        SiglusStepDispatch inputs = new SiglusStepDispatch();
        for (int i = 0; i < 64; i++) assertTrue(inputs.reserveCommand());
        assertFalse(inputs.reserveCommand());
        SiglusLatestControl<Command> controls = new SiglusLatestControl<>();
        AtomicInteger connected = new AtomicInteger();
        Command handoff = new Command(connected::incrementAndGet, () -> { });
        assertTrue(controls.offer(handoff));
        controls.take().run();
        assertFalse(controls.complete());
        assertEquals(1, connected.get());
        assertFalse(handoff.discarded);
        // Completion does not depend on another UI/Surface event or free input slot.
        assertFalse(inputs.reserveCommand());
    }

    @Test public void newWindowPendingDuringOldAttachRunsAfterLateCompletion() {
        SiglusLatestControl<Command> controls = new SiglusLatestControl<>();
        SiglusOutputState output = new SiglusOutputState();
        AtomicInteger ready = new AtomicInteger();
        long windowA = output.attach();
        Command first = new Command(() -> { if (output.current(windowA)) ready.set(1); }, () -> { });
        assertTrue(controls.offer(first));
        Command inFlight = controls.take(); // The old owner is still inside EGL.
        output.detach(false);
        long windowB = output.attach();
        Command newest = new Command(() -> { if (output.current(windowB)) ready.set(2); }, () -> { });
        assertFalse(controls.offer(newest)); // One drain, one pending latest request.
        inFlight.run(); // A's acknowledgement arrives after B was made available.
        assertEquals(0, ready.get());
        assertTrue(controls.complete());
        controls.take().run();
        assertFalse(controls.complete());
        assertEquals(2, ready.get());
    }

    @Test public void repeatedBackgroundDetachDoesNotCancelTheProducerHandoffBarrier() {
        SiglusOutputState output = new SiglusOutputState();
        SiglusLatestControl<Command> controls = new SiglusLatestControl<>();
        output.attach();
        long barrierGeneration = output.detach(true);
        AtomicInteger detached = new AtomicInteger();
        Command barrier = new Command(detached::incrementAndGet, () -> { });
        assertTrue(controls.offer(barrier));
        // Activity pause and TextureView destruction ask for the same desired-null
        // state. Production skips these duplicate controls, preserving the barrier.
        assertEquals(0L, output.detach(false));
        assertEquals(0L, output.detach(false));
        assertEquals(barrierGeneration, output.generation);
        controls.take().run();
        assertFalse(controls.complete());
        assertEquals(1, detached.get());
        assertFalse(barrier.discarded);
    }

    @Test public void uiDestructionDoesNotReleaseTheInFlightOrAttachedWindow() {
        SiglusLeasePool pool = new SiglusLeasePool(1);
        AtomicInteger released = new AtomicInteger();
        SiglusLeasePool.Lease lease = pool.acquire(released::incrementAndGet);
        assertNotNull(lease);
        assertTrue(lease.retain()); // Queued/in-flight command ownership.
        lease.release(); // TextureView onDestroyed withdraws its UI reference.
        assertEquals(0, released.get());
        assertNull(pool.acquire(() -> { }));
        assertTrue(lease.retain()); // EGL attachment has a separate reference.
        lease.release(); // Completion releases only the command's reference.
        assertEquals(0, released.get());
        lease.release(); // Confirmed EGL detach finally releases the window.
        assertEquals(1, released.get());
        assertFalse(lease.retain());
        assertNotNull(pool.acquire(() -> { }));
    }

    @Test public void failedDetachRetainsItsSlotWithoutConsumingTheUiReference() {
        SiglusLeasePool pool = new SiglusLeasePool(1);
        AtomicInteger released = new AtomicInteger();
        SiglusLeasePool.Lease lease = pool.acquire(released::incrementAndGet);
        assertTrue(lease.retain()); // Output command.
        assertTrue(lease.retain()); // EGL owner, possibly retained until process exit.
        lease.release(); // Failed command ends; EGL destruction has not succeeded.
        assertEquals(0, released.get());
        lease.release(); // A later UI destruction still leaves the EGL reference.
        assertNull(pool.acquire(() -> { }));
        assertEquals(0, released.get());
        lease.release(); // Only a proven owner cleanup may return this slot.
        assertEquals(1, released.get());
    }

    @Test public void replacingQueuedWindowsReleasesOnlyTheSupersededCommand() {
        SiglusLeasePool pool = new SiglusLeasePool(2);
        AtomicInteger releasedA = new AtomicInteger(), releasedB = new AtomicInteger();
        SiglusLeasePool.Lease a = pool.acquire(releasedA::incrementAndGet);
        SiglusLeasePool.Lease b = pool.acquire(releasedB::incrementAndGet);
        SiglusLatestControl<Command> controls = new SiglusLatestControl<>();
        assertTrue(a.retain());
        Command old = new Command(() -> { }, a::release);
        assertTrue(controls.offer(old));
        a.release(); // UI A destroyed; queued command still owns A.
        assertTrue(b.retain());
        Command current = new Command(() -> { }, b::release);
        assertFalse(controls.offer(current));
        assertTrue(old.discarded);
        assertEquals(1, releasedA.get());
        assertEquals(0, releasedB.get());
        controls.take().run();
        assertFalse(controls.complete());
        assertEquals(0, releasedB.get()); // UI B is still live.
        b.release();
        assertEquals(1, releasedB.get());
    }

    @Test public void closeDropsPendingResourcesButNeverRevokesTheInFlightLease() {
        SiglusLatestControl<Command> controls = new SiglusLatestControl<>();
        AtomicInteger inFlightReleased = new AtomicInteger(), pendingReleased = new AtomicInteger();
        Command active = new Command(() -> { }, inFlightReleased::incrementAndGet);
        Command pending = new Command(() -> { }, pendingReleased::incrementAndGet);
        assertTrue(controls.offer(active));
        Command inFlight = controls.take();
        assertFalse(controls.offer(pending));
        controls.close();
        assertEquals(1, pendingReleased.get());
        assertEquals(0, inFlightReleased.get());
        inFlight.run();
        assertEquals(1, inFlightReleased.get());
        assertFalse(controls.complete());
        Command rejected = new Command(() -> { }, pendingReleased::incrementAndGet);
        assertFalse(controls.offer(rejected));
        assertTrue(rejected.discarded);
        assertEquals(2, pendingReleased.get());
    }

    @Test public void permanentlyRetainedWindowsCannotExceedTheProcessBound() {
        SiglusLeasePool pool = new SiglusLeasePool(4);
        for (int i = 0; i < 4; i++) assertNotNull(pool.acquire(() -> { }));
        for (int i = 0; i < 100; i++) assertNull(pool.acquire(() -> { }));
    }

    @Test public void sameWindowResizeRejectsOldReadyAndFirstFrameAcknowledgements() {
        SiglusOutputState output = new SiglusOutputState();
        long oldSize = output.attach();
        long newSize = output.attach();
        assertFalse(output.current(oldSize));
        assertTrue(output.current(newSize));
        output.detach(false);
        assertFalse(output.current(newSize));
    }
}
