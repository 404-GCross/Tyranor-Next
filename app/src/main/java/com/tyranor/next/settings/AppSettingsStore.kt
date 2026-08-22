package com.tyranor.next.settings

import android.content.Context

/**
 * 应用设置存储层：与引擎无关的应用级偏好（如主题色）。
 * 使用独立 prefs 文件 app_settings，避免混入引擎进程读取的 yukihub_prefs。
 */
object AppSettingsStore {

    const val KEY_THEME_COLOR = "theme_color"

    /** 默认主题色：#307DEF，与 theme/Color.kt 的 Blue40 一致。 */
    const val DEFAULT_THEME_COLOR = "#307DEF"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** 当前主题色 HEX（#RRGGBB）。 */
    fun getThemeColorHex(c: Context): String =
        prefs(c).getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR

    fun setThemeColorHex(c: Context, hex: String) =
        prefs(c).edit().putString(KEY_THEME_COLOR, hex).apply()
}
