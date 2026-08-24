package com.tyranor.next.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    primaryColor: Color? = null,
    content: @Composable () -> Unit,
) {
  // 配色来源：动态取色开启时种子色来自壁纸/封面，关闭时由 primaryColor 或手动轮盘色提供
  AppThemeColors.ensureLoaded(LocalContext.current)
  // 在函数体内直接读取全局主题色：配合 @NonSkippableComposable，主题色变更时本主题
  // 必然重组并向整棵组合树提供新 colorScheme（不依赖调用点对默认参数的订阅）。
  val manual = primaryColor ?: AppThemeColors.primary
  // 深/浅色由应用设置「外观模式」控制；背景/文字用随模式的动态常量（Color.kt getter）
  val dark = AppThemeColors.isDark
  val style = AppThemeColors.paletteStyle

  // 动态取色开关、来源、封面 URI、手动主色变化时重新解析实际生效的种子色
  LaunchedEffect(
      AppThemeColors.monetEnabled,
      AppThemeColors.monetSource,
      AppThemeColors.currentCoverUri,
      manual,
  ) {
      val seed = if (AppThemeColors.monetEnabled) {
          withContext(Dispatchers.Default) {
              AppThemeColors.resolveSeedColor(LocalContext.current)
          }
      } else {
          manual
      }
      AppThemeColors.effectiveSeed = seed
  }

  // 主题色变更时本主题必然重组：读取 effectiveSeed 触发重组
  val seed = AppThemeColors.effectiveSeed
  val colorScheme = monetColorScheme(
      seedColor = seed,
      isDark = dark,
      style = style,
  ).let { applyToneSwitch(it) }
  MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
  )
}
