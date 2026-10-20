package com.core.siglus;

/** Bounds windows including pending, in-flight, attached and permanently retained owners. */
final class SiglusLeasePool {
    private final int capacity;
    private int occupied;
    SiglusLeasePool(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
    }
    synchronized Lease acquire(Runnable release) {
        if (occupied >= capacity) return null;
        occupied++;
        return new Lease(release);
    }
    final class Lease {
        private int references = 1;
        private final Runnable release;
        private Lease(Runnable release) { this.release = release; }
        synchronized boolean retain() {
            if (references == 0) return false;
            references++;
            return true;
        }
        void release() {
            synchronized (this) {
                if (references == 0) return;
                if (--references != 0) return;
            }
            // Release the native resources before making the slot reusable.
            release.run();
            synchronized (SiglusLeasePool.this) { occupied--; }
        }
    }
}
