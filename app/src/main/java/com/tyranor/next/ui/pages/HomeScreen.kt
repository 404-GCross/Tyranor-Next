package com.tyranor.next.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyranor.next.scanner.EngineLauncher
import com.tyranor.next.scanner.EngineScanner
import com.tyranor.next.scanner.ScanGame
import com.tyranor.next.theme.NavWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 首页数据每次进入重组时重新加载，保证游戏页改动后切回立即生效
    var quickLaunch by remember { mutableStateOf(EngineScanner.loadQuickLaunch(context)) }
    var recentGames by remember { mutableStateOf(EngineScanner.loadRecentGames(context).take(10)) }
    var selectedGame by remember { mutableStateOf<ScanGame?>(null) }

    fun replaceGame(updated: ScanGame) {
        quickLaunch = quickLaunch.map { if (it.uri == updated.uri) updated else it }
        recentGames = recentGames.map { if (it.uri == updated.uri) updated else it }
        selectedGame = selectedGame?.let { if (it.uri == updated.uri) updated else it }
        // 同步持久化最近记录、快捷启动与主游戏库，避免修改丢失
        EngineScanner.saveRecentGames(context, recentGames)
        EngineScanner.saveQuickLaunch(context, quickLaunch)
        EngineScanner.saveGames(
            context,
            EngineScanner.loadGames(context).map { if (it.uri == updated.uri) updated else it },
        )
    }

    fun deleteGame(target: ScanGame) {
        quickLaunch = quickLaunch.filterNot { it.uri == target.uri }
        recentGames = recentGames.filterNot { it.uri == target.uri }
        selectedGame = null
        // 仅清理应用内数据（每游戏设置、最近记录、快捷启动、封面缓存、应用内存档镜像）；不触碰游戏文件
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

        // ===== 顶部栏底下固定三个快捷启动游戏（一行三个） =====
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) { i ->
                val game = quickLaunch.getOrNull(i)
                QuickLaunchSlot(
                    game = game,
                    onClick = { game?.let { EngineLauncher.launch(context, it) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ===== 快捷启动下方：最近打开列表（最多 10 条，圆角长矩形） =====
        if (recentGames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无最近打开的游戏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recentGames, key = { it.uri }) { game ->
                    RecentGameRow(game, onClick = { selectedGame = game })
                }
            }
        }
    }

    // ===== 点击最近打开项的底部抽屉栏（与游戏页一致，不直接启动游戏） =====
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

/** 首页快捷启动槽位：已设置显示封面/引擎色，空槽显示白色封面 + 加号。 */
@Composable
private fun QuickLaunchSlot(
    game: ScanGame?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (game == null) NavWhite else game.engine.coverColor())
                .clickable(enabled = game != null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (game == null) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "空槽位",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                val coverBitmap by rememberCoverBitmap(game.coverUri)
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!,
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        game.engine.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            game?.title ?: "快捷启动",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** 最近打开列表项：圆角长矩形，左侧游戏名，右侧打开时间。 */
@Composable
private fun RecentGameRow(game: ScanGame, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatOpenTime(game.openTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun formatOpenTime(ts: Long): String {
    if (ts <= 0) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }.getOrDefault("")
}
