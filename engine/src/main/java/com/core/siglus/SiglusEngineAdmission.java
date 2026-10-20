package com.core.siglus;

/** One native owner per process across both direct and filtered presentation. */
final class SiglusEngineAdmission {
    static final SiglusEngineAdmission PROCESS = new SiglusEngineAdmission();
    private Permit active;
    synchronized Permit acquire() {
        if (active != null) return null;
        active = new Permit();
        return active;
    }
    final class Permit {
        void release() {
            synchronized (SiglusEngineAdmission.this) {
                // An old completion cannot free a subsequent owner's permit.
                if (active == this) active = null;
            }
        }
    }
}
