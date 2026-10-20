package com.core.siglus;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** App-owned TextureView window; unlike SurfaceHolder it may outlive its UI callback. */
final class SiglusWindowLease {
    private static final SiglusLeasePool POOL = new SiglusLeasePool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<Runnable>> OBSERVERS = new ArrayList<>();
    final SurfaceTexture texture;
    final Surface surface;
    private final SiglusLeasePool.Lease ownership;

    private SiglusWindowLease(SurfaceTexture texture, Surface surface, SiglusLeasePool.Lease ownership) {
        this.texture = texture; this.surface = surface; this.ownership = ownership;
    }
    static SiglusWindowLease acquire(SurfaceTexture texture) {
        final Surface[] window = new Surface[1];
        SiglusLeasePool.Lease slot = POOL.acquire(() -> {
            if (window[0] != null) { window[0].release(); texture.release(); }
        });
        if (slot == null) return null;
        try {
            window[0] = new Surface(texture);
            return new SiglusWindowLease(texture, window[0], slot);
        } catch (RuntimeException failure) {
            // No client lease escaped; TextureView still owns this texture.
            slot.release();
            throw failure;
        }
    }
    boolean retain() { return ownership.retain(); }
    void release() {
        ownership.release();
        synchronized (OBSERVERS) {
            OBSERVERS.removeIf(reference -> reference.get() == null);
            for (WeakReference<Runnable> reference : OBSERVERS) {
                Runnable callback = reference.get();
                if (callback != null) { MAIN.removeCallbacks(callback); MAIN.post(callback); }
            }
        }
    }
    static void observeCapacity(Runnable callback) {
        synchronized (OBSERVERS) { OBSERVERS.add(new WeakReference<>(callback)); }
    }
    static void stopObservingCapacity(Runnable callback) {
        MAIN.removeCallbacks(callback);
        synchronized (OBSERVERS) {
            OBSERVERS.removeIf(reference -> reference.get() == null || reference.get() == callback);
        }
    }
}
