package com.tyranor.next.ui.pages

import android.app.Activity
import android.app.ActivityOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyranor.next.scanner.EngineLauncher
import com.tyranor.next.scanner.EngineScanner
import com.tyranor.next.scanner.EngineType
import com.tyranor.next.scanner.GameSaveManager
import com.tyranor.next.scanner.ScanGame
import com.tyranor.next.scanner.VndbCandidate
import com.tyranor.next.scanner.VndbCoverService
import com.tyranor.next.settings.PerGameSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var games by remember { mutableStateOf(EngineScanner.loadGames(context)) }
    var scanning by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<ScanGame?>(null) }

    val gridState = rememberLazyGridState()

    fun replaceGame(updated: ScanGame) {
        val nextGames = games.map { if (it.uri == updated.uri) updated else it }
        games = nextGames
        selectedGame = selectedGame?.let { if (it.uri == updated.uri) updated else it }
        EngineScanner.saveGames(context, nextGames)
    }

    fun deleteGame(target: ScanGame) {
        val nextGames = games.filterNot { it.uri == target.uri }
        games = nextGames
        selectedGame = null
        EngineScanner.saveGames(context, nextGames)
        // 仅清理应用内数据（每游戏设置、最近记录、封面缓存、应用内存档镜像）；不触碰游戏文件
        scope.launch(Dispatchers.IO) {
            cleanupDeletedGame(context, target)
        }
    }

    fun syncMissingCovers() {
        if (scanning) return
        scope.launch {
            scanning = true
            val current = games
            val updated = withContext(Dispatchers.IO) {
                current.map { game ->
                    val next = runCatching { VndbCoverService.fetchBestCover(context, game) }.getOrNull()
                    if (next != null && next.coverUri != game.coverUri) {
                        next
                    } else {
                        game
                    }
                }
            }
            games = updated
            EngineScanner.saveGames(context, updated)
            scanning = false
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
                EngineScanner.saveRoot(context, u)
                val all = mutableListOf<ScanGame>()
                EngineScanner.loadRoots(context).forEach { root ->
                    all += EngineScanner.scanRoot(context, root)
                }
                val seen = mutableSetOf<String>()
                val dedup = all.filter { seen.add(it.uri) }
                EngineScanner.saveGames(context, dedup)
                games = dedup
                scanning = false
            }
        }
    }

    GameLibraryContent(
        modifier = modifier,
        games = games,
        scanning = scanning,
        gridState = gridState,
        dirPickerLaunch = { dirPicker.launch(null) },
        syncMissingCovers = { syncMissingCovers() },
        refreshGames = {
            if (!scanning) {
                scope.launch {
                    scanning = true
                    val roots = EngineScanner.loadRoots(context)
                    if (roots.isNotEmpty()) {
                        val all = mutableListOf<ScanGame>()
                        roots.forEach { root ->
                            all += EngineScanner.scanRoot(context, root)
                        }
                        val seen = mutableSetOf<String>()
                        val dedup = all.filter { seen.add(it.uri) }
                        EngineScanner.saveGames(context, dedup)
                        games = dedup
                    }
                    scanning = false
                }
            }
        },
        onGameClick = { selectedGame = it },
    )

    // ===== 点击游戏卡片的底部抽屉栏 =====
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

/** 删除游戏后清理应用内关联数据（设置/最近记录/封面/存档镜像），绝不触碰游戏文件。 */
internal fun cleanupDeletedGame(context: android.content.Context, target: ScanGame) {
    PerGameSettingsStore.clear(context, target.uri)
    EngineScanner.removeRecentGame(context, target.uri)
    deleteCoverFile(context, target.coverUri)
    GameSaveManager(context).cleanupAppData(target)
}

private fun deleteCoverFile(context: android.content.Context, coverUri: String?) {
    if (coverUri.isNullOrBlank()) return
    val file = runCatching { File(android.net.Uri.parse(coverUri).path ?: return) }.getOrNull() ?: return
    val coverDir = File(context.filesDir, "covers_remote").canonicalPath
    if (runCatching { file.canonicalPath }.getOrNull()?.startsWith(coverDir) == true) {
        file.delete()
    }
}

internal fun startActivityWithFade(context: android.content.Context, intent: android.content.Intent) {
    if (context is Activity) {
        val options = ActivityOptions.makeCustomAnimation(
            context,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        context.startActivity(intent, options.toBundle())
    } else {
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun GameLibraryContent(
    modifier: Modifier,
    games: List<ScanGame>,
    scanning: Boolean,
    gridState: LazyGridState,
    dirPickerLaunch: () -> Unit,
    syncMissingCovers: () -> Unit,
    refreshGames: () -> Unit,
    onGameClick: (ScanGame) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        // ===== 顶部栏：页面背景色，标题居左 + 右侧三个图标按钮 =====
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "游戏",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = "自动获取封面",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { syncMissingCovers() }
                            .padding(4.dp),
                    )
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "扫描游戏",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { refreshGames() }
                            .padding(4.dp),
                    )
                }
            }
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
                        Text("暂无游戏", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "点击右上角添加文件夹并扫描",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = { dirPickerLaunch() },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("添加文件夹") }
                    }
                }
                else -> {
                    GameGrid(
                        games = games,
                        gridState = gridState,
                        onGameClick = onGameClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameActionsSheet(
    game: ScanGame,
    onDismiss: () -> Unit,
    onGameUpdated: (ScanGame) -> Unit,
    onDeleteGame: () -> Unit,
    onEngineSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var launchError by remember { mutableStateOf<String?>(null) }
    var showVndbSearch by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GameActionRow(Icons.Filled.PlayArrow, "启动游戏") {
                launchError = EngineLauncher.launch(context, game)
                if (launchError == null) onDismiss()
            }
            GameActionRow(Icons.Filled.Search, "搜索封面") { showVndbSearch = true }
            GameActionRow(Icons.Filled.Save, "存档管理") {
                startActivityWithFade(context, SaveManagementActivity.createIntent(context, game))
                onDismiss()
            }
            GameActionRow(Icons.Filled.Settings, "引擎设置", onClick = onEngineSettings)
            GameActionRow(Icons.Filled.Delete, "删除游戏", danger = true) { showDeleteConfirm = true }
        }

        launchError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // 底部安全区留白
        Box(Modifier.navigationBarsPadding().height(16.dp))
    }

    if (showVndbSearch) {
        VndbSearchDialog(
            game = game,
            onDismiss = { showVndbSearch = false },
            onBind = { candidate ->
                scope.launch {
                    launchError = "正在绑定封面…"
                    val updated = withContext(Dispatchers.IO) {
                        runCatching { VndbCoverService.bindCandidate(context, game, candidate) }.getOrNull()
                    }
                    if (updated != null) {
                        onGameUpdated(updated)
                        launchError = null
                        showVndbSearch = false
                        onDismiss()
                    } else {
                        launchError = "封面下载失败"
                    }
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除游戏", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "将移除「${game.title}」的应用内记录、设置与缓存，不会删除游戏文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteGame()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun VndbSearchDialog(
    game: ScanGame,
    onDismiss: () -> Unit,
    onBind: (VndbCandidate) -> Unit,
) {
    var keyword by remember { mutableStateOf(game.title) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<VndbCandidate>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun search() {
        val query = keyword.trim()
        if (query.isEmpty() || searching) return
        scope.launch {
            searching = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching { VndbCoverService.searchCandidates(query, 8) }
            }
            candidates = result.getOrDefault(emptyList())
            result.exceptionOrNull()?.let { error = it.message ?: "VNDB 搜索失败" }
            if (candidates.isEmpty() && error == null) error = "未找到匹配结果"
            searching = false
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索 VNDB 封面", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    label = { Text("游戏名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { search() },
                    enabled = !searching,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text(if (searching) "搜索中…" else "搜索", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (candidates.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lazyItems(candidates, key = { it.id }) { candidate ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF2F3F5))
                                    .clickable { onBind(candidate) }
                                    .padding(10.dp),
                            ) {
                                Text(candidate.title.ifBlank { candidate.originalTitle }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (candidate.originalTitle.isNotBlank()) {
                                    Text(candidate.originalTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    listOf(candidate.id, candidate.released, candidate.developer).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun GameActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified,
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
        gridItems(games, key = { it.uri }) { game ->
            GameCard(game, onClick = { onGameClick(game) })
        }
    }
}

@Composable
internal fun GameCard(game: ScanGame, onClick: () -> Unit) {
    Column {
        val coverBitmap by rememberCoverBitmap(game.coverUri)
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
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Tyranor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        game.engine.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            game.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun rememberCoverBitmap(coverUri: String?): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, coverUri) {
        value = withContext(Dispatchers.IO) {
            if (coverUri.isNullOrBlank()) return@withContext null
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(coverUri))?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

private fun EngineType.coverColor(): Color = when (this) {
    EngineType.KIRIKIRI -> Color(0xFF3B5998)
    EngineType.ONS -> Color(0xFF43A047)
    EngineType.TYRANO -> Color(0xFFC6443C)
    EngineType.ARTEMIS -> Color(0xFF7E57C2)
    EngineType.UNKNOWN -> Color(0xFF607D8B)
}
