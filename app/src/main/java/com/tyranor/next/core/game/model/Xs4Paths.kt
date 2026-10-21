package com.tyranor.next.core.game.model

import java.io.File
import java.io.IOException

/**
 * System4（xsystem4）路径纯函数：启动侧 `EngineLauncher`、存档镜像侧 `GameSaveManager`、
 * 扫描标题侧共用，保证「引擎实际读写的目录」与「存档管理读取的目录」永远同一处（单一实现）。
 *
 * - 独立存档（默认开）= `<externalFilesDir>/save/<safeSaveName(游戏根)>`；
 * - 关闭 = `<游戏根>/<ini SaveFolder，经 [sanitizeSaveFolder] 校验，缺省 SaveData>`。
 * - 游戏入口 ini 按大小写不敏感查找（`System40.ini` 优先，其次 `AliceStart.ini`），
 *   宿主以该实际文件作为位置参数传入，解决 ini 名大小写与引擎期望不一致的问题。
 */
object Xs4Paths {

    /** `SaveFolder` 缺省或非法时的回落目录名（与引擎默认一致）。 */
    const val DEFAULT_SAVE_FOLDER = "SaveData"

    /** 入口 ini 候选，顺序与引擎 `config_init_with_dir` 一致（先 System40.ini）。 */
    private val INI_CANDIDATES = listOf("System40.ini", "AliceStart.ini")

    private val WINDOWS_DRIVE_RE = Regex("^[A-Za-z]:")

    /** 独立存档目录：`<scopedRoot>/save/<safeSaveName(rootPath)>`（scopedRoot 由调用方传 `getExternalFilesDir(null)`）。 */
    fun scopedSaveDir(scopedRoot: File, rootPath: String): File =
        File(File(scopedRoot, "save"), GamePathUtils.safeSaveName(rootPath))

    /** 非独立存档目录：`<游戏根>/<SaveFolder>`；读取 ini 属于磁盘 IO，调用方须在 IO 线程执行。 */
    fun gameDirSaveDir(root: File): File =
        File(root, sanitizeSaveFolder(readSaveFolder(root)))

    /**
     * 校验 ini 中的 `SaveFolder`（来自不可信文件）：去首尾空白、`\` 视为 `/`；
     * 空串、绝对路径（含 Windows 盘符）、含 `..` 段或仅由 `.` 组成的值一律回落 [DEFAULT_SAVE_FOLDER]。
     */
    fun sanitizeSaveFolder(raw: String?): String {
        val value = raw?.trim()?.replace('\\', '/').orEmpty()
        if (value.isEmpty() || value.startsWith('/') || WINDOWS_DRIVE_RE.containsMatchIn(value)) {
            return DEFAULT_SAVE_FOLDER
        }
        val segments = value.split('/')
        if (segments.any { it == ".." }) return DEFAULT_SAVE_FOLDER
        if (segments.none { it.isNotEmpty() && it != "." }) return DEFAULT_SAVE_FOLDER
        return value
    }

    /** 大小写不敏感地解析游戏入口 ini（仅普通文件）；均缺失返回 null。与 `Xs4Activity.resolveIniFile` 双向互指，修改须同步。 */
    fun resolveIniFile(root: File): File? {
        val children = root.listFiles()?.filter { it.isFile } ?: return null
        for (candidate in INI_CANDIDATES) {
            children.firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun readSaveFolder(root: File): String? {
        val ini = resolveIniFile(root) ?: return null
        val head = try {
            readHead(ini)
        } catch (_: IOException) {
            return null
        }
        return Xs4Ini.parse(head).saveFolder
    }

    private fun readHead(file: File): ByteArray = file.inputStream().use { input ->
        val buffer = ByteArray(Xs4Ini.HEAD_BYTES)
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            total += count
        }
        buffer.copyOf(total)
    }
}
