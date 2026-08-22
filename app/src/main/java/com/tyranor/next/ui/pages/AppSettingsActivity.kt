package com.tyranor.next.ui.pages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyranor.next.settings.AppSettingsStore
import com.tyranor.next.scanner.EngineScanner
import com.tyranor.next.theme.AppThemeColors
import com.tyranor.next.theme.MiuixSettingsTheme
import com.tyranor.next.theme.TyranorNextTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用设置页 Activity：入口见设置页「应用设置」项。 */
class AppSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkMode = AppSettingsStore.getThemeMode(this) == AppSettingsStore.THEME_MODE_DARK
        enableEdgeToEdge(
            statusBarStyle = if (darkMode) androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (darkMode) androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
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

/** 应用设置页：色调轮盘、扫描深度与扫描目录管理、导航栏样式。 */
@Composable
internal fun AppSettingsScreen() {
    val ctx = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showScanDirs by remember { mutableStateOf(false) }
    var scanDirs by remember { mutableStateOf(EngineScanner.loadRoots(ctx)) }
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { u ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    u,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            EngineScanner.saveRoot(ctx, u)
            scanDirs = EngineScanner.loadRoots(ctx)
        }
    }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            ArrowPreference(
                                title = "色调轮盘",
                                startAction = {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(AppThemeColors.primary),
                                    )
                                },
                                endActions = {
                                    Text(
                                        AppThemeColors.primary.toHex(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { showColorPicker = true },
                            )
                        }
                    }
                }
                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            val modeIndex = if (AppSettingsStore.getThemeMode(ctx) == AppSettingsStore.THEME_MODE_DARK) 1 else 0
                            OverlayDropdownPreference(
                                title = "外观模式",
                                items = listOf("浅色", "深色"),
                                selectedIndex = modeIndex,
                                onSelectedIndexChange = { index ->
                                    val mode = if (index == 1) AppSettingsStore.THEME_MODE_DARK else AppSettingsStore.THEME_MODE_LIGHT
                                    AppSettingsStore.setThemeMode(ctx, mode)
                                    AppThemeColors.refresh(ctx)
                                },
                            )
                        }
                    }
                }
                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            var depth by remember { mutableIntStateOf(AppSettingsStore.getScanDepth(ctx)) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "扫描深度",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$depth 级",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = depth.toFloat(),
                                onValueChange = { depth = it.roundToInt().coerceIn(1, 5) },
                                onValueChangeFinished = { AppSettingsStore.setScanDepth(ctx, depth) },
                                valueRange = 1f..5f,
                                showKeyPoints = true,
                                keyPoints = (1..5).map { it.toFloat() },
                                magnetThreshold = 0.25f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ArrowPreference(
                                title = "扫描目录",
                                summary = "${scanDirs.size} 个目录",
                                onClick = { showScanDirs = true },
                            )
                        }
                    }
                }
                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = "圆角液态玻璃导航",
                                checked = AppSettingsStore.navStyleState.value == AppSettingsStore.NAV_STYLE_LIQUID_GLASS,
                                onCheckedChange = { checked ->
                                    AppSettingsStore.setNavStyle(
                                        ctx,
                                        if (checked) AppSettingsStore.NAV_STYLE_LIQUID_GLASS else AppSettingsStore.NAV_STYLE_DEFAULT,
                                    )
                                },
                            )
                        }
                    }
                }
                item { BottomInsetSpacer() }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = AppThemeColors.primary,
            onConfirm = { newColor ->
                AppSettingsStore.setThemeColorHex(ctx, newColor.copy(alpha = 1f).toHex())
                AppThemeColors.refresh(ctx)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showScanDirs) {
        AppAlertDialog(
            onDismissRequest = { showScanDirs = false },
            title = { Text("扫描目录", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (scanDirs.isEmpty()) {
                        Text(
                            "暂无扫描目录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    scanDirs.forEach { dir ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                scanDirName(ctx, dir),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    EngineScanner.removeRoot(ctx, android.net.Uri.parse(dir))
                                    scanDirs = EngineScanner.loadRoots(ctx)
                                },
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    TextButton(
                        onClick = { dirPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("添加目录") }
                }
            },
            confirmButton = { TextButton(onClick = { showScanDirs = false }) { Text("完成") } },
        )
    }
}

/** 扫描目录 URI → 可读目录名（取 SAF documentId 的最后一段，失败回退原 uri）。 */
private fun scanDirName(context: Context, uri: String): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri))
    docId.substringAfterLast(':').substringAfterLast('/').ifBlank { uri }
}.getOrDefault(uri)

/** 色调轮盘弹窗：内嵌 Miuix ColorPicker，确认后应用并持久化主题色。
 *  不允许透明色与黑白灰色（无色相），非法时禁用「确定」并提示。 */
@Composable
private fun ColorPickerDialog(
    initialColor: ComposeColor,
    onConfirm: (ComposeColor) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickerColor by remember { mutableStateOf(initialColor) }
    val invalid = pickerColor.isTransparentOrGray()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("色调轮盘", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                ColorPicker(
                    color = pickerColor,
                    onColorChanged = { pickerColor = it },
                )
                if (invalid) {
                    Text(
                        "不支持透明色或黑白灰色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(enabled = !invalid, onClick = { onConfirm(pickerColor) }) { Text("确定") }
        },
    )
}

/** 透明（alpha < 1）或黑白灰（RGB 三通道差在阈值内，无色相）视为非法主题色。 */
private fun ComposeColor.isTransparentOrGray(): Boolean {
    if (alpha < 1f) return true
    val maxC = maxOf(red, green, blue)
    val minC = minOf(red, green, blue)
    return maxC - minC <= 0.02f
}

/** Compose Color → #RRGGBB（不含透明度，主题色始终不透明）。 */
private fun ComposeColor.toHex(): String {
    val argb = ((alpha * 255f).roundToInt() shl 24) or
        ((red * 255f).roundToInt() shl 16) or
        ((green * 255f).roundToInt() shl 8) or
        (blue * 255f).roundToInt()
    return String.format("#%06X", argb and 0xFFFFFF)
}

/** 列表底部占位：避让系统导航栏。 */
@Composable
private fun BottomInsetSpacer() {
    Box(Modifier.fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
}
