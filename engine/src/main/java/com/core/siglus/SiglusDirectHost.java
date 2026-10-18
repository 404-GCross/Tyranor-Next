package com.core.siglus;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import java.util.ArrayList;
import java.util.List;

/**
 * Upstream SurfaceView path: all live-handle JNI completes on the main thread,
 * so a SurfaceHolder destruction callback cannot overtake a native render call.
 * No host GLES context, TextureView, private producer or worker is constructed.
 */
final class SiglusDirectHost implements SiglusHost {
    private static final String TAG = "SiglusDirectHost";
    private static final List<SiglusDirectHost> RETAINED_OWNERS = new ArrayList<>();
    private final Context context;
    private final SiglusEngineAdmission.Permit processOwner;
    private Listener listener;
    private long handle;
    private boolean closed;

    SiglusDirectHost(Context context, Listener listener) {
        requireMainThread();
        this.context = context.getApplicationContext();
        this.listener = listener;
        processOwner = SiglusEngineAdmission.PROCESS.acquire();
        if (processOwner == null) throw new IllegalStateException("Previous Siglus owner has not stopped");
    }

    void create(Surface display, int width, int height, double scale, String directory,
                String language, int generation) {
        requireMainThread();
        if (closed || handle != 0L) return;
        boolean success = false;
        try {
            if (display != null && display.isValid()) {
                NativeSiglus.nativeInitContext(context);
                if (language != null && !language.trim().isEmpty()) NativeSiglus.nativeSetLanguage(language.trim());
                handle = NativeSiglus.create(display, width, height, scale, directory);
                if (handle != 0L) {
                    NativeSiglus.setNativeMessageboxCallback(handle);
                    success = true;
                }
            }
        } catch (RuntimeException failure) { Log.e(TAG, "Engine creation failed", failure); }
        // Direct callbacks are synchronous; Activity's direct branch does not
        // compare a request ID assigned only after this call returns.
        if (listener != null) listener.onCreated(success, false, generation);
    }

    void connect(Surface display, int width, int height, int generation) {
        requireMainThread();
        if (closed || handle == 0L) return;
        boolean success = false;
        try {
            if (display != null && display.isValid()) success = NativeSiglus.setSurface(handle, display, width, height);
        } catch (RuntimeException failure) { Log.e(TAG, "Display connection failed", failure); }
        if (listener != null) listener.onConnected(0L, success, generation);
    }

    void resize(int width, int height) { runNative(() -> NativeSiglus.resize(handle, width, height)); }

    @Override public boolean matchesHandle(long value) {
        requireMainThread();
        return !closed && value != 0L && handle == value;
    }

    @Override public boolean step(int dtMs) {
        requireMainThread();
        if (closed || handle == 0L) return false;
        runNative(() -> {
            int status = NativeSiglus.step(handle, dtMs);
            int[] area = new int[4];
            boolean needed = status == 0 && NativeSiglus.imeArea(handle, area);
            if (listener != null) listener.onStepped(status, needed, area);
        });
        return true;
    }

    @Override public void touch(int phase, double x, double y) {
        runNative(() -> NativeSiglus.touch(handle, phase, x, y));
    }
    @Override public void keyEvent(int keyCode, String text, boolean repeat) {
        runNative(() -> NativeSiglus.keyEvent(handle, keyCode, text, repeat));
    }
    @Override public void textInput(String text) { runNative(() -> NativeSiglus.textInput(handle, text)); }
    @Override public void imePreedit(String text, int start, int end) {
        runNative(() -> NativeSiglus.imePreedit(handle, text, start, end));
    }
    @Override public void submitMessagebox(long engineHandle, long requestId, long value) {
        requireMainThread();
        if (matchesHandle(engineHandle))
            runNative(() -> NativeSiglus.submitMessageboxResult(handle, requestId, value));
    }

    private void runNative(Runnable operation) {
        requireMainThread();
        if (closed || handle == 0L) return;
        try { operation.run(); }
        catch (RuntimeException failure) { if (listener != null) listener.onFailure(failure); }
    }

    @Override public void close(Runnable afterStopped) {
        requireMainThread();
        if (closed) return;
        closed = true;
        listener = null;
        try {
            if (handle != 0L) NativeSiglus.destroy(handle);
            handle = 0L;
            processOwner.release();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Native destruction failed; retaining process admission", failure);
            RETAINED_OWNERS.add(this);
            return;
        }
        if (afterStopped != null) afterStopped.run();
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Direct Siglus JNI requires the main thread");
    }
}
