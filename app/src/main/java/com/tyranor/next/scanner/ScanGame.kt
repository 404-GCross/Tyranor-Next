package com.tyranor.next.scanner

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
    /** 用户通过“启动文件”手动指定的启动入口文件名（相对游戏目录）；null 表示自动。 */
    val launchFile: String? = null,
    /** 最近一次打开时间戳（毫秒），仅最近打开列表展示使用；0 表示未知。 */
    val openTime: Long = 0,
)

/** 简化描述，兼容 SharedPreferences 持久化所需的字段。 */
data class ScannedRoot(
    val uri: String,
    val name: String,
)
