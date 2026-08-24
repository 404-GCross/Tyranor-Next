package com.tyranor.next.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 在生成后的动态 ColorScheme 上套用「色调切换」与固定灰白背景：
 * 彩色角色（primary/secondary/tertiary/container 等）来自莫奈动态配色；
 * 页面背景/表面/文字保持应用既有的中性灰白基调，由「色调切换」控制是否互换
 * （直接复用 Color.kt 的公开 getter，保证与改动前语义一致）。
 */
private fun applyToneSwitch(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = PageGrey,
    surface = PageGrey,
    onBackground = TextColor,
    onSurface = TextColor,
)

@Composable
@NonSkippableComposable
fun TyranorNextTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    AppThemeColors.ensureLoaded(context)
    val manual = AppThemeColors.primary
    val dark = AppThemeColors.isDark
    val style = AppThemeColors.paletteStyle

    LaunchedEffect(
        AppThemeColors.monetEnabled,
        AppThemeColors.monetSource,
        AppThemeColors.currentCoverUri,
        manual,
    ) {
        if (AppThemeColors.monetEnabled) {
            AppThemeColors.updateEffectiveSeed(AppThemeColors.resolveSeedColor(context))
        }
    }

    val colorScheme = monetColorScheme(
        seedColor = if (AppThemeColors.monetEnabled) {
            AppThemeColors.effectiveSeed
        } else {
            manual
        },
        isDark = dark,
        style = style,
    ).let { applyToneSwitch(it) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
