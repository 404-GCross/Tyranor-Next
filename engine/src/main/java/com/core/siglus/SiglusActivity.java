package com.core.siglus;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.core.engine.EnginePrefs;
import com.core.engine.R;
import com.core.engine.EngineUiText;
import com.core.engine.LaunchContract;
import com.core.gl.GraphicsFilterChain;
import com.core.gl.ArtemisPerformanceOverlay;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * SiglusEngine（siglus_rs）宿主 Activity。
 *
 * <p>Java 持有 SurfaceView 生命周期与 Choreographer 时钟，独立线程串行驱动 Rust 引擎
 * 经 {@code siglus_android_*} C ABI 被逐步驱动。差异点：游戏路径/语言/回写键取自
 * {@link LaunchContract} extras，消息框复用 engine 既有文案资源，IME 与键事件按
 * TyranorNext 契约转发。
 */
public final class SiglusActivity extends AppCompatActivity implements
        SurfaceHolder.Callback,
        Choreographer.FrameCallback,
        View.OnTouchListener,
        SiglusTextInputView.Listener,
        NativeSiglus.MessageboxListener {

    private static final String TAG = "SiglusActivity";

    /** 引擎侧取消/返回键（Windows VK Escape）。 */
    private static final int VK_ESCAPE = 0x1B;
    private static final int VK_ENTER = 0x0D;
    private static final int VK_SPACE = 0x20;
    private static final int VK_BACK = 0x08;
    private static final int VK_DELETE = 0x2E;
    private static final int VK_TAB = 0x09;
    private static final int VK_SHIFT = 0x10;
    private static final int VK_CONTROL = 0x11;
    private static final int VK_ALT = 0x12;
    private static final int VK_META = 0x5B;

    private static final long BACK_EXIT_WINDOW_MS = 2000L;
    private static final int MAX_FRAME_DT_MS = 250;
    private static final long FALLBACK_RETRY_MS = 1000L;
    private static final int MAX_FALLBACK_RETRIES = 5;

    private FrameLayout rootLayout;
    private SurfaceView surfaceView;
    private SiglusTextInputView textInputView;
    private View loadingOverlay;

    private SiglusEngineThread engine;
    private boolean engineReady, engineCreating, windowAvailable;
    private int surfaceGeneration;
    private long surfaceConnectionRequest;
    private int surfaceConnectionKind;
    private static final int CONNECT_DIRECT = 0, CONNECT_PROXY = 1, CONNECT_FALLBACK = 2;
    private boolean running = false;
    private volatile boolean surfaceReady = false;
    private long lastFrameNs = 0L;
    private boolean loadingOverlayVisible = true;
    private boolean imeVisible = false;

    private String gameRoot;
    private String gameDirName;
    private String pathHash;

    private long lastBackMs = 0L;
    private boolean resumed, destroying, faulted, proxyPreparing, proxyAttached, proxyFailed;
    private boolean fallbackPending;
    private int fallbackRetries;
    private boolean pointerActive;
    private double lastTouchX, lastTouchY;
    private final SiglusSurfaceBinding surfaceBinding = new SiglusSurfaceBinding();
    private SiglusGraphicsProxy graphicsProxy;
    private ArtemisPerformanceOverlay graphicsOverlay;
    private final Set<Long> messageboxRequests = new HashSet<>();
    private Thread stageSizeWorker;
    private int stageSizeRequest;
    private final Runnable fallbackRetry = () -> {
        if (resumed && !destroying && graphicsProxy != null && proxyFailed)
            restoreDirect(graphicsProxy);
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(buildContentView());

        gameRoot = firstNonEmpty(
                getIntent().getStringExtra(LaunchContract.PATH),
                getIntent().getStringExtra(LaunchContract.GAME_PATH),
                getIntent().getStringExtra(LaunchContract.PROJECT_ROOT),
                getIntent().getStringExtra(LaunchContract.GAME_DIR));
        if (gameRoot == null || gameRoot.trim().isEmpty()) {
            Toast.makeText(this, uiString(R.string.engine_siglus_missing_game_dir),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        gameDirName = new File(gameRoot).getName();
        pathHash = getIntent().getStringExtra(LaunchContract.SIGLUS_PATH_HASH);

        NativeSiglus.setMessageboxListener(this);
        try {
            engine = new SiglusEngineThread(this, new SiglusEngineThread.Listener() {
                @Override public void onCreated(boolean success, boolean cancelled, int generation) {
                    engineCreating = false;
                    if (cancelled) {
                        SurfaceHolder current = surfaceView.getHolder();
                        if (windowAvailable && current.getSurface().isValid()) ensureEngine(current);
                        return;
                    }
                    if (!success) {
                        Toast.makeText(SiglusActivity.this, uiString(R.string.engine_siglus_init_failed),
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    engineReady = true;
                    SurfaceHolder current = surfaceView.getHolder();
                    if (generation == surfaceGeneration && windowAvailable && current.getSurface().isValid()) {
                        surfaceBinding.directConnected();
                        surfaceReady = true;
                        engine.resize(surfaceWidth(current), surfaceHeight(current), surfaceGeneration);
                    } else if (windowAvailable && current.getSurface().isValid()) connectDirect(current, CONNECT_DIRECT);
                    prepareGraphics();
                    writeBackGameTitleAsync();
                    maybeStartFrameLoop();
                }
                @Override public void onConnected(long request, boolean success, int generation) {
                    completeSurfaceConnection(request, success, generation);
                }
                @Override public void onStepped(int status, boolean imeNeeded, int[] area) {
                    if (status > 0) { finish(); return; }
                    if (status < 0) { faulted = true; stopFrameLoop(); return; }
                    if (graphicsProxy == null) hideLoadingOverlay();
                    if (resumed) updateImeState(imeNeeded, area);
                }
                @Override public void onBlocked() {
                    // The owner may be inside a slow VM/producer call. Queue a
                    // recovery, never destroy its handle from the UI thread.
                    if (graphicsProxy != null) restoreDirect(graphicsProxy);
                }
                @Override public void onFailure(RuntimeException failure) {
                    Log.e(TAG, "Engine owner operation failed", failure);
                    faulted = true;
                    stopFrameLoop();
                }
            });
        } catch (RuntimeException failure) {
            Log.e(TAG, "Engine owner unavailable", failure);
            finish();
            return;
        }

        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener(this);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.setKeepScreenOn(true);

        applyImmersive();
        installBackHandling();
    }

    private View buildContentView() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        rootLayout.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        textInputView = new SiglusTextInputView(this, this);
        textInputView.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(1, 1);
        inputParams.gravity = Gravity.TOP | Gravity.START;
        rootLayout.addView(textInputView, inputParams);

        loadingOverlay = buildLoadingOverlay();
        rootLayout.addView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return rootLayout;
    }

    private View buildLoadingOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.BLACK);
        ProgressBar spinner = new ProgressBar(this);
        overlay.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        TextView label = new TextView(this);
        label.setText(uiString(R.string.engine_starting_game));
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(14.0f);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        labelParams.topMargin = (int) (72 * getResources().getDisplayMetrics().density);
        overlay.addView(label, labelParams);
        return overlay;
    }

    private void hideLoadingOverlay() {
        if (loadingOverlayVisible && loadingOverlay != null) {
            loadingOverlayVisible = false;
            rootLayout.removeView(loadingOverlay);
            loadingOverlay = null;
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // singleInstance：游戏运行中再次收到启动请求时不叠加新引擎实例。
        Toast.makeText(this, uiString(R.string.engine_another_game_running),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        fallbackRetries = 0; // A new foreground session permits another bounded recovery attempt.
        applyImmersive();
        if (graphicsProxy != null && proxyFailed) {
            restoreDirect(graphicsProxy);
        } else if (graphicsProxy != null && proxyAttached && !proxyPreparing && surfaceView.getHolder().getSurface().isValid()) {
            cancelPointer();
            surfaceReady = false;
            graphicsProxy.attachOutput(surfaceView.getHolder().getSurface(),
                    surfaceWidth(surfaceView.getHolder()), surfaceHeight(surfaceView.getHolder()));
        }
        maybeStartFrameLoop();
    }

    @Override
    protected void onPause() {
        resumed = false;
        surfaceView.removeCallbacks(fallbackRetry);
        stopFrameLoop();
        cancelPointer();
        if (graphicsOverlay != null) graphicsOverlay.hide();
        if (graphicsProxy != null) graphicsProxy.detachOutput();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroying = true;
        resumed = false;
        stageSizeRequest++;
        if (stageSizeWorker != null) { stageSizeWorker.interrupt(); stageSizeWorker = null; }
        surfaceView.removeCallbacks(fallbackRetry);
        stopFrameLoop();
        if (graphicsOverlay != null) graphicsOverlay.hide();
        hideIme();
        NativeSiglus.setMessageboxListener(null);
        destroyEngine(); // Asynchronous owner destruction releases the producer first.
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    private void applyImmersive() {
        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void installBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = SystemClock.uptimeMillis();
                if (lastBackMs != 0L && now - lastBackMs <= BACK_EXIT_WINDOW_MS) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                lastBackMs = now;
                if (engineReady) {
                    forwardKey(VK_ESCAPE, null, false);
                }
                Toast.makeText(SiglusActivity.this,
                        uiString(R.string.engine_siglus_back_again_to_exit),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- Surface ----------

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        surfaceGeneration++;
        windowAvailable = true;
        surfaceBinding.surfaceCreated();
        if (engine != null) engine.windowState(surfaceGeneration, true);
        surfaceReady = false;
        stopFrameLoop();
        if (graphicsProxy != null && (proxyAttached || proxyFailed)) {
            if (proxyFailed) restoreDirect(graphicsProxy);
            else if (!proxyPreparing)
                graphicsProxy.attachOutput(holder.getSurface(), surfaceWidth(holder), surfaceHeight(holder));
        } else if (engineReady) connectDirect(holder, CONNECT_DIRECT);
        else ensureEngine(holder);
        maybeStartFrameLoop();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        windowAvailable = true;
        if (engine != null) engine.windowState(surfaceGeneration, true);
        if (!engineReady) ensureEngine(holder);
        else if (graphicsProxy != null && (proxyAttached || proxyFailed)) {
            if (proxyFailed) restoreDirect(graphicsProxy);
            else if (!proxyPreparing) {
                cancelPointer();
                surfaceReady = false;
                graphicsProxy.attachOutput(holder.getSurface(), Math.max(1, width), Math.max(1, height));
            }
        } else if (surfaceBinding.isDirectCurrent())
            engine.resize(Math.max(1, width), Math.max(1, height), surfaceGeneration);
        else connectDirect(holder, CONNECT_DIRECT);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceGeneration++;
        windowAvailable = false;
        if (engine != null) engine.windowState(surfaceGeneration, false);
        surfaceBinding.surfaceDestroyed();
        // Keep the VM and producer alive across a lost display window.
        surfaceReady = false;
        surfaceView.removeCallbacks(fallbackRetry);
        stopFrameLoop();
        cancelPointer();
        if (graphicsOverlay != null) graphicsOverlay.hide();
        if (graphicsProxy != null) graphicsProxy.detachOutput();
    }

    private void connectDirect(SurfaceHolder holder, int kind) {
        if (engine == null || !engineReady || !windowAvailable || !holder.getSurface().isValid()) return;
        surfaceReady = false;
        surfaceConnectionKind = kind;
        surfaceConnectionRequest = engine.setSurface(holder.getSurface(), surfaceWidth(holder),
                surfaceHeight(holder), true, surfaceGeneration);
    }

    private void completeSurfaceConnection(long request, boolean success, int generation) {
        if (destroying || request != surfaceConnectionRequest) return;
        if (surfaceConnectionKind == CONNECT_PROXY) {
            SiglusGraphicsProxy proxy = graphicsProxy;
            if (proxy == null || proxyFailed) return;
            if (!success) { restoreDirect(proxy); return; }
            proxyPreparing = false;
            proxy.activate();
            surfaceReady = false;
            SurfaceHolder current = surfaceView.getHolder();
            if (resumed && current.getSurface().isValid())
                proxy.attachOutput(current.getSurface(), surfaceWidth(current), surfaceHeight(current));
            graphicsOverlay = new ArtemisPerformanceOverlay(this, proxy);
            if (resumed) graphicsOverlay.show();
        } else {
            if (surfaceConnectionKind == CONNECT_FALLBACK) fallbackPending = false;
            if (!success) {
                if (surfaceConnectionKind == CONNECT_FALLBACK) scheduleFallbackRetry();
                return;
            }
            surfaceReady = generation == surfaceGeneration && windowAvailable
                    && surfaceView.getHolder().getSurface().isValid();
            if (surfaceReady) surfaceBinding.directConnected();
            else surfaceBinding.clear();
            if (surfaceConnectionKind == CONNECT_FALLBACK && graphicsProxy != null)
                finishDirectRestore(graphicsProxy);
        }
        maybeStartFrameLoop();
    }

    private int surfaceWidth(SurfaceHolder holder) {
        Rect frame = holder.getSurfaceFrame();
        int width = frame != null ? frame.width() : surfaceView.getWidth();
        return Math.max(1, width);
    }

    private int surfaceHeight(SurfaceHolder holder) {
        Rect frame = holder.getSurfaceFrame();
        int height = frame != null ? frame.height() : surfaceView.getHeight();
        return Math.max(1, height);
    }

    private void ensureEngine(@NonNull SurfaceHolder holder) {
        if (engine == null || engineReady || engineCreating || destroying || !windowAvailable) return;
        engineCreating = true;
        String language = getIntent().getStringExtra(LaunchContract.SIGLUS_LANGUAGE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        engine.create(holder.getSurface(), surfaceWidth(holder), surfaceHeight(holder),
                metrics.density, gameRoot, language, surfaceGeneration);
    }

    private void prepareGraphics() {
        GraphicsFilterChain chain = GraphicsFilterChain.parse(
                getIntent().getStringExtra(LaunchContract.GRAPHICS_FILTER_CHAIN));
        if (!getIntent().getBooleanExtra(LaunchContract.GRAPHICS_FILTER_ENABLED, false)
                || chain == null || !chain.hasActive()) return;
        // create() may have generated key.toml. Decode only after it succeeds,
        // off the engine/UI thread; the running VM keeps direct output meanwhile.
        final File directory = new File(gameRoot);
        final SiglusEngineThread engineOwner = engine;
        final int request = ++stageSizeRequest;
        final Handler main = new Handler(Looper.getMainLooper());
        final WeakReference<SiglusActivity> owner = new WeakReference<>(this);
        stageSizeWorker = new Thread(() -> {
            SiglusStageSize.Size result = null;
            try { result = SiglusStageSize.read(directory); }
            catch (RuntimeException failure) { Log.w(TAG, "Stage configuration unavailable", failure); }
            if (Thread.currentThread().isInterrupted()) return;
            final SiglusStageSize.Size size = result;
            main.post(() -> {
                SiglusActivity activity = owner.get();
                if (activity == null || activity.destroying || activity.faulted || activity.isFinishing()
                        || activity.isDestroyed() || activity.engine != engineOwner || !activity.engineReady
                        || activity.stageSizeRequest != request) return;
                activity.stageSizeWorker = null;
                if (size == null) {
                    Log.w(TAG, "No trusted configured stage size; keeping direct output");
                    return;
                }
                activity.prepareGraphics(chain, size, engineOwner);
            });
        }, "Siglus-stage-size");
        try { stageSizeWorker.start(); }
        catch (RuntimeException failure) {
            stageSizeWorker = null;
            Log.w(TAG, "Stage configuration worker unavailable; keeping direct output", failure);
        }
    }

    private void prepareGraphics(GraphicsFilterChain chain, SiglusStageSize.Size size, SiglusEngineThread engineOwner) {
        SiglusGraphicsProxy candidate;
        try {
            candidate = new SiglusGraphicsProxy(this, chain, size.width, size.height,
                getIntent().getBooleanExtra(LaunchContract.GRAPHICS_FILTER_ANALYZER, false),
                new SiglusGraphicsProxy.Listener() {
                    @Override public void onPrepared(SiglusGraphicsProxy proxy, Surface producer) {
                        if (destroying || graphicsProxy != proxy || engine != engineOwner) return;
                        // Existing ABI: producer pixels match the decrypted stage.
                        // Actual buffer dimensions are not queried through a new ABI.
                        proxyAttached = true; // May reach Rust before the main-thread acknowledgement.
                        surfaceConnectionKind = CONNECT_PROXY;
                        surfaceConnectionRequest = engine.setSurface(producer, size.width, size.height,
                                false, surfaceGeneration);
                    }
                    @Override public void onPreparationTimeout(SiglusGraphicsProxy proxy) {
                        if (destroying || graphicsProxy != proxy || engine != engineOwner
                                || !proxyPreparing || proxyAttached) return;
                        // The proxy closed before this callback and never delivered a producer.
                        // Rust keeps direct output; only a current holder generation may resume.
                        graphicsProxy = null;
                        proxyPreparing = false;
                        proxyFailed = false;
                        SurfaceHolder current = surfaceView.getHolder();
                        surfaceReady = windowAvailable && current.getSurface().isValid() && surfaceBinding.isDirectCurrent();
                        Log.w(TAG, "Graphics preparation timed out; keeping Rust direct output");
                        Toast.makeText(SiglusActivity.this, uiString(R.string.graphics_siglus_fallback),
                                Toast.LENGTH_LONG).show();
                        maybeStartFrameLoop();
                    }
                    @Override public void onOutputReady(SiglusGraphicsProxy proxy) {
                        if (destroying || graphicsProxy != proxy || engine != engineOwner || !proxyAttached) return;
                        surfaceReady = surfaceView.getHolder().getSurface().isValid();
                        if (resumed && graphicsOverlay != null) graphicsOverlay.show();
                        maybeStartFrameLoop();
                    }
                    @Override public void onFirstFrame(SiglusGraphicsProxy proxy) {
                        if (!destroying && graphicsProxy == proxy) hideLoadingOverlay();
                    }
                    @Override public void onFailure(SiglusGraphicsProxy proxy, RuntimeException failure) {
                        if (!destroying && graphicsProxy == proxy && engine == engineOwner) restoreDirect(proxy);
                    }
                });
        } catch (RuntimeException failure) {
            Log.w(TAG, "Surface proxy unavailable; keeping direct output", failure);
            return;
        }
        stopFrameLoop();
        cancelPointer();
        proxyPreparing = true;
        surfaceReady = false;
        graphicsProxy = candidate;
        candidate.prepare();
    }

    private void restoreDirect(SiglusGraphicsProxy proxy) {
        if (graphicsProxy != proxy || destroying || fallbackPending) return;
        fallbackPending = true;
        surfaceView.removeCallbacks(fallbackRetry);
        stopFrameLoop();
        cancelPointer();
        if (graphicsOverlay != null) { graphicsOverlay.hide(); graphicsOverlay = null; }
        proxyFailed = true;
        proxyPreparing = false;
        surfaceReady = false;
        // Disconnect the borrowed display before asking the single native owner
        // to reconnect it. Never release the producer while step/setSurface runs.
        proxy.detachOutput(detached -> {
            if (graphicsProxy != proxy || destroying) return;
            if (engine == null || !engineReady) { fallbackPending = false; return; }
            SurfaceHolder holder = surfaceView.getHolder();
            if (!detached || !resumed || !windowAvailable || !holder.getSurface().isValid()) {
                fallbackPending = false;
                scheduleFallbackRetry();
                return;
            }
            if (!surfaceBinding.requiresConnection(proxyAttached)) {
                engine.resize(surfaceWidth(holder), surfaceHeight(holder), surfaceGeneration);
                fallbackPending = false;
                surfaceReady = true;
                finishDirectRestore(proxy);
                maybeStartFrameLoop();
            } else connectDirect(holder, CONNECT_FALLBACK);
        });
    }

    private void finishDirectRestore(SiglusGraphicsProxy proxy) {
        surfaceView.removeCallbacks(fallbackRetry);
        fallbackRetries = 0;
        graphicsProxy = null;
        proxyAttached = proxyFailed = proxyPreparing = false;
        proxy.release();
        Toast.makeText(this, uiString(R.string.graphics_siglus_fallback), Toast.LENGTH_LONG).show();
    }

    private void scheduleFallbackRetry() {
        surfaceView.removeCallbacks(fallbackRetry);
        if (!resumed || destroying || !windowAvailable || !surfaceView.getHolder().getSurface().isValid()) return;
        if (fallbackRetries >= MAX_FALLBACK_RETRIES) {
            // Keep the VM and producer alive. A late owner-thread completion or
            // a new lifecycle/Surface callback can resume without polling forever.
            if (fallbackRetries == MAX_FALLBACK_RETRIES) {
                Log.e(TAG, "Direct fallback retries exhausted; awaiting a new Surface or owner completion");
                fallbackRetries++;
            }
            return;
        }
        if (fallbackRetries == 0) Log.w(TAG, "Direct fallback pending; retrying with bounded backoff");
        surfaceView.postDelayed(fallbackRetry, FALLBACK_RETRY_MS << fallbackRetries++);
    }

    private void cancelPointer() {
        if (pointerActive && engineReady && engine != null) engine.touch(3, lastTouchX, lastTouchY);
        pointerActive = false;
    }

    private void destroyEngine() {
        surfaceBinding.clear();
        SiglusGraphicsProxy producerOwner = graphicsProxy;
        graphicsProxy = null;
        SiglusEngineThread owner = engine;
        engine = null;
        engineReady = engineCreating = false;
        if (producerOwner != null) {
            producerOwner.stopObserving();
            producerOwner.detachOutput();
        }
        if (owner != null) owner.close(() -> {
            if (producerOwner != null) producerOwner.release();
        });
        else if (producerOwner != null) producerOwner.release();
    }

    // ---------- 帧循环 ----------

    private void maybeStartFrameLoop() {
        if (!running && canStep()) {
            running = true;
            lastFrameNs = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private boolean canStep() {
        return resumed && !destroying && !faulted && engineReady && engine != null && surfaceReady
                && !proxyPreparing && !proxyFailed;
    }

    private void stopFrameLoop() {
        if (running) {
            running = false;
            lastFrameNs = 0L;
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running || !canStep()) {
            stopFrameLoop();
            return;
        }
        if (!messageboxRequests.isEmpty()) {
            lastFrameNs = 0L;
            Choreographer.getInstance().postFrameCallback(this);
            return;
        }
        if (graphicsProxy != null && graphicsProxy.hasFailed()) {
            restoreDirect(graphicsProxy);
            return;
        }
        if (lastFrameNs == 0L) {
            lastFrameNs = frameTimeNanos;
        }
        long dtNs = frameTimeNanos - lastFrameNs;
        lastFrameNs = frameTimeNanos;
        int dtMs = (int) (dtNs / 1_000_000L);
        if (dtMs < 0) {
            dtMs = 0;
        }
        if (dtMs > MAX_FRAME_DT_MS) {
            dtMs = MAX_FRAME_DT_MS;
        }

        // At most one queued/in-flight owner step. A step without a produced
        // buffer still completes, so load/script Frame boundaries can advance.
        engine.step(dtMs);
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ---------- 输入 ----------

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (!engineReady || event == null) {
            return false;
        }
        int phase;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                phase = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                phase = 1;
                break;
            case MotionEvent.ACTION_UP:
                phase = 2;
                break;
            case MotionEvent.ACTION_CANCEL:
                phase = 3;
                break;
            default:
                return false;
        }
        if (!canStep()) {
            if (phase == 2 || phase == 3) cancelPointer();
            return true;
        }
        double x = event.getX(), y = event.getY();
        if (graphicsProxy != null) {
            SiglusViewport v = graphicsProxy.viewport();
            if (v == null) return true;
            x *= (double) v.surfaceWidth / Math.max(1, surfaceView.getWidth());
            y *= (double) v.surfaceHeight / Math.max(1, surfaceView.getHeight());
            if (phase == 0 && !v.contains(x, y)) { pointerActive = false; return true; }
            x = v.sourceX(x); y = v.sourceY(y);
        }
        if (phase == 0) pointerActive = true;
        else if (!pointerActive) return true;
        hideIme();
        lastTouchX = x; lastTouchY = y;
        engine.touch(phase, x, y);
        if (phase == 2 || phase == 3) pointerActive = false;
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (engineReady && event != null) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_MULTIPLE) {
                int keyCode = event.getKeyCode();
                int vk = vkCodeFor(keyCode);
                boolean repeat = event.getRepeatCount() > 0;
                if (vk != 0) {
                    forwardKey(vk, null, repeat);
                    return true;
                }
                int unicode = event.getUnicodeChar();
                if (unicode != 0) {
                    forwardKey(0, String.valueOf((char) unicode), repeat);
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP && vkCodeFor(event.getKeyCode()) != 0) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void forwardKey(int vk, @Nullable String text, boolean repeat) {
        if (!engineReady) {
            return;
        }
        engine.keyEvent(vk, text, repeat);
    }

    /** Android KeyEvent → Windows 虚拟键码（与引擎 host.rs::vm_key_from_platform_code 对齐）。 */
    private static int vkCodeFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
                return VK_ESCAPE;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return VK_ENTER;
            case KeyEvent.KEYCODE_SPACE:
                return VK_SPACE;
            case KeyEvent.KEYCODE_DEL:
                return VK_BACK;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return VK_DELETE;
            case KeyEvent.KEYCODE_TAB:
                return VK_TAB;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return VK_SHIFT;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return VK_CONTROL;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return VK_ALT;
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return VK_META;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 0x25;
            case KeyEvent.KEYCODE_DPAD_UP:
                return 0x26;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 0x27;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return 0x28;
            case KeyEvent.KEYCODE_MOVE_HOME:
                return 0x24;
            case KeyEvent.KEYCODE_MOVE_END:
                return 0x23;
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return 0x41 + (keyCode - KeyEvent.KEYCODE_A);
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return 0x30 + (keyCode - KeyEvent.KEYCODE_0);
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return 0x70 + (keyCode - KeyEvent.KEYCODE_F1);
        }
        return 0;
    }

    // ---------- IME ----------

    private void updateImeState(boolean needed, int[] area) {
        if (!engineReady || engine == null || !canStep()) return;
        if (needed) {
            if (!imeVisible) {
                textInputView.requestFocus();
                // Mirror the working KRKR host: post the request so the view is
                // attached/focused before showing, and use flag 0 instead of
                // SHOW_IMPLICIT (which the system may silently decline).
                textInputView.post(() -> {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(textInputView, 0);
                    }
                });
                imeVisible = true;
            }
            SiglusViewport v = graphicsProxy == null ? null : graphicsProxy.viewport();
            double x0 = area[0], y0 = area[1], x1 = (double) area[0] + area[2], y1 = (double) area[1] + area[3];
            if (v != null) {
                x0 = v.screenX(x0); y0 = v.screenY(y0); x1 = v.screenX(x1); y1 = v.screenY(y1);
            }
            double scaleX = (double) surfaceView.getWidth() / surfaceWidth(surfaceView.getHolder());
            double scaleY = (double) surfaceView.getHeight() / surfaceHeight(surfaceView.getHolder());
            textInputView.updateCursorAnchor(new Rect(
                    (int) Math.round(x0 * scaleX) + surfaceView.getLeft(),
                    (int) Math.round(y0 * scaleY) + surfaceView.getTop(),
                    (int) Math.round(x1 * scaleX) + surfaceView.getLeft(),
                    (int) Math.round(y1 * scaleY) + surfaceView.getTop()));
        } else if (imeVisible) {
            hideIme();
        }
    }

    private void hideIme() {
        if (!imeVisible) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(textInputView.getWindowToken(), 0);
        }
        textInputView.clearFocus();
        imeVisible = false;
    }

    @Override
    public void onCommitText(String text) {
        if (engineReady && text != null && !text.isEmpty()) {
            engine.textInput(text);
        }
    }

    @Override
    public void onPreedit(String text, int cursorStart, int cursorEnd) {
        if (!engineReady) {
            return;
        }
        engine.imePreedit(text, cursorStart, cursorEnd);
    }

    @Override
    public void onImeKeyEvent(int keyCode, boolean down) {
        if (!engineReady || !down) {
            return;
        }
        int vk = vkCodeFor(keyCode);
        if (vk != 0) {
            forwardKey(vk, null, false);
        }
    }

    // ---------- 消息框 ----------

    @Override
    public void onMessagebox(long engineHandle, long requestId, int kind, String title, String message) {
        runOnUiThread(() -> showMessagebox(engineHandle, requestId, kind, title, message));
    }

    private void showMessagebox(long engineHandle, long requestId, int kind, String title, String message) {
        if (engine == null || !engine.matchesHandle(engineHandle) || destroying) return;
        if (isFinishing() || isDestroyed()) {
            submitMessagebox(engineHandle, requestId, fallbackMessageboxValue(kind));
            return;
        }
        if (!messageboxRequests.add(requestId)) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(EngineUiText.localizeCommonDialogText(this, title));
        builder.setMessage(message == null ? "" : message);
        builder.setCancelable(true);
        switch (kind) {
            case 1: // OK / CANCEL
                builder.setPositiveButton(uiString(R.string.engine_ok),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 0));
                builder.setNegativeButton(uiString(R.string.engine_cancel),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 1));
                builder.setOnCancelListener(dialog ->
                        submitMessagebox(engineHandle, requestId, 1));
                break;
            case 2: // YES / NO
                builder.setPositiveButton(uiString(R.string.engine_yes),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 0));
                builder.setNegativeButton(uiString(R.string.engine_no),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 1));
                builder.setOnCancelListener(dialog ->
                        submitMessagebox(engineHandle, requestId, 1));
                break;
            case 3: // YES / NO / CANCEL
                builder.setPositiveButton(uiString(R.string.engine_yes),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 0));
                builder.setNegativeButton(uiString(R.string.engine_no),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 1));
                builder.setNeutralButton(uiString(R.string.engine_cancel),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 2));
                builder.setOnCancelListener(dialog ->
                        submitMessagebox(engineHandle, requestId, 2));
                break;
            case 0: // OK
            default:
                builder.setPositiveButton(uiString(R.string.engine_ok),
                        (dialog, which) -> submitMessagebox(engineHandle, requestId, 0));
                builder.setOnCancelListener(dialog ->
                        submitMessagebox(engineHandle, requestId, 0));
                break;
        }
        try { builder.show(); }
        catch (RuntimeException failure) {
            Log.w(TAG, "Native message box unavailable", failure);
            submitMessagebox(engineHandle, requestId, fallbackMessageboxValue(kind));
        }
    }

    private void submitMessagebox(long engineHandle, long requestId, long value) {
        if (!destroying && engine != null && engine.matchesHandle(engineHandle)) {
            engine.submitMessagebox(engineHandle, requestId, value);
            messageboxRequests.remove(requestId);
        }
    }

    private static long fallbackMessageboxValue(int kind) {
        switch (kind) {
            case 1:
            case 2:
                return 1;
            case 3:
                return 2;
            case 0:
            default:
                return 0;
        }
    }

    // ---------- 标题回写 ----------

    /**
     * 引擎启动成功后读取 Gameexe GAMENAME 并写入共享 prefs，App 侧在下一次入库时条件导入
     * （仅覆盖仍等于目录名的自动标题）。引擎进程无法访问 Room，故走 prefs 回写协议。
     */
    private void writeBackGameTitleAsync() {
        if (pathHash == null || pathHash.trim().isEmpty() || gameRoot == null) {
            return;
        }
        final String dir = gameRoot;
        final String hash = pathHash.trim();
        final String defaultTitle = gameDirName;
        new Thread(() -> {
            try {
                String name = NativeSiglus.gameNameFromDir(dir);
                if (name == null) {
                    return;
                }
                String title = name.trim();
                if (title.isEmpty() || title.equals(defaultTitle)) {
                    return;
                }
                SharedPreferences prefs = getSharedPreferences(EnginePrefs.APP_PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(EnginePrefs.KEY_SIGLUS_TITLE_PREFIX + hash, title).apply();
                Log.i(TAG, "siglus title written back: " + title);
            } catch (Throwable t) {
                Log.w(TAG, "title write-back failed", t);
            }
        }, "siglus-title").start();
    }

    // ---------- 工具 ----------

    private String uiString(int resourceId) {
        return EngineUiText.get(this, resourceId);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
