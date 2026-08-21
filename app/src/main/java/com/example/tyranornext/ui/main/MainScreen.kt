package com.example.tyranornext.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.tyranornext.R
import com.example.tyranornext.ui.pages.EngineScreen
import com.example.tyranornext.ui.pages.GameScreen
import com.example.tyranornext.ui.pages.HomeScreen
import com.example.tyranornext.ui.pages.SettingsScreen

// 底部导航栏 Tab 定义
private data class Tab(
  val label: String,
  val iconRes: Int,
)

private val tabItems = listOf(
  Tab("首页", R.drawable.ic_home),
  Tab("游戏", R.drawable.ic_game),
  Tab("引擎", R.drawable.ic_module),
  Tab("设置", R.drawable.ic_settings),
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
  var selectedIndex by rememberSaveable { mutableStateOf(0) }

  // 外层只负责布局：内容区 + 底部导航栏（不用 Scaffold，避免与子页顶部栏的 inset 冲突）
  Column(modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      when (selectedIndex) {
        0 -> HomeScreen(Modifier.fillMaxSize())
        1 -> GameScreen(Modifier.fillMaxSize())
        2 -> EngineScreen(Modifier.fillMaxSize())
        3 -> SettingsScreen(Modifier.fillMaxSize())
      }
    }
    NavigationBar(
      containerColor = com.example.tyranornext.theme.NavWhite,
      contentColor = androidx.compose.material3.LocalContentColor.current,
    ) {
      tabItems.forEachIndexed { index, tab ->
        NavigationBarItem(
          selected = selectedIndex == index,
          onClick = { selectedIndex = index },
          icon = {
            Image(
              painter = painterResource(tab.iconRes),
              contentDescription = tab.label,
              modifier = Modifier.size(28.dp),
            )
          },
          label = { Text(tab.label) },
          // 去掉选中高亮：选中/未选中背景均为透明，文字颜色统一
          colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
            selectedIconColor = androidx.compose.material3.LocalContentColor.current,
            selectedTextColor = androidx.compose.material3.LocalContentColor.current,
            indicatorColor = Color.Transparent,
            unselectedIconColor = androidx.compose.material3.LocalContentColor.current,
            unselectedTextColor = androidx.compose.material3.LocalContentColor.current,
          ),
        )
      }
    }
  }
}