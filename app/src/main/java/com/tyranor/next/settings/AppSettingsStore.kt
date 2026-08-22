package com.tyranor.next.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * 应用设置存储层：与引擎无关的应用级偏好（如主题色、导航栏样式）。
 * 使用独立 prefs 文件 app_settings，避免混入引擎进程读取的 yukihub_prefs。
 */
object AppSettingsStore {

    const val KEY_THEME_COLOR = "theme_color"
    const val KEY_NAV_STYLE = "nav_style"
    const val KEY_SCAN_DEPTH = "scan_depth"
    const val KEY_THEME_MODE = "theme_mode"

    /** 默认主题色：#307DEF，与 theme/Color.kt 的 Blue40 一致。 */
    const val DEFAULT_THEME_COLOR = "#307DEF"

    /** 外观模式：浅色。 */
    const val THEME_MODE_LIGHT = "light"

    /** 外观模式：深色。 */
    const val THEME_MODE_DARK = "dark"

    /** 文件夹扫描深度默认值（层级，1..5）。 */
    const val DEFAULT_SCAN_DEPTH = 3

    /** 底部导航栏样式：默认。 */
    const val NAV_STYLE_DEFAULT = "default"

    /** 底部导航栏样式：圆角液态玻璃（流体玻璃）。 */
    const val NAV_STYLE_LIQUID_GLASS = "liquid_glass"

    /** 导航栏样式内存态：随设置页切换即时广播，供 MainScreen 重组切换样式。 */
    val navStyleState: MutableState<String> = mutableStateOf(NAV_STYLE_DEFAULT)

    /** 首次组合时从持久化加载导航栏样式到内存态（幂等，重复调用仅重新读一次）。 */
    fun initNavStyle(c: Context) {
        navStyleState.value = getNavStyle(c)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** 当前主题色 HEX（#RRGGBB）。 */
    fun getThemeColorHex(c: Context): String =
        prefs(c).getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR

    fun setThemeColorHex(c: Context, hex: String) =
        prefs(c).edit().putString(KEY_THEME_COLOR, hex).apply()

    /** 当前底部导航栏样式（默认 / 液态玻璃）。 */
    fun getNavStyle(c: Context): String =
        prefs(c).getString(KEY_NAV_STYLE, NAV_STYLE_DEFAULT) ?: NAV_STYLE_DEFAULT

    fun setNavStyle(c: Context, style: String) {
        prefs(c).edit().putString(KEY_NAV_STYLE, style).apply()
        navStyleState.value = style
    }

    /** 文件夹扫描深度（1..5，默认 3）。 */
    fun getScanDepth(c: Context): Int =
        prefs(c).getInt(KEY_SCAN_DEPTH, DEFAULT_SCAN_DEPTH).coerceIn(1, 5)

    fun setScanDepth(c: Context, depth: Int) =
        prefs(c).edit().putInt(KEY_SCAN_DEPTH, depth.coerceIn(1, 5)).apply()

    /** 外观模式（浅色/深色）。 */
    fun getThemeMode(c: Context): String =
        prefs(c).getString(KEY_THEME_MODE, THEME_MODE_LIGHT) ?: THEME_MODE_LIGHT

    fun setThemeMode(c: Context, mode: String) =
        prefs(c).edit().putString(KEY_THEME_MODE, mode).apply()
}
