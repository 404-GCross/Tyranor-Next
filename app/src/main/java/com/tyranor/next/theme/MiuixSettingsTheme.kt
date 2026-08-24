package com.tyranor.next.theme

import androidx.compose.material3.MaterialTheme
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
 * 设置页专用 Miuix 主题：直接复用外层 TyranorNextTheme 提供的动态 ColorScheme
 * （主/次/辅色与容器色来自莫奈动态配色，背景/表面/文字来自「色调切换」），
 * 把对应角色映射到 Miuix 配色，保证设置页与主 UI 完全同色。
 * 必须在 TyranorNextTheme 内部调用（AppSettingsActivity 已包裹）。
 */
@Composable
@NonSkippableComposable
fun MiuixSettingsTheme(
    primaryColor: Color? = null,
    content: @Composable () -> Unit,
) {
    AppThemeColors.ensureLoaded(LocalContext.current)
    // 复用外层 TyranorNextTheme 计算好的动态配色，不在此重复取色
    val scheme = MaterialTheme.colorScheme
    val dark = AppThemeColors.isDark
    val colors = if (dark) {
        darkColorScheme(
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            primaryContainer = scheme.primaryContainer,
            onPrimaryContainer = scheme.onPrimaryContainer,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            background = scheme.background,
            surface = scheme.surface,
            surfaceContainer = NavWhite,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface,
            onSurfaceContainer = TextColor,
            sliderKeyPointForeground = Color.White,
        )
    } else {
        lightColorScheme(
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            primaryContainer = scheme.primaryContainer,
            onPrimaryContainer = scheme.onPrimaryContainer,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            background = scheme.background,
            surface = scheme.surface,
            surfaceContainer = NavWhite,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface,
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
