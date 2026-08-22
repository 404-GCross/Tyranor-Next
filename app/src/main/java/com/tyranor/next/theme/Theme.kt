package com.tyranor.next.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun TyranorNextTheme(
  primaryColor: Color = AppThemeColors.primary,
  content: @Composable () -> Unit,
) {
  // 固定浅色配色，不随系统深/浅色模式或壁纸取色变化；primaryColor 由应用设置「色调轮盘」提供
  AppThemeColors.ensureLoaded(LocalContext.current)
  MaterialTheme(
    colorScheme = lightColorScheme(
      primary = primaryColor,
      secondary = Teal40,
      tertiary = Amber40,
      // 页面中性灰
      background = PageGrey,
      surface = PageGrey,
      onBackground = TextColor,
      onSurface = TextColor,
    ),
    typography = Typography,
    content = content,
  )
}
