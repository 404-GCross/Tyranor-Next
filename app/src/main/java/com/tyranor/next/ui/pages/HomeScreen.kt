package com.tyranor.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyranor.next.scanner.EngineScanner
import com.tyranor.next.scanner.ScanGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recentGames by remember { mutableStateOf(EngineScanner.loadRecentGames(context)) }
    var selectedGame by remember { mutableStateOf<ScanGame?>(null) }

    fun replaceGame(updated: ScanGame) {
        recentGames = recentGames.map { if (it.uri == updated.uri) updated else it }
        selectedGame = selectedGame?.let { if (it.uri == updated.uri) updated else it }
        // 同步持久化最近记录与主游戏库，避免修改丢失
        EngineScanner.saveRecentGames(context, recentGames)
        EngineScanner.saveGames(
            context,
            EngineScanner.loadGames(context).map { if (it.uri == updated.uri) updated else it },
        )
    }

    fun deleteGame(target: ScanGame) {
        recentGames = recentGames.filterNot { it.uri == target.uri }
        selectedGame = null
        // 仅清理应用内数据（每游戏设置、最近记录、封面缓存、应用内存档镜像）；不触碰游戏文件
        scope.launch(Dispatchers.IO) {
            cleanupDeletedGame(context, target)
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("首页", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (recentGames.isEmpty()) {
                Text(
                    "暂无最近打开的游戏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentGames, key = { it.uri }) { game ->
                        GameCard(game, onClick = { selectedGame = game })
                    }
                }
            }
        }
    }

    // ===== 点击游戏卡片的底部抽屉栏（与游戏页一致，不直接启动游戏） =====
    selectedGame?.let { game ->
        GameActionsSheet(
            game = game,
            onDismiss = { selectedGame = null },
            onGameUpdated = { replaceGame(it) },
            onDeleteGame = { deleteGame(game) },
            onEngineSettings = {
                startActivityWithFade(context, PerGameSettingsActivity.createIntent(context, game))
                selectedGame = null
            },
        )
    }
}
