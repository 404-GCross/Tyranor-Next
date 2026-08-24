package com.tyranor.next.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.tyranor.next.settings.AppSettingsStore

/**
 * 全局主题色：读写 AppSettingsStore，变化时通过 snapshot state 通知所有已组合页面
 * 即时重组（应用设置「色调轮盘」确认后全 App 生效）。
 */
object AppThemeColors {
    private var loaded = false
    var primary by mutableStateOf(Blue40)
        private set

    /** 深色模式（应用设置「外观模式」）；变化时全 App 重组。 */
    var isDark by mutableStateOf(false)
        private set

    /** 色调切换：控制页面背景色与组件色是否互换。 */
    var toneSwitchEnabled by mutableStateOf(true)
        private set

    /** 莫奈动态取色开关（应用设置「动态取色」）。 */
    var monetEnabled by mutableStateOf(false)
        private set

    /** 动态取色来源：system（跟随系统壁纸）/ cover（游戏封面）。 */
    var monetSource by mutableStateOf(AppSettingsStore.MONET_SOURCE_SYSTEM)
        private set

    /** 配色风格（Material You 调色板）。 */
    var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot)
        private set

    /** 当前选中游戏的封面 URI（由 GameScreen 写入，供「游戏封面取色」读取）。 */
    var currentCoverUri by mutableStateOf<String?>(null)

    /** 实际生效的种子色：由 TyranorNextTheme 计算后写入，供嵌套的 MiuixSettingsTheme 复用。 */
    var effectiveSeed by mutableStateOf(Blue40)
        private set

    /** 首次组合时从存储加载（幂等，避免每次重组都读 prefs）；
     *  跟随系统时每次组合都重读，系统深/浅切换（Activity 重建）后能立即拿到新值。 */
    fun ensureLoaded(context: Context) {
        if (!loaded || AppSettingsStore.getThemeMode(context) == AppSettingsStore.THEME_MODE_SYSTEM) {
            loaded = true
            refresh(context)
        }
    }

    /** 从存储重读主题色与外观模式并广播变更（system 模式按系统当前深/浅解析）。 */
    fun refresh(context: Context) {
        primary = parseColorHex(AppSettingsStore.getThemeColorHex(context))
        isDark = AppSettingsStore.isDarkEffective(context)
        toneSwitchEnabled = AppSettingsStore.isToneSwitchEnabled(context)
        monetEnabled = AppSettingsStore.isMonetEnabled(context)
        monetSource = AppSettingsStore.getMonetSource(context)
        paletteStyle = PaletteStyle.fromStorageValue(AppSettingsStore.getPaletteStyleValue(context))
    }

    /** 更新主题实际使用的种子色，并广播组合重组。 */
    fun updateEffectiveSeed(seed: Color) {
        effectiveSeed = seed
    }

    /**
     * 解析实际生效的种子色（供主题生成动态配色）：
     * - 动态取色关闭：返回手动轮盘色 [primary]
     * - 来源「跟随系统壁纸」且 Android 12+：取系统壁纸 accent 色，失败回退手动色；
     *   Android 12 以下不支持壁纸取色（未引入 MonetCompat），直接回退手动色
     * - 来源「游戏封面」：从 [currentCoverUri] 提取主色，失败回退手动色
     */
    suspend fun resolveSeedColor(context: Context): Color {
        if (!monetEnabled) return primary
        return when (monetSource) {
            AppSettingsStore.MONET_SOURCE_COVER ->
                extractSeedColorFromCover(context, currentCoverUri) ?: primary
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        Color(context.resources.getColor(android.R.color.system_accent1_500, null))
                    }.getOrDefault(primary)
                } else {
                    primary
                }
            }
        }
    }
}

/** 解析 #RRGGBB 为 Compose Color，非法值回退默认蓝。 */
fun parseColorHex(hex: String): Color = try {
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))
} catch (t: Throwable) {
    Blue40
}
