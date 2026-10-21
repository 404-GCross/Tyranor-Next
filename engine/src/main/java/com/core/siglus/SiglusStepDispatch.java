package com.core.siglus;

/** Admission shared by the UI clock and the single engine owner. */
final class SiglusStepDispatch {
    private static final int MAX_COMMANDS = 64;
    private boolean pending, running, closed, inputReset;
    private int commands;

    synchronized boolean reserve() {
        if (closed || pending || running) return false;
        pending = true;
        return true;
    }

    synchronized boolean begin() {
        if (closed || !pending) return false;
        pending = false;
        running = true;
        return true;
    }

    synchronized void complete() { running = false; }

    synchronized boolean reserveCommand() {
        if (closed || commands >= MAX_COMMANDS) return false;
        commands++;
        return true;
    }

    synchronized void completeCommand() { if (commands > 0) commands--; }

    synchronized boolean inputAvailable() { return !closed && !inputReset; }

    synchronized boolean reserveInputReset() {
        if (closed || inputReset) return false;
        inputReset = true;
        return true;
    }

    synchronized void completeInputReset() { inputReset = false; }

    synchronized void close() {
        closed = true;
        pending = false;
        // A running JNI call keeps its owner until it returns. close() never
        // turns completion/cancellation into permission for another caller.
    }
}
