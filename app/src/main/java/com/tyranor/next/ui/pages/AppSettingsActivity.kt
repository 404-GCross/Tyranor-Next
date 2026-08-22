package com.tyranor.next.ui.pages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyranor.next.theme.MiuixSettingsTheme
import com.tyranor.next.theme.TyranorNextTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用设置页 Activity：入口见设置页「应用设置」项。 */
class AppSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            TyranorNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppSettingsScreen()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, AppSettingsActivity::class.java)
    }
}

/** 应用设置页骨架：暂只有标题栏，内容后续填充。 */
@Composable
internal fun AppSettingsScreen() {
    MiuixSettingsTheme {
        MiuixScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.background)) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "应用设置",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无设置项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}
