package com.core.siglus;

/** One independent control slot: input saturation cannot discard lifecycle work. */
final class SiglusLatestControl<T extends SiglusLatestControl.Command> {
    interface Command { void run(); void discard(); }
    private T pending;
    private boolean scheduled, closed;

    synchronized boolean offer(T command) {
        if (closed) { command.discard(); return false; }
        if (pending != null) pending.discard();
        pending = command;
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    synchronized T take() { T result = pending; pending = null; return result; }

    synchronized boolean complete() {
        if (pending != null) return true;
        scheduled = false;
        return false;
    }

    synchronized void close() {
        closed = true;
        if (pending != null) { pending.discard(); pending = null; }
        // An in-flight command keeps its own resource until its owner returns.
    }
}
