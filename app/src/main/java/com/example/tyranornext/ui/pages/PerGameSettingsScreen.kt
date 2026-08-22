package com.example.tyranornext.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.tyranornext.scanner.EngineType
import com.example.tyranornext.scanner.ScanGame
import com.example.tyranornext.settings.EngineSettingsStore
import com.example.tyranornext.settings.PerGameSettingsStore
import com.example.tyranornext.theme.NavWhite
import org.json.JSONObject

/**
 * 单游戏（应用级）引擎设置页。每项基于「覆盖 ?: 全局」，可单独切回“跟随全局”。
 * 顶部右侧保存图标提交覆盖；左上返回。
 */
@Composable
fun PerGameSettingsScreen(game: ScanGame, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val gid = game.uri

    // 覆盖值（null=跟随全局）
    var krVersion by remember { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, PerGameSettingsStore.F_ENGINE_VERSION)) }
    var krKernel by remember { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, PerGameSettingsStore.F_ENGINE_KERNEL)) }
    var krScoped by remember { mutableStateOf(PerGameSettingsStore.getBool(ctx, gid, PerGameSettingsStore.F_SCOPED_SAVE_DIR)) }
    var krFont by remember { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, PerGameSettingsStore.F_DEFAULT_FONT)) }
    var krForceFont by remember { mutableStateOf(PerGameSettingsStore.getBool(ctx, gid, PerGameSettingsStore.F_FORCE_DEFAULT_FONT)) }
    val krRender = PerGameSettingsStore.KR_FIELDS.associateWith { field ->
        remember(field) { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, field)) }
    }

    var artVersion by remember { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, PerGameSettingsStore.F_ART_VERSION)) }
    var artRotate by remember { mutableStateOf(PerGameSettingsStore.getBool(ctx, gid, PerGameSettingsStore.F_ART_ROTATE)) }
    var artPatch by remember { mutableStateOf(PerGameSettingsStore.getStr(ctx, gid, PerGameSettingsStore.F_ART_PATCH)) }

    val onsOverride = remember { mutableStateOf(PerGameSettingsStore.loadOnsOverride(ctx, gid) ?: JSONObject()) }
    var onsScoped by remember { mutableStateOf(onsBool(onsOverride.value, "scopedsavedir")) }
    var onsStretch by remember { mutableStateOf(onsBool(onsOverride.value, "strechfull")) }
    var onsCutout by remember { mutableStateOf(onsBool(onsOverride.value, "ignorecutout")) }
    var onsNoVideo by remember { mutableStateOf(onsBool(onsOverride.value, "disablevideo")) }
    var onsSharp by remember { mutableStateOf(onsBool(onsOverride.value, "sharpness")) }
    var onsSharpVal by remember { mutableStateOf(onsStr(onsOverride.value, "sharpness_value", "2")) }
    var onsEnc by remember { mutableStateOf(onsStr(onsOverride.value, "encoding", "gbk")) }

    var tyExternal by remember { mutableStateOf(PerGameSettingsStore.getBool(ctx, gid, "ty_external")) }
    var tyScoped by remember { mutableStateOf(PerGameSettingsStore.getBool(ctx, gid, "ty_scoped")) }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val p = copyFontToPrivate(ctx, uri)
            if (p != null) krFont = p
        }
    }

    val globalKrVersion = EngineSettingsStore.getKrEngineVersion(ctx)
    val globalKrKernel = EngineSettingsStore.getKrKernel(ctx)
    val globalKrScoped = EngineSettingsStore.isKrScopedSaveDir(ctx)
    val globalKrFont = EngineSettingsStore.getKrDefaultFont(ctx)
    val globalForce = EngineSettingsStore.isKrForceDefaultFont(ctx)
    val globalRenderer = EngineSettingsStore.getKrRenderer(ctx)
    val globalOns = remember { EngineSettingsStore.loadOns(ctx) }
    val globalArtVersion = EngineSettingsStore.getArtEngineVersion(ctx)
    val globalArtRotate = EngineSettingsStore.isArtRotateScreen(ctx)
    val globalArtPatch = EngineSettingsStore.getArtAutoPatch(ctx)
    val globalTyExternal = EngineSettingsStore.isTyranoExternalNetwork(ctx)
    val globalTyScoped = EngineSettingsStore.isTyranoScopedSaveDir(ctx)

    val isSdl3 = (krKernel ?: globalKrKernel) == EngineSettingsStore.KERNEL_KRKRSDL3
    val effVersion = krVersion ?: globalKrVersion

    fun save() {
        PerGameSettingsStore.setStr(ctx, gid, PerGameSettingsStore.F_ENGINE_VERSION, krVersion)
        PerGameSettingsStore.setStr(ctx, gid, PerGameSettingsStore.F_ENGINE_KERNEL, krKernel)
        PerGameSettingsStore.setBool(ctx, gid, PerGameSettingsStore.F_SCOPED_SAVE_DIR, krScoped)
        PerGameSettingsStore.setStr(ctx, gid, PerGameSettingsStore.F_DEFAULT_FONT, krFont)
        PerGameSettingsStore.setBool(ctx, gid, PerGameSettingsStore.F_FORCE_DEFAULT_FONT, krForceFont)
        krRender.forEach { (field, st) -> PerGameSettingsStore.setStr(ctx, gid, field, st.value) }
        PerGameSettingsStore.setStr(ctx, gid, PerGameSettingsStore.F_ART_VERSION, artVersion)
        PerGameSettingsStore.setBool(ctx, gid, PerGameSettingsStore.F_ART_ROTATE, artRotate)
        PerGameSettingsStore.setStr(ctx, gid, PerGameSettingsStore.F_ART_PATCH, artPatch)
        val onsObj = JSONObject()
        putIfNotNull(onsObj, "scopedsavedir", onsScoped)
        putIfNotNull(onsObj, "strechfull", onsStretch)
        putIfNotNull(onsObj, "ignorecutout", onsCutout)
        putIfNotNull(onsObj, "disablevideo", onsNoVideo)
        putIfNotNull(onsObj, "sharpness", onsSharp)
        putIfNotNull(onsObj, "sharpness_value", onsSharpVal)
        putIfNotNull(onsObj, "encoding", onsEnc)
        PerGameSettingsStore.setOnsOverride(ctx, gid, onsObj)
        PerGameSettingsStore.setBool(ctx, gid, "ty_external", tyExternal)
        PerGameSettingsStore.setBool(ctx, gid, "ty_scoped", tyScoped)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(game.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { save(); android.widget.Toast.makeText(ctx, "已保存", android.widget.Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Filled.Save, contentDescription = "保存", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (game.engine) {
                EngineType.KIRIKIRI -> item {
                    SectionCard("KRKR") {
                        OverrideSwitch("独立存档目录", globalKrScoped, krScoped) { krScoped = it }
                        OverrideChoice("引擎版本", KR_VERSION_MAP2, globalKrVersion, krVersion) { krVersion = it }
                        OverrideChoice("引擎内核", KR_KERNEL_MAP2, globalKrKernel, krKernel) { krKernel = it }
                        if (!isSdl3) {
                            OverrideFont("默认字体", globalKrFont, krFont, onReset = { krFont = "" }, onPick = { fontLauncher.launch("*/*") })
                            OverrideSwitch("强制默认字体", globalForce, krForceFont) { krForceFont = it }
                            OverrideChoice("渲染器", KR_RENDERER_MAP2, globalRenderer, krRender[PerGameSettingsStore.F_RENDERER]!!.value) {
                                krRender[PerGameSettingsStore.F_RENDERER]!!.value = it
                            }
                        }
                    }
                }
                EngineType.ONS -> item {
                    SectionCard("ONS") {
                        OverrideSwitch("独立存档目录", globalOns.scopedSaveDir, onsScoped) { onsScoped = it }
                        OverrideSwitch("全屏拉伸", globalOns.stretchFull, onsStretch) { onsStretch = it }
                        OverrideSwitch("忽略刘海", globalOns.ignoreCutout, onsCutout) { onsCutout = it }
                        OverrideSwitch("禁用视频", globalOns.disableVideo, onsNoVideo) { onsNoVideo = it }
                        OverrideSwitch("画面锐化", globalOns.sharpness, onsSharp) { onsSharp = it }
                        OverrideChoice("文本编码", ONS_ENCODING_MAP2, globalOns.encoding.decode(), onsEnc) { onsEnc = it }
                    }
                }
                EngineType.ARTEMIS -> item {
                    SectionCard("Artemis") {
                        OverrideChoice("引擎版本", ART_VERSION_MAP2, globalArtVersion, artVersion) { artVersion = it }
                        OverrideSwitch("画面反转", globalArtRotate, artRotate) { artRotate = it }
                        OverrideChoice("自动补丁", ART_PATCH_MAP2, globalArtPatch, artPatch) { artPatch = it }
                    }
                }
                EngineType.TYRANO, EngineType.UNKNOWN -> item {
                    SectionCard("Tyrano") {
                        OverrideSwitch("允许外部网络", globalTyExternal, tyExternal) { tyExternal = it }
                        OverrideSwitch("独立存档目录", globalTyScoped, tyScoped) { tyScoped = it }
                    }
                }
            }

            item { Box(Modifier.fillMaxWidth().navigationBarsPadding().height(12.dp)) }
        }
    }
}

private fun navigationBarsPadding(): Int = 24

// ───────────────────────── 覆盖行组件 ─────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = NavWhite),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** 覆盖版单选行：值为未覆盖时显示“跟随全局(…)”。 */
@Composable
private fun OverrideChoice(label: String, options: Map<String, String>, global: String, override: String?, onSet: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val following = override == null
    val effValue = override ?: global
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            if (following) "跟随全局 · ${labelOf(effValue, options)}" else labelOf(effValue, options),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (open) {
        AppAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                LazyColumn {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { onSet(null); open = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = following, onClick = { onSet(null); open = false })
                            Text("跟随全局 · ${labelOf(global, options)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    options.forEach { (k, text) ->
                        item(key = k) {
                            Row(Modifier.fillMaxWidth().clickable { onSet(k); open = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !following && override == k, onClick = { onSet(k); open = false })
                                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

/** 覆盖版开关行：三态（跟随全局 / 开 / 关）。 */
@Composable
private fun OverrideSwitch(label: String, global: Boolean, override: Boolean?, onSet: (Boolean?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            when {
                override == null -> "跟随全局（${if (global) "开" else "关"}）"
                override == true -> "开"
                else -> "关"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (open) {
        AppAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    OverrideRadio("跟随全局（${if (global) "开" else "关"}）", override == null) { onSet(null); open = false }
                    OverrideRadio("开", override == true) { onSet(true); open = false }
                    OverrideRadio("关", override == false) { onSet(false); open = false }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun OverrideRadio(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun OverrideFont(label: String, global: String, override: String?, onReset: () -> Unit, onPick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val following = override == null
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge) }
        Text(
            if (following) "跟随全局（${global.ifEmpty { "内置字体" }}）" else override.ifEmpty { "内置字体" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (open) {
        AppAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { onReset(); open = false }.padding(vertical = 8.dp)) { Text("跟随全局", style = MaterialTheme.typography.bodyLarge) }
                    Row(Modifier.fillMaxWidth().clickable { open = false; onPick() }.padding(vertical = 8.dp)) { Text("选择字体文件…", style = MaterialTheme.typography.bodyLarge) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

private fun labelOf(v: String, map: Map<String, String>): String = map[v] ?: v.ifEmpty { "内置字体" }

private fun copyFontToPrivate(ctx: android.content.Context, uri: android.net.Uri): String? = try {
    val name = (uri.lastPathSegment ?: "font.ttf").substringAfterLast('/').substringAfterLast('\\')
    val dir = java.io.File(ctx.filesDir, "fonts")
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val target = java.io.File(dir, name)
    ctx.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { out -> input.copyTo(out) } } ?: return null
    target.absolutePath
} catch (t: Throwable) { null }

private fun onsBool(o: JSONObject, key: String): Boolean? = if (o.has(key)) o.optBoolean(key) else null
private fun onsStr(o: JSONObject, key: String, def: String): String? = if (o.has(key)) o.optString(key, def) else null
private fun putIfNotNull(o: JSONObject, key: String, v: Boolean?) { if (v != null) o.put(key, v) else o.remove(key) }
private fun putIfNotNull(o: JSONObject, key: String, v: String?) { if (v != null) o.put(key, v) else o.remove(key) }
private fun String.decode(): String = if (this == "sjis") "sjis" else if (this == "utf8") "utf8" else "gbk"

private val KR_VERSION_MAP2 = mapOf(
    EngineSettingsStore.KR_AUTO to "自动",
    EngineSettingsStore.KR_139 to "1.3.9",
    EngineSettingsStore.KR_134 to "1.3.4",
    EngineSettingsStore.KR_126 to "1.2.6",
)
private val KR_KERNEL_MAP2 = mapOf(
    EngineSettingsStore.KR_AUTO to "自动",
    EngineSettingsStore.KERNEL_KIRIKIRI2 to "吉里吉里2",
    EngineSettingsStore.KERNEL_KRKRSDL3 to "krkrsdl3",
)
private val KR_RENDERER_MAP2 = mapOf(
    EngineSettingsStore.RENDERER_SOFTWARE to "软件渲染",
    EngineSettingsStore.RENDERER_OPENGL to "OpenGL",
)
private val ONS_ENCODING_MAP2 = mapOf("gbk" to "GBK", "sjis" to "Shift-JIS", "utf8" to "UTF-8")
private val ART_VERSION_MAP2 = mapOf(
    EngineSettingsStore.ART_ENGINE_AUTO to "自动",
    EngineSettingsStore.ART_ENGINE_V1 to "V1",
    EngineSettingsStore.ART_ENGINE_V2 to "V2",
    EngineSettingsStore.ART_ENGINE_V3 to "V3",
)
private val ART_PATCH_MAP2 = mapOf(
    EngineSettingsStore.AUTO_PATCH_ASK to "启动时询问",
    EngineSettingsStore.AUTO_PATCH_AUTO to "自动",
    EngineSettingsStore.AUTO_PATCH_OFF to "关闭",
)
