package com.example.tyranornext.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
  lightColorScheme(
    primary = Blue40,
    secondary = Teal40,
    tertiary = Amber40,
    // 页面中性灰
    background = PageGrey,
    surface = PageGrey,
    onBackground = TextColor,
    onSurface = TextColor,
  )

@Composable
fun TyranorNextTheme(content: @Composable () -> Unit) {
  // 完全固定配色，不随系统深/浅色模式或壁纸取色变化
  MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
