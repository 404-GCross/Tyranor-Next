package com.tyranor.next.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.tyranor.next.ui.common.WithoutPressIndication
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 文字统一规范：页面内容只用 titleMedium(16sp)/bodyMedium(14sp) 两档。
 * Miuix preference 的标题默认用 headline1(17sp)，这里覆盖为 16sp，
 * 使其严格落入两档内，与 Material titleMedium 一致。
 */
private val TyranorMiuixTextStyles = defaultTextStyles(
    headline1 = TextStyle(fontSize = 16.sp),
)

/**
 * 设置页专用 Miuix 主题：配色与 TyranorNextTheme 对齐，深/浅色由应用设置「外观模式」控制
 * （深灰页面背景 + 深色卡片 或 浅灰背景 + 白色卡片）。
 * primaryColor 由应用设置「色调轮盘」提供，默认蓝与 TyranorNextTheme 一致。
 */
@Composable
@NonSkippableComposable
fun MiuixSettingsTheme(
    primaryColor: Color? = null,
    content: @Composable () -> Unit,
) {
    AppThemeColors.ensureLoaded(LocalContext.current)
    // 与 TyranorNextTheme 同理：在函数体内直接读取全局主题色，
    // 配合 @NonSkippableComposable 保证轮盘切换主题色时本主题必然重组，
    // 使设置类页面与其余页面同步跟随主题色。
    val primary = primaryColor ?: AppThemeColors.primary
    val dark = AppThemeColors.isDark
    val colors = if (dark) {
        darkColorScheme(
            primary = primary,
            background = PageGrey,
            surface = PageGrey,
            surfaceContainer = NavWhite,
            onBackground = TextColor,
            onSurface = TextColor,
            onSurfaceContainer = TextColor,
            sliderKeyPointForeground = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            background = PageGrey,
            surface = PageGrey,
            surfaceContainer = NavWhite,
            onBackground = TextColor,
            onSurface = TextColor,
            onSurfaceContainer = TextColor,
            sliderKeyPointForeground = Color.White,
        )
    }
    MiuixTheme(
        colors = colors,
        textStyles = TyranorMiuixTextStyles,
    ) {
        WithoutPressIndication(content)
    }
}
