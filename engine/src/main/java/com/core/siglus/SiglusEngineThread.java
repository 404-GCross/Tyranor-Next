package com.core.siglus;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sole owner of every live-handle JNI call. BufferQueue backpressure may block
 * step(), but never the UI or the independent GLES consumer. A silent step is
 * allowed: the unmodified ABI does not promise that each step presents a frame.
 */
final class SiglusEngineThread {
    interface Listener {
        void onCreated(boolean success, boolean cancelled, int windowGeneration);
        void onConnected(long request, boolean success, int windowGeneration);
        void onStepped(int status, boolean imeNeeded, int[] imeArea);
        void onBlocked();
        void onFailure(RuntimeException failure);
    }

    private static final String TAG = "SiglusEngineThread";
    private static final long OPERATION_WARNING_MS = 10000L;
    private static final AtomicBoolean PROCESS_OWNER = new AtomicBoolean();
    // Failed native destruction must retain the producer's consumer as well;
    // releasing it on a different thread would invalidate a still-live handle.
    private static final List<SiglusEngineThread> RETAINED_OWNERS = new ArrayList<>();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("Siglus-engine");
    private final Handler worker;
    private final SiglusStepDispatch steps = new SiglusStepDispatch();
    private final SiglusLatestControl<SiglusLatestControl.Command> controls = new SiglusLatestControl<>();
    private volatile Listener listener;
    private volatile boolean closed, queueWarning, messageboxReplyPending;
    private volatile double lastPointerX, lastPointerY;
    private volatile long handle, connectionRequest, activeOperation;
    private long nextOperation;
    // Owner-thread state. Hold Java Surface wrappers until the corresponding
    // native window is replaced/destroyed, including delayed close after step().
    private Surface boundSurface, boundSource;
    private Runnable releaseAfterStop;

    SiglusEngineThread(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        if (!PROCESS_OWNER.compareAndSet(false, true))
            throw new IllegalStateException("Previous Siglus owner has not stopped");
        try {
            thread.start();
            worker = new Handler(thread.getLooper());
        } catch (RuntimeException | Error failure) {
            try { thread.quitSafely(); }
            finally { PROCESS_OWNER.set(false); }
            throw failure;
        }
    }

    boolean matchesHandle(long value) { return !closed && value != 0L && handle == value; }

    void create(Surface surface, int width, int height, double scale, String directory,
                String language, int generation) {
        final Surface target = snapshot(surface);
        if (target == null) {
            created(false, surface == null || !surface.isValid(), generation);
            return;
        }
        postControl(() -> {
            try {
                if (!target.isValid()) {
                    created(false, true, generation);
                    return;
                }
                // Key recovery during create() may legitimately be slow. Do not
                // time it out or create a second owner while it is still running.
                NativeSiglus.nativeInitContext(context);
                if (language != null && !language.trim().isEmpty())
                    NativeSiglus.nativeSetLanguage(language.trim());
                handle = NativeSiglus.create(target, width, height, scale, directory);
                if (handle != 0L) {
                    boundSurface = target;
                    boundSource = surface;
                    NativeSiglus.setNativeMessageboxCallback(handle);
                }
                created(handle != 0L, false, generation);
            } catch (RuntimeException failure) {
                Log.e(TAG, "Engine creation failed", failure);
                created(false, false, generation);
            } finally { if (boundSurface != target) target.release(); }
        }, target::release);
    }

    private void created(boolean success, boolean cancelled, int generation) {
        deliver(callback -> callback.onCreated(success, cancelled, generation));
    }

    long setSurface(Surface surface, int width, int height, int generation) {
        final long request = ++connectionRequest;
        final Surface target = snapshot(surface);
        if (target == null) {
            deliver(callback -> callback.onConnected(request, false, generation));
            return request;
        }
        postControl(() -> {
            try {
                if (handle == 0L || request != connectionRequest || !target.isValid()) {
                    deliver(callback -> callback.onConnected(request, false, generation));
                    return;
                }
                runNative(() -> {
                    boolean success;
                    if (boundSource == surface) {
                        // Private producer dimensions remain stable until this
                        // ordered control operation; no Android window is passed.
                        NativeSiglus.resize(handle, width, height);
                        success = true;
                    } else {
                        success = NativeSiglus.setSurface(handle, target, width, height);
                        if (success) {
                            Surface previous = boundSurface;
                            boundSurface = target;
                            boundSource = surface;
                            if (previous != null) previous.release();
                        }
                    }
                    final boolean connected = success;
                    deliver(callback -> callback.onConnected(request, connected, generation));
                });
            } finally { if (boundSurface != target) target.release(); }
        }, () -> { target.release(); deliver(callback -> callback.onConnected(request, false, generation)); });
        return request;
    }

    private static Surface snapshot(Surface source) {
        // Only proxy-owned private producers enter this class. Parcel copying
        // keeps each pending control request separate; the consumer stays alive
        // until ordered native replacement/destruction acknowledges detachment.
        Parcel parcel = Parcel.obtain();
        try {
            if (source == null || !source.isValid()) return null;
            source.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Surface result = Surface.CREATOR.createFromParcel(parcel);
            if (result.isValid()) return result;
            result.release();
        } catch (RuntimeException failure) { Log.w(TAG, "Window snapshot unavailable", failure); }
        finally { parcel.recycle(); }
        return null;
    }

    boolean step(int dtMs) {
        if (!steps.reserve()) return false;
        if (!worker.post(() -> {
            if (!steps.begin()) return;
            try {
                if (closed || handle == 0L) return;
                runNative(() -> {
                    int status = NativeSiglus.step(handle, dtMs);
                    int[] area = new int[4];
                    boolean needed = status == 0 && NativeSiglus.imeArea(handle, area);
                    deliver(callback -> callback.onStepped(status, needed, area));
                });
            } finally { steps.complete(); }
        })) {
            steps.close();
            return false;
        }
        return true;
    }

    void touch(int phase, double x, double y) {
        lastPointerX = x; lastPointerY = y;
        postInput(() -> NativeSiglus.touch(handle, phase, x, y));
    }
    void keyEvent(int keyCode, String text, boolean repeat) {
        // Preserve the original mobile key_event semantics; no extra key ABI.
        postInput(() -> NativeSiglus.keyEvent(handle, keyCode, text, repeat));
    }
    void textInput(String text) { postInput(() -> NativeSiglus.textInput(handle, text)); }
    void imePreedit(String text, int start, int end) {
        postInput(() -> NativeSiglus.imePreedit(handle, text, start, end));
    }
    void submitMessagebox(long engineHandle, long requestId, long value) {
        if (closed || messageboxReplyPending) return;
        // Siglus exposes one native modal request at a time. Its reply has a
        // reserved queue slot and cannot be dropped by ordinary input saturation.
        messageboxReplyPending = true;
        if (!worker.postAtFrontOfQueue(() -> {
            try {
                if (!closed && handle == engineHandle)
                    runNative(() -> NativeSiglus.submitMessageboxResult(handle, requestId, value));
            } finally { messageboxReplyPending = false; }
        })) messageboxReplyPending = false;
    }

    private void postInput(Runnable operation) {
        if (!steps.inputAvailable()) return;
        if (!postCommand(() -> { if (handle != 0L) runNative(operation); })) requestInputReset();
    }

    private void requestInputReset() {
        if (!steps.reserveInputReset()) return;
        // One reserved reset follows every input already admitted. Suppress new
        // inputs until it drains so a later DOWN cannot outlive a coalesced UP.
        if (!worker.post(() -> {
            try {
                if (!closed && handle != 0L) runNative(() -> {
                    NativeSiglus.touch(handle, 3, lastPointerX, lastPointerY);
                    NativeSiglus.imePreedit(handle, null, -1, -1);
                });
            } finally { steps.completeInputReset(); }
        })) steps.completeInputReset();
    }

    private void postControl(Runnable operation, Runnable discarded) {
        SiglusLatestControl.Command command = new SiglusLatestControl.Command() {
            @Override public void run() { if (closed) discard(); else operation.run(); }
            @Override public void discard() { if (discarded != null) discarded.run(); }
        };
        if (controls.offer(command) && !worker.post(this::drainControl)) controls.close();
    }

    private void drainControl() {
        SiglusLatestControl.Command command = controls.take();
        try { if (command != null) command.run(); }
        finally {
            if (controls.complete() && !worker.post(this::drainControl)) controls.close();
        }
    }

    private boolean postCommand(Runnable operation) { return postCommand(operation, null); }

    private boolean postCommand(Runnable operation, Runnable discarded) {
        if (!steps.reserveCommand()) {
            if (discarded != null) discarded.run();
            if (!closed && !queueWarning) {
                queueWarning = true;
                deliver(Listener::onBlocked);
            }
            return false;
        }
        if (!worker.post(() -> {
            try {
                if (!closed) operation.run();
                else if (discarded != null) discarded.run();
            }
            finally { steps.completeCommand(); queueWarning = false; }
        })) {
            steps.completeCommand();
            if (discarded != null) discarded.run();
            return false;
        }
        return true;
    }

    private void runNative(Runnable operation) {
        final long request = ++nextOperation;
        activeOperation = request;
        Runnable warning = () -> {
            if (!closed && activeOperation == request) {
                Log.w(TAG, "Native owner is still busy; retaining its handle and producer");
                Listener callback = listener;
                if (callback != null) callback.onBlocked();
            }
        };
        main.postDelayed(warning, OPERATION_WARNING_MS);
        try { operation.run(); }
        catch (RuntimeException failure) { deliver(callback -> callback.onFailure(failure)); }
        finally {
            activeOperation = 0L;
            main.removeCallbacks(warning);
        }
    }

    private interface Delivery { void accept(Listener listener); }
    private void deliver(Delivery delivery) {
        main.post(() -> {
            Listener callback = listener;
            if (!closed && callback != null) delivery.accept(callback);
        });
    }

    /** Stops admission immediately; destruction stays behind any running JNI call. */
    void close(Runnable afterStopped) {
        if (closed) return;
        closed = true;
        listener = null;
        steps.close();
        controls.close();
        connectionRequest++;
        releaseAfterStop = afterStopped;
        worker.post(() -> {
            try {
                if (handle != 0L) NativeSiglus.destroy(handle);
                handle = 0L;
                if (boundSurface != null) boundSurface.release();
                boundSurface = boundSource = null;
                PROCESS_OWNER.set(false);
                Runnable completion = releaseAfterStop;
                releaseAfterStop = null;
                if (completion != null) main.post(completion);
            } catch (RuntimeException failure) {
                Log.e(TAG, "Native destruction failed; retaining owner resources", failure);
                synchronized (RETAINED_OWNERS) { RETAINED_OWNERS.add(this); }
            } finally { thread.quitSafely(); }
        });
    }
}
