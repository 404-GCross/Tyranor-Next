package com.core.siglus;

/** Shared output intent; a duplicate detach must not cancel a handoff barrier. */
final class SiglusOutputState {
    volatile boolean wanted;
    volatile long generation;
    long attach() { wanted = true; return ++generation; }
    long detach(boolean barrier) {
        if (!wanted && !barrier) return 0L;
        wanted = false;
        return ++generation;
    }
    boolean current(long request) { return wanted && request == generation; }
}
