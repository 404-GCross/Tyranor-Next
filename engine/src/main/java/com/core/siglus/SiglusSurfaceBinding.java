package com.core.siglus;

/** Main-thread ownership of the direct SurfaceHolder window across proxy preparation. */
final class SiglusSurfaceBinding {
    private long generation;
    private long directGeneration = -1;

    void surfaceCreated() {
        generation++;
    }

    void surfaceDestroyed() {
        clear();
    }

    void directConnected() {
        directGeneration = generation;
    }

    void clear() {
        directGeneration = -1;
    }

    boolean isDirectCurrent() {
        return directGeneration == generation;
    }

    boolean requiresConnection(boolean proxyMayOwnProducer) {
        return proxyMayOwnProducer || !isDirectCurrent();
    }
}
