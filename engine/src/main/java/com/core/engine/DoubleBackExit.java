package com.core.engine;

import android.app.Activity;
import android.widget.Toast;

import java.util.Map;
import java.util.WeakHashMap;

public final class DoubleBackExit {
    private static final long EXIT_WINDOW_MS = 2_000L;
    private static final Map<Activity, Long> LAST_BACK_TIME = new WeakHashMap<>();

    private DoubleBackExit() { }

    public static boolean shouldExit(Activity activity) {
        if (activity == null) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        Long last = LAST_BACK_TIME.get(activity);
        if (last != null && now - last <= EXIT_WINDOW_MS) {
            LAST_BACK_TIME.remove(activity);
            return true;
        }
        LAST_BACK_TIME.put(activity, now);
        Toast.makeText(activity, "再按一次返回退出游戏", Toast.LENGTH_SHORT).show();
        return false;
    }

    public static void clear(Activity activity) {
        if (activity != null) LAST_BACK_TIME.remove(activity);
    }
}
