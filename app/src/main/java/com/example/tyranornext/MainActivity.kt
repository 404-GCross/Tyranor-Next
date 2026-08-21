package com.example.tyranornext

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.tyranornext.scanner.EnginePluginBootstrap
import com.example.tyranornext.theme.TyranorNextTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 首启自动安装引擎原生插件（幂等，放在后台线程避免首次复制阻塞 UI）
    Thread {
      EnginePluginBootstrap.provisionIfNeeded(applicationContext)
    }.apply { isDaemon = true }.start()

    enableEdgeToEdge(
      statusBarStyle = androidx.activity.SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
      navigationBarStyle = androidx.activity.SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
    )

    // 状态栏/导航栏透明，让顶部栏(surfaceContainer)与底部导航栏背景向上向下延伸成沉浸式
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    setContent {
      TyranorNextTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}