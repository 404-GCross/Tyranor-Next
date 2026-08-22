package com.example.tyranornext.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 设置页专用 Miuix 主题：固定浅色，配色与 TyranorNextTheme 对齐
 * （浅灰页面背景 + 白色卡片 + 蓝色主色），不随系统深色模式变化。
 */
private val TyranorMiuixLightColors = lightColorScheme(
    primary = Blue40,
    background = PageGrey,
    surface = PageGrey,
    surfaceContainer = NavWhite,
    onBackground = TextColor,
    onSurface = TextColor,
    onSurfaceContainer = TextColor,
)

@Composable
fun MiuixSettingsTheme(content: @Composable () -> Unit) {
    MiuixTheme(colors = TyranorMiuixLightColors, content = content)
}
