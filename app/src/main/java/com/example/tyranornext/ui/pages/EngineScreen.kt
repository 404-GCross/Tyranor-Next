package com.example.tyranornext.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tyranornext.R
import com.example.tyranornext.scanner.EngineLauncher
import com.example.tyranornext.scanner.EngineType
import com.example.tyranornext.theme.NavWhite

/** 引擎页：列表行展示已集成的游戏引擎。 */
@Composable
fun EngineScreen(modifier: Modifier = Modifier) {
    val engines = EngineLauncher.supportedEngines

    Column(modifier.fillMaxSize()) {
        // 顶部栏：标题居左
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("引擎", style = MaterialTheme.typography.titleLarge)
            }
        }

        // 引擎列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(engines, key = { it.name }) { engine ->
                EngineRow(engine)
            }
        }
    }
}

@Composable
private fun EngineRow(engine: EngineType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = NavWhite),
        shape = RoundedCornerShape(8.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.engine_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    engine.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    engineDescription(engine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已集成",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun engineDescription(engine: EngineType): String = when (engine) {
    EngineType.KIRIKIRI -> "Kirikiri2 / 吉里吉里，.xp3 与 startup.tjs 游戏"
    EngineType.ONS -> "ONScripter，nscript.dat 与 .nsa 归档游戏"
    EngineType.TYRANO -> "TyranoBuilder，index.html 与 tyrano/ 脚本游戏"
    EngineType.ARTEMIS -> "Artemis，system.ini 与 .pfs 归档游戏"
    EngineType.UNKNOWN -> "未知引擎"
}
