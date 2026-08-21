package com.example.tyranornext.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tyranornext.scanner.EngineLauncher
import com.example.tyranornext.scanner.EngineScanner
import com.example.tyranornext.scanner.EngineType
import com.example.tyranornext.scanner.ScanGame
import kotlinx.coroutines.launch

// 每页：一行3个 × 6行 = 18 个
private const val PAGE_SIZE = 3 * 6

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var games by remember { mutableStateOf(EngineScanner.loadGames(context)) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedGame by remember { mutableStateOf<ScanGame?>(null) }
    // 单游戏(应用级)设置页：非空时整页替换
    var settingsGame by remember { mutableStateOf<ScanGame?>(null) }

    if (settingsGame != null) {
        PerGameSettingsScreen(game = settingsGame!!, onBack = { settingsGame = null })
        return
    }

    // 无限加载：当前已展开的游戏数量（初始一页，滚动到底/不满屏自动追加）
    var visibleCount by remember { mutableStateOf(PAGE_SIZE) }
    val gridState = rememberLazyGridState()

    // 首页加载后：若首屏不满会持续自动补页，直到填满或全部展示
    LaunchedEffect(games) {
        visibleCount = minOf(PAGE_SIZE, games.size)
    }

    // 滚动到接近底部时加载下一页（覆盖"不满屏自动加载"：visibleItems 为空或已到底）
    LaunchedEffect(games, visibleCount) {
        if (visibleCount >= games.size) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= visibleCount - 1
        }.collect { atBottom ->
            if (atBottom) {
                visibleCount = minOf(visibleCount + PAGE_SIZE, games.size)
            }
        }
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // 添加后立即扫描该目录
            scope.launch {
                scanning = true
                status = null
                EngineScanner.saveRoot(context, u)
                val all = mutableListOf<ScanGame>()
                EngineScanner.loadRoots(context).forEach { root ->
                    all += EngineScanner.scanRoot(context, root)
                }
                val seen = mutableSetOf<String>()
                val dedup = all.filter { seen.add(it.uri) }
                EngineScanner.saveGames(context, dedup)
                games = dedup
                status = "扫描完成：共 ${dedup.size} 个游戏"
                scanning = false
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // ===== 顶部栏：标题居左 + 右侧两个图标按钮 =====
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "游戏",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = "添加文件夹",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(31.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { dirPicker.launch(null) }
                        .padding(4.dp),
                )
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "扫描游戏",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(31.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!scanning) {
                                scope.launch {
                                    scanning = true
                                    status = null
                                    val roots = EngineScanner.loadRoots(context)
                                    if (roots.isEmpty()) {
                                        status = "请先添加文件夹"
                                    } else {
                                        val all = mutableListOf<ScanGame>()
                                        roots.forEach { root ->
                                            all += EngineScanner.scanRoot(context, root)
                                        }
                                        val seen = mutableSetOf<String>()
                                        val dedup = all.filter { seen.add(it.uri) }
                                        EngineScanner.saveGames(context, dedup)
                                        games = dedup
                                        status = "扫描完成：共 ${dedup.size} 个游戏"
                                    }
                                    scanning = false
                                }
                            }
                        }
                        .padding(4.dp),
                )
            }
        }

        // ===== 扫描状态 =====
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ===== 内容区 =====
        Box(Modifier.fillMaxSize()) {
            when {
                scanning -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                games.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无游戏", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "点击右上角添加文件夹并扫描",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = { dirPicker.launch(null) },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("添加文件夹") }
                    }
                }
                else -> {
                    GameGrid(
                        games = games.subList(0, visibleCount.coerceAtMost(games.size)),
                        gridState = gridState,
                        onGameClick = { selectedGame = it },
                    )
                }
            }
        }
    }

    // ===== 点击游戏卡片的底部抽屉栏 =====
    selectedGame?.let { game ->
        GameActionsSheet(
            game = game,
            onDismiss = { selectedGame = null },
            onEngineSettings = {
                settingsGame = game
                selectedGame = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameActionsSheet(game: ScanGame, onDismiss: () -> Unit, onEngineSettings: () -> Unit) {
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color.White,
    ) {
        // 标题
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 1.dp)

        // 三个功能项
        GameActionRow(Icons.Filled.PlayArrow, "启动游戏") {
            launchError = EngineLauncher.launch(context, game)
            if (launchError == null) onDismiss()
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp)
        GameActionRow(Icons.Filled.Settings, "引擎设置", onClick = onEngineSettings)
        HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp)
        GameActionRow(Icons.Filled.Star, "收藏游戏") { onDismiss() }

        launchError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // 底部安全区留白
        Box(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun GameActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

@Composable
private fun GameGrid(games: List<ScanGame>, gridState: LazyGridState, onGameClick: (ScanGame) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),            // 一行三个
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.uri }) { game ->
            GameCard(game, onClick = { onGameClick(game) })
        }
    }
}

@Composable
private fun GameCard(game: ScanGame, onClick: () -> Unit) {
    Column {
        // 卡片 1:3（高:宽 = 4:3 立式封面，一行三列）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(game.engine.coverColor())
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Tyranor",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    game.engine.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Text(
            game.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

private fun EngineType.coverColor(): Color = when (this) {
    EngineType.KIRIKIRI -> Color(0xFF3B5998)
    EngineType.ONS -> Color(0xFF43A047)
    EngineType.TYRANO -> Color(0xFFC6443C)
    EngineType.ARTEMIS -> Color(0xFF7E57C2)
    EngineType.UNKNOWN -> Color(0xFF607D8B)
}