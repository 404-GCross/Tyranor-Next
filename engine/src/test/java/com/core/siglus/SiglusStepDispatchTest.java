package com.core.siglus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SiglusStepDispatchTest {
    @Test public void onlyOneQueuedOrRunningStepIsAdmitted() {
        SiglusStepDispatch steps = new SiglusStepDispatch();
        assertTrue(steps.reserve());
        assertFalse(steps.reserve());
        assertTrue(steps.begin());
        assertFalse(steps.reserve());
        steps.complete(); // Completion does not require a produced image.
        assertTrue(steps.reserve());
        assertTrue(steps.begin());
        steps.complete();
        assertTrue(steps.reserve());
    }

    @Test public void closeCancelsQueuedWorkWithoutStartingIt() {
        SiglusStepDispatch steps = new SiglusStepDispatch();
        assertTrue(steps.reserve());
        steps.close();
        assertFalse(steps.begin());
        assertFalse(steps.reserve());
        assertFalse(steps.reserveCommand());
    }

    @Test public void lateCompletionCannotReopenAClosedOwner() {
        SiglusStepDispatch steps = new SiglusStepDispatch();
        assertTrue(steps.reserve());
        assertTrue(steps.begin());
        steps.close();
        assertFalse(steps.reserve());
        steps.complete();
        assertFalse(steps.reserve());
        assertFalse(steps.begin());
    }

    @Test public void aStalledOwnerCannotAccumulateUnboundedInputCommands() {
        SiglusStepDispatch steps = new SiglusStepDispatch();
        for (int i = 0; i < 64; i++) assertTrue(steps.reserveCommand());
        assertFalse(steps.reserveCommand());
        steps.completeCommand();
        assertTrue(steps.reserveCommand());
        steps.close();
        steps.completeCommand();
        assertFalse(steps.reserveCommand());
    }

    @Test public void saturationKeepsOneResetSlotAndSuppressesLaterInputsUntilRelease() {
        SiglusStepDispatch steps = new SiglusStepDispatch();
        for (int i = 0; i < 64; i++) assertTrue(steps.reserveCommand());
        assertFalse(steps.reserveCommand());
        assertTrue(steps.reserveInputReset());
        assertFalse(steps.inputAvailable());
        assertFalse(steps.reserveInputReset());
        steps.completeInputReset();
        assertTrue(steps.inputAvailable());
        steps.close();
        assertFalse(steps.reserveInputReset());
        assertFalse(steps.inputAvailable());
    }
}
