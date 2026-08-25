package com.core.engine;

import android.app.Activity;
import android.os.Build;
import android.view.KeyEvent;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 双击返回退出引擎的共享计时工具。
 *
 * 两种使用模式：
 * - 消费型：web 类引擎（Tyrano 等无游戏内返回键语义），在 Activity.dispatchKeyEvent 里用
 *   {@link #dispatchBackKey} 首次按下即拦截并弹提示，窗口内第二次按下执行退出动作。
 * - 透传型：krkr/SDL 类引擎，返回键要传给游戏；在真正消费按键的 View/native 入口处调
 *   {@link #shouldExit}——首次按下弹提示后放行给引擎，窗口内第二次按下返回 true 由调用方
 *   执行退出且不再透传。Activity 层不得拦截返回键，否则事件到不了透传入口。
 */
public final class DoubleBackExit {
    private static final long EXIT_WINDOW_MS = 2_000L;
    private static final Map<Activity, Long> LAST_BACK_TIME = new WeakHashMap<>();
    private static final Map<Activity, Boolean> SUPPRESS_BACK_UP = new WeakHashMap<>();

    private DoubleBackExit() { }

    public interface ExitAction {
        void exit();
    }

    public static boolean shouldExit(Activity activity) {
        if (activity == null) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        Long last = LAST_BACK_TIME.get(activity);
        if (last != null && now - last <= EXIT_WINDOW_MS) {
            LAST_BACK_TIME.remove(activity);
            return true;
        }
        LAST_BACK_TIME.put(activity, now);
        if (backPromptEnabled(activity)) {
            Toast.makeText(activity, "再按一次返回退出游戏", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    /** 首次按下是否弹提示（设置页可关；prefs 读取失败时保持默认开启）。 */
    private static boolean backPromptEnabled(Activity activity) {
        try {
            return activity.getSharedPreferences(com.core.engine.EnginePrefs.APP_PREFS, android.content.Context.MODE_PRIVATE)
                    .getBoolean(com.core.engine.EnginePrefs.KEY_BACK_EXIT_PROMPT, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void handleBack(Activity activity, ExitAction action) {
        if (shouldExit(activity) && action != null) action.exit();
    }

    public static boolean dispatchBackKey(Activity activity, KeyEvent event, ExitAction action) {
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                SUPPRESS_BACK_UP.put(activity, true);
                handleBack(activity, action);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && SUPPRESS_BACK_UP.remove(activity) != null) {
            return true;
        }
        return event.getAction() == KeyEvent.ACTION_MULTIPLE;
    }

    public static Object registerPredictiveBack(Activity activity, ExitAction action) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null;
        OnBackInvokedCallback callback = () -> handleBack(activity, action);
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback);
        return callback;
    }

    public static void unregisterPredictiveBack(Activity activity, Object callback) {
        if (activity == null || callback == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) callback);
    }

    public static void clear(Activity activity) {
        if (activity != null) {
            LAST_BACK_TIME.remove(activity);
            SUPPRESS_BACK_UP.remove(activity);
        }
    }
}
