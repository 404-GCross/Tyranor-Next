package com.example.tyranornext.ui.pages

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tyranornext.settings.EngineSettingsStore
import java.io.File

/** 设置页：统一配置各引擎（KRKR / ONS / Artemis / Tyrano）的全局默认设置。 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    var krVersion by remember { mutableStateOf(EngineSettingsStore.getKrEngineVersion(ctx)) }
    var krKernel by remember { mutableStateOf(EngineSettingsStore.getKrKernel(ctx)) }
    var krScoped by remember { mutableStateOf(EngineSettingsStore.isKrScopedSaveDir(ctx)) }
    var krFont by remember { mutableStateOf(EngineSettingsStore.getKrDefaultFont(ctx)) }
    var krForceFont by remember { mutableStateOf(EngineSettingsStore.isKrForceDefaultFont(ctx)) }
    var krRenderer by remember { mutableStateOf(EngineSettingsStore.getKrRenderer(ctx)) }
    var krDrawThread by remember { mutableStateOf(EngineSettingsStore.getKrSoftwareDrawThread(ctx)) }
    var krSwCompress by remember { mutableStateOf(EngineSettingsStore.getKrSoftwareCompressTex(ctx)) }
    var krOglCompress by remember { mutableStateOf(EngineSettingsStore.getKrOglCompressTex(ctx)) }
    var krMem by remember { mutableStateOf(EngineSettingsStore.getKrMemUsage(ctx)) }
    var krTexsize by remember { mutableStateOf(EngineSettingsStore.getKrOglMaxTexsize(ctx)) }
    var krAccurate by remember { mutableStateOf(EngineSettingsStore.getKrOglAccurateRender(ctx)) }
    var krFps by remember { mutableStateOf(EngineSettingsStore.getKrFpsLimit(ctx)) }

    var ons by remember { mutableStateOf(EngineSettingsStore.loadOns(ctx)) }

    var artVersion by remember { mutableStateOf(EngineSettingsStore.getArtEngineVersion(ctx)) }
    var artRotate by remember { mutableStateOf(EngineSettingsStore.isArtRotateScreen(ctx)) }
    var artPatch by remember { mutableStateOf(EngineSettingsStore.getArtAutoPatch(ctx)) }

    var tyExternal by remember { mutableStateOf(EngineSettingsStore.isTyranoExternalNetwork(ctx)) }
    var tyScoped by remember { mutableStateOf(EngineSettingsStore.isTyranoScopedSaveDir(ctx)) }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = importFont(ctx, uri)
            if (path != null) {
                krFont = path
            }
        }
    }

    val isSdl3 = krKernel == EngineSettingsStore.KERNEL_KRKRSDL3
    val krIs134126 = krVersion == EngineSettingsStore.KR_134 || krVersion == EngineSettingsStore.KR_126

    // 编辑→保存模型：控件仅更新本地状态，点顶部保存按钮才统一写盘
    fun saveAll() {
        EngineSettingsStore.setKrEngineVersion(ctx, krVersion)
        EngineSettingsStore.setKrKernel(ctx, krKernel)
        EngineSettingsStore.setKrScopedSaveDir(ctx, krScoped)
        EngineSettingsStore.setKrDefaultFont(ctx, krFont)
        EngineSettingsStore.setKrForceDefaultFont(ctx, krForceFont)
        EngineSettingsStore.setKrRenderer(ctx, krRenderer)
        EngineSettingsStore.setKrSoftwareDrawThread(ctx, krDrawThread)
        EngineSettingsStore.setKrSoftwareCompressTex(ctx, krSwCompress)
        EngineSettingsStore.setKrOglCompressTex(ctx, krOglCompress)
        EngineSettingsStore.setKrMemUsage(ctx, krMem)
        EngineSettingsStore.setKrOglMaxTexsize(ctx, krTexsize)
        EngineSettingsStore.setKrOglAccurateRender(ctx, krAccurate)
        EngineSettingsStore.setKrFpsLimit(ctx, krFps)
        EngineSettingsStore.saveOns(ctx, ons)
        EngineSettingsStore.setArtEngineVersion(ctx, artVersion)
        EngineSettingsStore.setArtRotateScreen(ctx, artRotate)
        EngineSettingsStore.setArtAutoPatch(ctx, artPatch)
        EngineSettingsStore.setTyranoExternalNetwork(ctx, tyExternal)
        EngineSettingsStore.setTyranoScopedSaveDir(ctx, tyScoped)
    }

    Column(modifier.fillMaxSize()) {
        // 顶部栏：标题居左 + 右侧保存按钮
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    saveAll()
                    android.widget.Toast.makeText(ctx, "引擎设置已保存", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Save, contentDescription = "保存设置", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        LazyListPlaceholder(
            krVersion, krKernel, krScoped, krFont, krForceFont, krRenderer, krDrawThread,
            krSwCompress, krOglCompress, krMem, krTexsize, krAccurate, krFps, isSdl3, krIs134126,
            ons, artVersion, artRotate, artPatch, tyExternal, tyScoped, fontLauncher,
            onKrVersion = { krVersion = it },
            onKrKernel = { krKernel = it },
            onKrScoped = { krScoped = it },
            onKrForceFont = { krForceFont = it },
            onKrRenderer = { krRenderer = it },
            onKrDrawThread = { krDrawThread = it },
            onKrSwCompress = { krSwCompress = it },
            onKrOglCompress = { krOglCompress = it },
            onKrMem = { krMem = it },
            onKrTexsize = { krTexsize = it },
            onKrAccurate = { krAccurate = it },
            onKrFps = { krFps = it },
            onResetKrFont = { krFont = "" },
            onOns = { ons = it },
            onArtVersion = { artVersion = it },
            onArtRotate = { artRotate = it },
            onArtPatch = { artPatch = it },
            onTyExternal = { tyExternal = it },
            onTyScoped = { tyScoped = it },
        )
    }
}

// 占位，实际列表在下方 LazyListPlaceholder 中
private typealias FontPickerLauncher = androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>

@Composable
private fun LazyListPlaceholder(
    krVersion: String, krKernel: String, krScoped: Boolean, krFont: String, krForceFont: Boolean,
    krRenderer: String, krDrawThread: String, krSwCompress: String, krOglCompress: String,
    krMem: String, krTexsize: String, krAccurate: String, krFps: String, isSdl3: Boolean, krIs134126: Boolean,
    ons: EngineSettingsStore.Ons, artVersion: String, artRotate: Boolean, artPatch: String,
    tyExternal: Boolean, tyScoped: Boolean, fontLauncher: FontPickerLauncher,
    onKrVersion: (String) -> Unit, onKrKernel: (String) -> Unit, onKrScoped: (Boolean) -> Unit,
    onKrForceFont: (Boolean) -> Unit, onKrRenderer: (String) -> Unit, onKrDrawThread: (String) -> Unit,
    onKrSwCompress: (String) -> Unit, onKrOglCompress: (String) -> Unit, onKrMem: (String) -> Unit,
    onKrTexsize: (String) -> Unit, onKrAccurate: (String) -> Unit, onKrFps: (String) -> Unit,
    onResetKrFont: () -> Unit, onOns: (EngineSettingsStore.Ons) -> Unit,
    onArtVersion: (String) -> Unit, onArtRotate: (Boolean) -> Unit, onArtPatch: (String) -> Unit,
    onTyExternal: (Boolean) -> Unit, onTyScoped: (Boolean) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EngineCard("KRKR", "KRKR", "Kirikiri2 · 吉里吉里") {
                ListSwitch("独立存档目录", krScoped, onKrScoped)
                SingleChoiceRow("引擎版本", optionLabel(krVersion, KR_SELECT_MAP), KR_SELECT_MAP, krVersion, onKrVersion)
                SingleChoiceRow(
                    "引擎内核",
                    when (krKernel) {
                        EngineSettingsStore.KERNEL_KRKRSDL3 -> "krkrsdl3"
                        EngineSettingsStore.KERNEL_KIRIKIRI2 -> "吉里吉里2"
                        else -> "自动"
                    },
                    mapOf(
                        EngineSettingsStore.KR_AUTO to "自动",
                        EngineSettingsStore.KERNEL_KIRIKIRI2 to "吉里吉里2",
                        EngineSettingsStore.KERNEL_KRKRSDL3 to "krkrsdl3",
                    ), krKernel, onKrKernel,
                )
                if (!isSdl3) {
                    FontRow("默认字体", krFont.ifEmpty { "内置字体" }, onResetKrFont, { fontLauncher.launch("*/*") })
                    if (krVersion != EngineSettingsStore.KR_126) {
                        ListSwitch("强制使用默认字体", krForceFont, onKrForceFont)
                    }
                    ListSwitch("OpenGL 精确渲染", krAccurate == "1") { b -> onKrAccurate(if (b) "1" else "0") }
                    SingleChoiceRow("渲染器", krRendererLabel(krRenderer), KR_RENDERER_MAP, krRenderer.ifEmpty { "default" }) {
                        onKrRenderer(if (it == "default") "" else it)
                    }
                    if (krRenderer == "" || krRenderer == EngineSettingsStore.RENDERER_SOFTWARE) {
                        SingleChoiceRow("软件渲染线程数", if (krDrawThread == "" || krDrawThread == "0") "自动" else krDrawThread, KR_THREAD_MAP.plus("0" to "自动"), krDrawThread, onKrDrawThread)
                        SingleChoiceRow("软件纹理压缩", choiceLabel(krSwCompress, KR_SW_COMPRESS_MAP).ifEmpty { "引擎默认" }, KR_SW_COMPRESS_MAP, krSwCompress, onKrSwCompress)
                    }
                    if (krRenderer == "" || krRenderer == EngineSettingsStore.RENDERER_OPENGL) {
                        SingleChoiceRow("OpenGL 纹理压缩", choiceLabel(krOglCompress, KR_OGL_COMPRESS_MAP).ifEmpty { "引擎默认" }, KR_OGL_COMPRESS_MAP, krOglCompress, onKrOglCompress)
                        SingleChoiceRow("最大纹理尺寸", if (krTexsize.isEmpty() || krTexsize == "0") "自动" else krTexsize, KR_TEXSIZE_MAP, krTexsize, onKrTexsize)
                    }
                    SingleChoiceRow("内存用量", choiceLabel(krMem, KR_MEM_MAP).ifEmpty { "引擎默认" }, KR_MEM_MAP, krMem, onKrMem)
                    if (!krIs134126) {
                        SingleChoiceRow("FPS 限制", choiceLabel(krFps, KR_FPS_MAP).ifEmpty { "引擎默认" }, KR_FPS_MAP, krFps, onKrFps)
                    }
                }
            }
        }

        item {
            EngineCard("ONS", "ONS", "ONScripter · Yuri") {
                ListSwitch("独立存档目录", ons.scopedSaveDir) { b -> onOns(ons.copy(scopedSaveDir = b)) }
                ListSwitch("全屏拉伸", ons.stretchFull) { b -> onOns(ons.copy(stretchFull = b)) }
                ListSwitch("忽略刘海", ons.ignoreCutout) { b -> onOns(ons.copy(ignoreCutout = b)) }
                ListSwitch("禁用视频", ons.disableVideo) { b -> onOns(ons.copy(disableVideo = b)) }
                ListSwitch("画面锐化", ons.sharpness) { b -> onOns(ons.copy(sharpness = b)) }
                if (ons.sharpness) {
                    SingleChoiceRow("锐化强度", ons.sharpnessValue, ONS_SHARPNESS_MAP, ons.sharpnessValue) { it ->
                        onOns(ons.copy(sharpnessValue = it))
                    }
                }
                SingleChoiceRow(
                    "文本编码",
                    when (ons.encoding) { "sjis" -> "Shift-JIS"; "utf8" -> "UTF-8"; else -> "GBK" },
                    mapOf("gbk" to "GBK", "sjis" to "Shift-JIS", "utf8" to "UTF-8"),
                    ons.encoding, { it -> onOns(ons.copy(encoding = it)) },
                )
            }
        }

        item {
            EngineCard("Artemis", "Artemis", "Artemis · 红茶") {
                SingleChoiceRow("引擎版本", artVersionLabel(artVersion), ART_VERSION_MAP, artVersion, onArtVersion)
                ListSwitch("画面反转", artRotate, onArtRotate)
                SingleChoiceRow("自动补丁", artPatchLabel(artPatch), ART_PATCH_MAP, artPatch, onArtPatch)
            }
        }

        item {
            EngineCard("Tyrano", "Tyrano", "TyranoScript · 网页脚本") {
                ListSwitch("允许加载外部网络资源", tyExternal, onTyExternal)
                ListSwitch("独立存档目录", tyScoped, onTyScoped)
            }
        }

        item {
            Text(
                "引擎设置保存后，启动对应引擎的游戏时自动生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item { Box(Modifier.fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) }
    }
}

@Composable
private fun EngineCard(title: String, header: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(header, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
            content()
        }
    }
}

@Composable
private fun ListSwitch(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

/** 单选下拉行：点击行弹出单选对话框，选中后回调。 */
@Composable
private fun SingleChoiceRow(
    label: String,
    value: String,
    options: Map<String, String>,
    current: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (k, optionLabel) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(k); open = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = k == current, onClick = { onSelect(k); open = false })
                            Text(optionLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

/** 字体行：点击弹出「内置字体 / 选择字体文件」，选择文件时启动系统文件选择器。 */
@Composable
private fun FontRow(label: String, value: String, onReset: () -> Unit, onPick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { onReset(); open = false }.padding(vertical = 10.dp)) {
                        Text("使用内置字体", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(Modifier.fillMaxWidth().clickable { open = false; onPick() }.padding(vertical = 10.dp)) {
                        Text("选择字体文件…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

private fun importFont(ctx: android.content.Context, uri: Uri): String? = try {
    val displayName = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    val name = (displayName ?: "font.ttf").substringAfterLast('/').substringAfterLast('\\')
    if (!listOf(".ttf", ".ttc", ".otf", ".otc").any { name.lowercase().endsWith(it) }) return null
    val dir = File(ctx.filesDir, "fonts")
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val target = File(dir, name)
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { out -> input.copyTo(out) }
    } ?: return null
    target.absolutePath
} catch (t: Throwable) {
    null
}

private fun krRendererLabel(v: String): String = when (v) {
    EngineSettingsStore.RENDERER_OPENGL -> "OpenGL"
    EngineSettingsStore.RENDERER_SOFTWARE -> "软件渲染"
    else -> "引擎默认"
}

private fun optionLabel(v: String, map: Map<String, String>): String = map[v] ?: v.ifEmpty { "引擎默认" }
private fun choiceLabel(v: String, map: Map<String, String>): String = map[v].orEmpty()

private fun artVersionLabel(v: String): String = when (v) {
    EngineSettingsStore.ART_ENGINE_V1 -> "V1"
    EngineSettingsStore.ART_ENGINE_V2 -> "V2"
    EngineSettingsStore.ART_ENGINE_V3 -> "V3"
    else -> "自动"
}
private fun artPatchLabel(v: String): String = when (v) {
    EngineSettingsStore.AUTO_PATCH_AUTO -> "自动"
    EngineSettingsStore.AUTO_PATCH_OFF -> "关闭"
    else -> "启动时询问"
}

private val KR_SELECT_MAP = mapOf(
    EngineSettingsStore.KR_AUTO to "自动",
    EngineSettingsStore.KR_139 to "1.3.9",
    EngineSettingsStore.KR_134 to "1.3.4",
    EngineSettingsStore.KR_126 to "1.2.6",
)
private val KR_RENDERER_MAP = mapOf(
    "default" to "引擎默认",
    EngineSettingsStore.RENDERER_SOFTWARE to "软件渲染",
    EngineSettingsStore.RENDERER_OPENGL to "OpenGL",
)
private val KR_THREAD_MAP = (1..8).associate { it.toString() to "$it 线程" }
private val KR_SW_COMPRESS_MAP = mapOf("none" to "无", "halfline" to "半行", "lz4" to "LZ4", "lz4+tlg5" to "LZ4+TLG5")
private val KR_OGL_COMPRESS_MAP = mapOf("none" to "无", "half" to "半精度", "etc2" to "ETC2", "pvrtc" to "PVRTC")
private val KR_MEM_MAP = mapOf(
    EngineSettingsStore.MEM_USAGE_UNLIMITED to "不限制",
    EngineSettingsStore.MEM_USAGE_HIGH to "高",
    EngineSettingsStore.MEM_USAGE_MEDIUM to "中",
    EngineSettingsStore.MEM_USAGE_LOW to "低",
)
private val KR_TEXSIZE_MAP = (listOf("0") + listOf(1024, 2048, 4096, 8192, 16384).map { it.toString() })
    .associateWith { if (it == "0") "自动" else it }
private val KR_FPS_MAP = mapOf("60" to "60", "45" to "45", "30" to "30", "15" to "15")
private val ONS_SHARPNESS_MAP = mapOf("1" to "1.0", "2" to "2.0", "3" to "3.0", "4" to "4.0", "5" to "5.0")
private val ART_VERSION_MAP = mapOf(
    EngineSettingsStore.ART_ENGINE_AUTO to "自动",
    EngineSettingsStore.ART_ENGINE_V1 to "V1",
    EngineSettingsStore.ART_ENGINE_V2 to "V2",
    EngineSettingsStore.ART_ENGINE_V3 to "V3",
)
private val ART_PATCH_MAP = mapOf(
    EngineSettingsStore.AUTO_PATCH_ASK to "启动时询问",
    EngineSettingsStore.AUTO_PATCH_AUTO to "自动",
    EngineSettingsStore.AUTO_PATCH_OFF to "关闭",
)