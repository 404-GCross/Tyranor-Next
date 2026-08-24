package com.tyranor.next.settings

import android.content.Context
import android.content.res.Configuration
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
    const val KEY_TONE_SWITCH = "tone_switch"
    const val KEY_GAME_SORT = "game_sort"
    const val KEY_MONET_SWITCH = "monet_switch"
    const val KEY_MONET_SOURCE = "monet_source"
    const val KEY_PALETTE_STYLE = "palette_style"

    /** 默认主题色：#307DEF，与 theme/Color.kt 的 Blue40 一致。 */
    const val DEFAULT_THEME_COLOR = "#307DEF"

    /** 外观模式：浅色。 */
    const val THEME_MODE_LIGHT = "light"

    /** 外观模式：深色。 */
    const val THEME_MODE_DARK = "dark"

    /** 外观模式：跟随系统深/浅色。 */
    const val THEME_MODE_SYSTEM = "system"

    /** 动态取色来源：跟随系统壁纸。 */
    const val MONET_SOURCE_SYSTEM = "system"

    /** 动态取色来源：游戏封面。 */
    const val MONET_SOURCE_COVER = "cover"

    /** 配色风格存储键值：与 theme/MonetColorScheme.kt 的 PaletteStyle 枚举名对应。 */
    const val PALETTE_STYLE_TONAL_SPOT = "tonal_spot"
    const val PALETTE_STYLE_NEUTRAL = "neutral"
    const val PALETTE_STYLE_VIBRANT = "vibrant"
    const val PALETTE_STYLE_EXPRESSIVE = "expressive"
    const val PALETTE_STYLE_RAINBOW = "rainbow"
    const val PALETTE_STYLE_FRUIT_SALAD = "fruit_salad"
    const val PALETTE_STYLE_MONOCHROME = "monochrome"
    const val PALETTE_STYLE_FIDELITY = "fidelity"
    const val PALETTE_STYLE_CONTENT = "content"

    /** 文件夹扫描深度默认值（层级，1..5）。 */
    const val DEFAULT_SCAN_DEPTH = 3

    /** 游戏排序：按标题字母/字符顺序。 */
    const val GAME_SORT_ALPHA = "alpha"

    /** 游戏排序：按标题中 【】/[] 标签内容分组。 */
    const val GAME_SORT_BRACKET_TAG = "bracket_tag"

    /** 底部导航栏样式：默认。 */
    const val NAV_STYLE_DEFAULT = "default"

    /** 底部导航栏样式：圆角液态玻璃（流体玻璃）。 */
    const val NAV_STYLE_LIQUID_GLASS = "liquid_glass"

    /** 导航栏样式内存态：随设置页切换即时广播，供 MainScreen 重组切换样式。 */
    val navStyleState: MutableState<String> = mutableStateOf(NAV_STYLE_DEFAULT)

    /** 游戏排序内存态：设置页切换后游戏页可随重组读取。 */
    val gameSortState: MutableState<String> = mutableStateOf(GAME_SORT_ALPHA)

    /** 首次组合时从持久化加载导航栏样式到内存态（幂等，重复调用仅重新读一次）。 */
    fun initNavStyle(c: Context) {
        navStyleState.value = getNavStyle(c)
    }

    fun initGameSort(c: Context) {
        gameSortState.value = getGameSort(c)
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

    fun getGameSort(c: Context): String =
        when (prefs(c).getString(KEY_GAME_SORT, GAME_SORT_ALPHA)) {
            GAME_SORT_BRACKET_TAG -> GAME_SORT_BRACKET_TAG
            else -> GAME_SORT_ALPHA
        }

    fun setGameSort(c: Context, sort: String) {
        val normalized = when (sort) {
            GAME_SORT_BRACKET_TAG -> GAME_SORT_BRACKET_TAG
            else -> GAME_SORT_ALPHA
        }
        prefs(c).edit().putString(KEY_GAME_SORT, normalized).apply()
        gameSortState.value = normalized
    }

    /** 外观模式（跟随系统/浅色/深色）。 */
    fun getThemeMode(c: Context): String =
        prefs(c).getString(KEY_THEME_MODE, THEME_MODE_LIGHT) ?: THEME_MODE_LIGHT

    fun setThemeMode(c: Context, mode: String) =
        prefs(c).edit().putString(KEY_THEME_MODE, mode).apply()

    /** 色调切换：开启时使用白色页面背景 + 中性灰组件；关闭时使用中性灰页面背景 + 白色组件。 */
    fun isToneSwitchEnabled(c: Context): Boolean =
        prefs(c).getBoolean(KEY_TONE_SWITCH, true)

    fun setToneSwitchEnabled(c: Context, enabled: Boolean) =
        prefs(c).edit().putBoolean(KEY_TONE_SWITCH, enabled).apply()

    /** 系统当前是否深色模式（资源配置 uiMode）。 */
    fun isSystemDark(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 实际生效的深色状态：dark 恒深色，system 跟随系统，其余（含 light 与未知值）为浅色。 */
    fun isDarkEffective(c: Context): Boolean = when (getThemeMode(c)) {
        THEME_MODE_DARK -> true
        THEME_MODE_SYSTEM -> isSystemDark(c)
        else -> false
    }

    /** 莫奈动态取色开关（默认关闭，走手动轮盘色）。 */
    fun isMonetEnabled(c: Context): Boolean =
        prefs(c).getBoolean(KEY_MONET_SWITCH, false)

    fun setMonetEnabled(c: Context, enabled: Boolean) =
        prefs(c).edit().putBoolean(KEY_MONET_SWITCH, enabled).apply()

    /** 动态取色来源：跟随系统壁纸 / 游戏封面。 */
    fun getMonetSource(c: Context): String =
        when (prefs(c).getString(KEY_MONET_SOURCE, MONET_SOURCE_SYSTEM)) {
            MONET_SOURCE_COVER -> MONET_SOURCE_COVER
            else -> MONET_SOURCE_SYSTEM
        }

    fun setMonetSource(c: Context, source: String) {
        val normalized = when (source) {
            MONET_SOURCE_COVER -> MONET_SOURCE_COVER
            else -> MONET_SOURCE_SYSTEM
        }
        prefs(c).edit().putString(KEY_MONET_SOURCE, normalized).apply()
    }

    /** 配色风格存储值（默认 tonal_spot）。 */
    fun getPaletteStyleValue(c: Context): String =
        prefs(c).getString(KEY_PALETTE_STYLE, PALETTE_STYLE_TONAL_SPOT) ?: PALETTE_STYLE_TONAL_SPOT

    fun setPaletteStyleValue(c: Context, value: String) =
        prefs(c).edit().putString(KEY_PALETTE_STYLE, value).apply()
}
