package com.example.tyranornext.scanner

import android.net.Uri

/** 扫描产出的游戏候选。 */
data class ScanGame(
    val title: String,
    val uri: String,
    val engine: EngineType,
    val launchTarget: String,
    val coverUri: String? = null,
    val vndbId: String? = null,
    val metadataTitle: String? = null,
)

/** 简化描述，兼容 SharedPreferences 持久化所需的字段。 */
data class ScannedRoot(
    val uri: String,
    val name: String,
)
