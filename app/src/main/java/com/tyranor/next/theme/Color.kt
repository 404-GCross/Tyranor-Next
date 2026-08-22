package com.tyranor.next.theme

import androidx.compose.ui.graphics.Color

// 主色
val Blue40 = Color(0xFF307DEF)
val Teal40 = Color(0xFF2E7D78)
val Amber40 = Color(0xFF9A6C1A)

// 浅色模式固定色
private val PageGreyLight = Color(0xFFF2F3F5)  // 页面背景：中性灰
private val NavWhiteLight = Color(0xFFFFFFFF)  // 卡片/导航栏背景：白色
private val TextColorLight = Color(0xFF1F2329) // 正文/标题文字：深灰黑
private val UnselectedGreyLight = Color(0xFF8A8F98) // 导航栏未选中图标/文字：中性灰

// 深色模式对应色
private val PageGreyDark = Color(0xFF17191C)
private val NavWhiteDark = Color(0xFF222529)
private val TextColorDark = Color(0xFFE3E4E6)
private val UnselectedGreyDark = Color(0xFF7A8087)

// 页面背景：随深色模式取深/浅
val PageGrey: Color get() = if (AppThemeColors.isDark) PageGreyDark else PageGreyLight
// 卡片/导航栏背景：随深色模式取深/浅
val NavWhite: Color get() = if (AppThemeColors.isDark) NavWhiteDark else NavWhiteLight
// 正文/标题文字色：随深色模式取深/浅
val TextColor: Color get() = if (AppThemeColors.isDark) TextColorDark else TextColorLight
// 导航栏未选中图标/文字色：随深色模式取深/浅
val UnselectedGrey: Color get() = if (AppThemeColors.isDark) UnselectedGreyDark else UnselectedGreyLight
