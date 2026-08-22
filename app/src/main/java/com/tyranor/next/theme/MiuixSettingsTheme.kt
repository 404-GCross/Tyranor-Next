package com.tyranor.next.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
 * 设置页专用 Miuix 主题：固定浅色，配色与 TyranorNextTheme 对齐
 * （浅灰页面背景 + 白色卡片），不随系统深色模式变化。
 * primaryColor 由应用设置「色调轮盘」提供，默认蓝与 TyranorNextTheme 一致。
 */
@Composable
fun MiuixSettingsTheme(
    primaryColor: Color = AppThemeColors.primary,
    content: @Composable () -> Unit,
) {
    AppThemeColors.ensureLoaded(LocalContext.current)
    MiuixTheme(
        colors = lightColorScheme(
            primary = primaryColor,
            background = PageGrey,
            surface = PageGrey,
            surfaceContainer = NavWhite,
            onBackground = TextColor,
            onSurface = TextColor,
            onSurfaceContainer = TextColor,
        ),
        textStyles = TyranorMiuixTextStyles,
        content = content,
    )
}
