package com.core.engine;

import android.app.Activity;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.Map;
import java.util.WeakHashMap;

public final class DoubleBackExit {
    private static final long EXIT_WINDOW_MS = 2_000L;
    private static final Map<Activity, Long> LAST_BACK_TIME = new WeakHashMap<>();
    private static final Map<Activity, Boolean> SUPPRESS_BACK_UP = new WeakHashMap<>();

    private DoubleBackExit() { }

    public interface ExitAction {
        void exit();
    }

    public static boolean shouldExit(Activity activity) {
        return shouldExit(activity, true);
    }

    /**
     * @param feedback 首次按压是否弹"再按一次返回退出游戏"提示。返回键已透传给游戏的
     *                 引擎（krkr 双内核）应传 false，避免每次游戏内返回都弹 toast；
     *                 首按无可见效果的引擎（Tyrano/Artemis/SDL2/ONS）保持提示。
     */
    public static boolean shouldExit(Activity activity, boolean feedback) {
        if (activity == null) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        Long last = LAST_BACK_TIME.get(activity);
        if (last != null && now - last <= EXIT_WINDOW_MS) {
            LAST_BACK_TIME.remove(activity);
            return true;
        }
        LAST_BACK_TIME.put(activity, now);
        if (feedback) Toast.makeText(activity, "再按一次返回退出游戏", Toast.LENGTH_SHORT).show();
        return false;
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

    public static void clear(Activity activity) {
        if (activity != null) {
            LAST_BACK_TIME.remove(activity);
            SUPPRESS_BACK_UP.remove(activity);
        }
    }
}
