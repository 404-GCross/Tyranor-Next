package com.tyranor.next.core.engine.external

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * RTP 导入被拒的原因（类型化错误码，不带文案）。
 *
 * core 层禁止把本地化文案当返回值（AGENT.md 错误处理协议）：此处只给类型与数值，
 * 可展示文案由 UI 层组装（见 `ui/settings/RpgMakerRtpMessages.kt`）。
 *
 * 导入入口直接返回本类型（null = 成功），不再维护「最近一次失败原因」这类全局状态：
 * 全局槽在并发/连续导入下会把上一次的原因挂到本次失败上，也让调用方必须记住「先读返回值
 * 再读原因」的隐式顺序。
 */
sealed interface RtpImportRejection {

    /** 条目数超过上限。 */
    data class EntryCountExceeded(val count: Int, val limit: Int) : RtpImportRejection

    /** 单条目解压后超过上限。 */
    data class EntrySizeExceeded(val size: Long, val limit: Long) : RtpImportRejection

    /** 累计解压量超过上限。 */
    data class TotalSizeExceeded(val size: Long, val limit: Long) : RtpImportRejection

    /** 无法创建目录（参数为目录名或条目名）。 */
    data class DirectoryCreateFailed(val name: String) : RtpImportRejection

    /**
     * 读写失败（源不可读、临时目录创建失败、替换目标失败等）。
     *
     * [name] 为相关路径/目录名，便于用户定位是「选错了目录」还是「存储空间不足」。
     */
    data class IoFailed(val name: String) : RtpImportRejection

    /** 源里没有任何可导入内容（空 zip / 空目录，或全被元数据过滤掉）。 */
    data object EmptyArchive : RtpImportRejection
}

/**
 * RPGM 外置模块的共享目录管理（RTP / 自定义字体）。
 *
 * 目录契约由 RPGM 插件固定（插件 `MainActivity` 将
 * `/sdcard/JoiPlay/RTP/<RPGXP|RPGVX|RPGVXACE|mkxp-z>/app` 挂载为 RTP 根）：
 * 因此 RTP 与自定义字体必须落在共享存储，App 私有目录外置插件不可读。
 *
 * 所有方法允许失败，且失败一律以**类型化结果**表达（`RtpImportRejection?` / null 或
 * `Boolean` / `String?`），由调用方按非致命处理；界面文案在 ui 层组装。
 */
object RpgMakerRuntimeEnvironment {
    private const val RTP_PARENT = "JoiPlay/RTP"
    private const val FONT_PARENT = "JoiPlay/fonts"
    private const val IMPORT_TEMP_DIR = ".import_tmp"

    /** 备份式替换用的旧目录暂存名（与 [IMPORT_TEMP_DIR] 同层，替换成功即删）。 */
    private const val IMPORT_BACKUP_DIR = ".import_backup"

    /**
     * 提交标记名（与 [IMPORT_BACKUP_DIR] 同层）。
     *
     * 备份式替换在「旧目录已移开、新内容未顶上」之间被杀时，`target` 缺失而旧数据在
     * 备份里，尚可归位；但 `copyRecursively` 只拷了一半就被杀时，`target` 存在且非空，
     * 仅凭目录是否存在无法与「完整导入」区分。因此替换期间先落 `.importing` 标记，
     * 成功收尾才删除——**标记存在即表示 target 不可信**（见 [recoverPendingImport]）。
     */
    private const val IMPORT_MARKER_FILE = ".importing"

    /**
     * 恢复/提交/清除共用 `.import_backup`、`.importing` 这两个固定名，是同一台状态机：
     * 导入在 IO 线程、`isRtpImported`/清除在 UI 线程，交错会互相踩踏（如把「替换中」的
     * 半成品 target 判成有效、或把刚写下的标记当残留删掉），因此这三段必须互斥。
     */
    private val importLock = Any()

    // 用户挑选的 RTP 包解压上限。数值与 NativePluginInstaller.unzipSafely **不同**
    // （那边是 32 条目 / 512MiB 总量，面向内置插件包）：RTP 是整包素材，条目数与总量
    // 都大得多，因此这里给到 10 万条目 / 4GiB、单条目同为 256MiB
    // （早期版本用 2 万 / 1GiB，对含音频的整包素材偏紧，属功能收窄）。
    // 三者都只是为了拦住 zip 炸弹（高压缩比）这一类构造，正常素材包远不会触及。
    private const val MAX_ZIP_ENTRIES = 100_000
    private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L
    private const val ZIP_BUFFER_SIZE = 64 * 1024

    private val FONT_EXTENSIONS = listOf(".ttf", ".ttc", ".otf", ".otc")

    fun rtpDirName(gameType: String): String = when (gameType.trim().lowercase(Locale.ROOT)) {
        "rpgmxp" -> "RPGXP"
        "rpgmvx" -> "RPGVX"
        "mkxp-z" -> "mkxp-z"
        else -> "RPGVXACE"
    }

    fun rtpDir(gameType: String): File =
        File(Environment.getExternalStorageDirectory(), "$RTP_PARENT/${rtpDirName(gameType)}")

    /**
     * RTP 根目录（`.../<子类型>/app`）。
     *
     * 顺带做一次**待替换现场的归位**：备份式替换若在「旧目录已改名、新内容未顶上」之间
     * 被杀，这里把旧目录放回去。少了这一步，[isRtpImported] 会报「未导入」、游戏也读不到
     * RTP，而盘上其实还留着完整的旧数据。归位是幂等的（目标已有效时不做任何事）。
     */
    fun rtpAppDir(gameType: String): File {
        val dir = File(rtpDir(gameType), "app")
        recoverPendingImport(dir)
        return dir
    }

    /**
     * RTP 根目录（`.../<子类型>/app`），**仅在无未收尾现场时**返回。
     *
     * 与 [rtpAppDir] 的区别：归位失败（如「标记已删、备份 rename 回 target 失败」留下
     * 「无标记 + 仅备份 + target 缺失」）时返回 null。**会向 app 目录写入**的调用方必须用本
     * 方法——若此时先把 target 建成非空（如 mkdirs + 写 sf.sf2），下一次恢复会把备份当成
     * 「已成功替换的陈旧孤儿」删掉，旧 RTP 永久丢失。
     */
    internal fun rtpAppDirIfReady(gameType: String): File? = rtpAppDirIfReady(rtpDir(gameType))

    /** [rtpAppDirIfReady] 的可测重载：以 [root] 为 `<子类型>` 目录，[delete] 可注入失败。 */
    internal fun rtpAppDirIfReady(
        root: File,
        delete: (File) -> Boolean = { it.deleteRecursively() },
    ): File? {
        val dir = File(root, "app")
        return if (recoverPendingImport(dir, delete)) dir else null
    }

    /**
     * 把中断的替换留下的现场归位（幂等）。
     *
     * 现场有两种形态：
     *  - **提交标记 `.importing`**：上一次替换未正常收尾，`target` 可能是半成品（备份式
     *    替换在「旧目录已改名、新内容未顶上」之间被杀，或 `copyRecursively` 只拷了一半）。
     *    有备份 → 删掉半成品 target、把备份归位；无备份 → 首次导入的半成品，无旧数据可
     *    保护，整体清除（连同标记）。
     *  - **仅备份（旧版遗留）**：目标缺失**或为空目录**、备份在 → 归位备份。目标为空目录
     *    只可能是上次替换在 `copyRecursively` 刚建目录就被杀留下的半成品，备份才是完整
     *    旧数据；[isRtpImported] 只看目录非空，不归位会让它误报「未导入」。
     *
     * **删除失败必须中止恢复**：半成品 target 删不掉时若继续删标记、声称成功，下次恢复
     * 会把「非空 target + 备份」判成「已成功替换的残留」而删掉备份——那是盘上唯一的旧
     * 数据。此时保留「标记 + 备份」现场，等下次重试（[delete] 可注入以便测试）。
     *
     * @return false = 现场无法收拾（备份路径被占等），调用方应放弃本次替换（不丢数据）
     */
    internal fun recoverPendingImport(
        target: File,
        delete: (File) -> Boolean = { it.deleteRecursively() },
    ): Boolean = synchronized(importLock) {
        val parent = target.parentFile ?: return@synchronized false
        val backup = File(parent, IMPORT_BACKUP_DIR)
        val marker = File(parent, IMPORT_MARKER_FILE)
        if (marker.exists()) {
            if (backup.exists()) {
                if (target.exists() && !delete(target)) return@synchronized false
                if (!marker.delete()) return@synchronized false
                if (!backup.renameTo(target)) return@synchronized false
            } else {
                if (target.exists() && !delete(target)) return@synchronized false
                if (!marker.delete()) return@synchronized false
            }
            return@synchronized true
        }
        if (backup.exists()) {
            if (!target.exists() || target.listFiles()?.isEmpty() == true) {
                // 目标缺失或为空目录：备份是唯一旧数据（或完整旧数据），归位
                if (target.exists() && !delete(target)) return@synchronized false
                if (!backup.renameTo(target)) return@synchronized false
            } else {
                // 目标有效：备份是上一次已成功替换的残留（成功路径清理失败留下的孤儿）。
                // 删不掉会占住 .import_backup 路径，使后续 target.renameTo(backup) 失败并
                // 被迫走「删旧数据」分支——直接返回失败更安全（不丢数据）
                if (!delete(backup)) return@synchronized false
            }
        }
        return@synchronized true
    }

    /** RTP 是否已导入（app 目录存在且非空）。 */
    fun isRtpImported(gameType: String): Boolean {
        val dir = rtpAppDir(gameType)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * 清除该子类型的 RTP（仅删 `app` 层级，保留同目录其余用户内容）。
     *
     * 顺带清理备份与提交标记：只删 `app` 的话，中断替换留下的备份会在下次
     * [recoverPendingImport] 时被**归位**——用户看到「清除成功却又回来了」。
     * 顺序：先删标记（标记在则恢复逻辑会把备份归位），再删备份，最后删 app；
     * 任一步失败都返回 false 让用户重试，不留半个现场。
     */
    fun clearRtp(gameType: String): Boolean = synchronized(importLock) {
        val root = rtpDir(gameType)
        val appDir = File(root, "app")
        val backup = File(root, IMPORT_BACKUP_DIR)
        val marker = File(root, IMPORT_MARKER_FILE)
        if (marker.exists() && !marker.delete()) return@synchronized false
        if (backup.exists() && !backup.deleteRecursively()) return@synchronized false
        if (!appDir.exists()) return@synchronized true
        appDir.deleteRecursively()
    }

    /**
     * 从 SAF zip 导入 RTP：解压到临时目录 → 拍平单层根目录 → 替换 `app`。
     *
     * @return null 表示成功；否则为类型化拒绝原因（文案由 UI 组装）
     */
    fun importRtpZip(context: Context, gameType: String, uri: Uri): RtpImportRejection? {
        // 整段包在 try 里：rtpAppDir 会读外部存储状态、mkdirs 会做 IO，
        // 任一环节抛出都必须转成类型化失败，异常不得穿到 UI 层
        var created: File? = null
        return try {
            val target = rtpAppDir(gameType)
            val parent = target.parentFile ?: return RtpImportRejection.IoFailed(target.path)
            if (!parent.exists() && !parent.mkdirs()) return RtpImportRejection.IoFailed(parent.name)
            val temp = File(parent, IMPORT_TEMP_DIR)
            created = temp
            temp.deleteRecursively()
            if (!temp.mkdirs()) return RtpImportRejection.IoFailed(temp.name)
            extractZip(context, uri, temp)?.let { return it }
            commitImportTemp(temp, target)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: java.util.zip.ZipException) {
            // 损坏/非法 zip：归因到源文件而非临时目录（否则用户看到的是「读写失败：.import_tmp」）
            RtpImportRejection.IoFailed(uri.lastPathSegment ?: "zip")
        } catch (_: Throwable) {
            // 磁盘满、权限不足、源不可读：
            // 都按类型化失败返回（本方法是 core 门面，调用方只按返回值分支）
            RtpImportRejection.IoFailed(created?.name ?: IMPORT_TEMP_DIR)
        } finally {
            created?.deleteRecursively()
        }
    }

    /** 从 SAF 目录树导入 RTP：递归复制 → 拍平单层根目录 → 替换 `app`。 */
    fun importRtpTree(context: Context, gameType: String, treeUri: Uri): RtpImportRejection? {
        // 目录导入此前只有「失败」布尔，用户看不到原因（选错目录、空目录、存储空间不足
        // 的提示全都一样），这里与 zip 路径对齐给出类型化原因
        var created: File? = null
        return try {
            // fromTreeUri 对非 tree URI 会抛 IllegalArgumentException：必须在 try 内
            val source = DocumentFile.fromTreeUri(context, treeUri)
                ?: return RtpImportRejection.IoFailed(treeUri.lastPathSegment ?: "tree")
            val target = rtpAppDir(gameType)
            val parent = target.parentFile ?: return RtpImportRejection.IoFailed(target.path)
            if (!parent.exists() && !parent.mkdirs()) return RtpImportRejection.IoFailed(parent.name)
            val temp = File(parent, IMPORT_TEMP_DIR)
            created = temp
            temp.deleteRecursively()
            if (!temp.mkdirs()) return RtpImportRejection.IoFailed(temp.name)
            copyDocumentTree(context, source, temp)?.let { return it }
            commitImportTemp(temp, target)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            RtpImportRejection.IoFailed(created?.name ?: IMPORT_TEMP_DIR)
        } finally {
            created?.deleteRecursively()
        }
    }

    /**
     * 临时目录 → `app` 的提交段（zip 与目录导入共用）。
     *
     * 两个导入路径的失败分支必须一致：拍平、替换与「导入后仍为空」的判定都在这里，
     * 否则两条路径会给出不同的失败原因。
     *
     * 替换采用**备份式** + **提交标记**：
     *  - 旧目录先改名成 `.import_backup`，新内容顶上后再删备份，失败即回滚；
     *  - `.importing` 标记在「旧目录已移开、新内容落位」前写盘、成功收尾才删除——
     *    存在即表示 target 不可信（可能只拷了一半），[recoverPendingImport] 据此
     *    恢复，而不是让 [isRtpImported] 误报「已导入」却缺素材；
     *  - **旧目录不能改名时直接放弃本次替换，绝不删旧数据**：删除后若安装又失败，
     *    旧 RTP 没有备份可回滚，将永久丢失；
     *  - 回滚时半成品**删不掉也必须中止**（保留「标记 + 备份」现场）：删掉标记继续走
     *    的话，下次恢复会把「非空 target + 备份」判成「已成功替换的残留」而删掉旧数据。
     *
     * 标记的落盘顺序（先改名、后写标记）是刻意的：标记 + 无备份 ⇔ 首次导入，恢复逻辑
     * 据此可安全清除半成品 target；若反过来先写标记再改名，在两步之间被杀会留下
     * 「标记 + 无备份 + 完整旧数据」——恢复逻辑无法把它与首次导入的半成品区分，
     * 会把完整旧数据当半成品删掉。先改名后写标记的窗口（备份 + 无标记 + 无 target）
     * 由任何先走 [recoverPendingImport] 的写入者归位（含启动路径，见
     * `RpgMakerExternalEngineModule.ensureRtpEnvironment`）。
     *
     * 先前的「先删旧目录再铺新内容」有两个后果——`copyRecursively` 中途失败会留下半成品
     * 目录，而 [isRtpImported] 只看目录非空，下次会显示「已导入」却缺素材；删除与替换之间
     * 被杀则旧 RTP 直接丢失。
     */
    internal fun commitImportTemp(
        temp: File,
        target: File,
        delete: (File) -> Boolean = { it.deleteRecursively() },
    ): RtpImportRejection? = synchronized(importLock) {
        flattenSingleRoot(temp)
        // 「无内容」在建目录前判定：空目录/空 zip 复制过去也只会得到空 app 目录，
        // 用户看到「导入成功」却没有任何素材。listFiles() 为 null 是 IO 错误而非空目录，
        // 按 IoFailed 上报（报成「没有内容」会把排查引到错误方向）
        val children = temp.listFiles() ?: return@synchronized RtpImportRejection.IoFailed(temp.name)
        if (children.isEmpty()) return@synchronized RtpImportRejection.EmptyArchive

        val parent = target.parentFile ?: return@synchronized RtpImportRejection.IoFailed(target.name)
        val backup = File(parent, IMPORT_BACKUP_DIR)
        val marker = File(parent, IMPORT_MARKER_FILE)
        // 上一次替换未正常收尾的现场（含半成品 target）先归位；收拾不了直接放弃本次替换
        if (!recoverPendingImport(target, delete)) {
            return@synchronized RtpImportRejection.IoFailed(target.name)
        }

        var hadTarget = false
        if (target.exists()) {
            if (!target.renameTo(backup)) {
                // 旧目录不能改名：**不删旧数据**。删除后若安装又失败，旧 RTP 将没有备份
                // 可回滚而永久丢失，直接放弃本次替换（旧数据保持原样）
                return@synchronized RtpImportRejection.IoFailed(target.name)
            }
            hadTarget = true
        }
        // 旧数据已移开（或本就无旧数据）后才置提交标记（次序说明见函数 KDoc）。
        // 置位失败回滚 rename 并放弃。
        if (!marker.createNewFile()) {
            if (hadTarget) runCatching { backup.renameTo(target) }
            return@synchronized RtpImportRejection.IoFailed(marker.name)
        }

        val installed = temp.renameTo(target) || temp.copyRecursively(target, overwrite = true)
        if (!installed || target.listFiles()?.isNotEmpty() != true) {
            // 回滚：清掉半成品、删标记，把旧目录放回去。
            // 顺序很关键——标记必须先删：若先放回旧数据却留下标记，下次恢复会把
            // 「放回的旧数据」误当成首次导入半成品而删掉。
            // 半成品删不掉则保持「标记 + 备份」现场（旧数据仍在备份），由下次恢复重试。
            if (target.exists() && !delete(target)) {
                return@synchronized RtpImportRejection.IoFailed(target.name)
            }
            if (!marker.delete()) {
                return@synchronized RtpImportRejection.IoFailed(target.name)
            }
            if (hadTarget && backup.exists() && !backup.renameTo(target)) {
                return@synchronized RtpImportRejection.IoFailed(target.name)
            }
            return@synchronized if (installed) {
                RtpImportRejection.EmptyArchive
            } else {
                RtpImportRejection.IoFailed(target.name)
            }
        }
        // 成功：先删提交标记（提交点）再删备份。标记删不掉 = 无法确认提交（下次恢复会把
        // 新 target 回滚到旧数据），按失败处理；旧数据仍在备份，不丢。
        if (!marker.delete()) {
            return@synchronized RtpImportRejection.IoFailed(target.name)
        }
        // 备份清理失败不阻塞本次成功，但会占住 .import_backup 固定路径（下次导入的
        // target.renameTo(backup) 会因目标已存在而失败 → 导入持续失败）：改名让位，
        // 陈旧的孤儿备份留作磁盘残留即可，不再挡路。
        if (backup.exists() && !backup.deleteRecursively()) {
            runCatching {
                backup.renameTo(File(parent, "$IMPORT_BACKUP_DIR.${System.currentTimeMillis()}"))
            }
        }
        null
    }

    /**
     * 导入自定义字体到共享目录，返回插件可读的绝对路径；扩展名非法或 IO 失败返回 null。
     *
     * 先写同目录临时文件再 rename：直写目标名会在写入失败/被杀时留下半成品并**截断
     * 旧字体**；临时文件 + rename 让目标名的替换是原子的，失败时旧字体保持原样。
     */
    fun importCustomFont(context: Context, uri: Uri): String? {
        var temp: File? = null
        return try {
            val displayName = runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment
            val name = (displayName ?: "font.ttf").substringAfterLast('/').substringAfterLast('\\')
            if (FONT_EXTENSIONS.none { name.lowercase(Locale.ROOT).endsWith(it) }) return null
            val dir = File(Environment.getExternalStorageDirectory(), FONT_PARENT)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val target = File(dir, name)
            val staging = File(dir, ".$name.${System.currentTimeMillis()}.tmp")
            temp = staging
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            } != null
            if (!copied) return null
            // rename 覆盖已存在目标（同目录原子替换）；失败则保留旧字体，返回 null
            if (!staging.renameTo(target)) return null
            target.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            // 失败/被杀路径不留半成品（成功时 staging 已改名，此处无操作）
            temp?.let { if (it.exists()) runCatching { it.delete() } }
        }
    }

    fun isCustomFontPresent(path: String): Boolean =
        path.isNotBlank() && File(path).exists()

    fun customFontFileName(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\')

    private fun extractZip(context: Context, uri: Uri, dest: File): RtpImportRejection? {
        val input = context.contentResolver.openInputStream(uri)
            ?: return RtpImportRejection.IoFailed(uri.lastPathSegment ?: "zip")
        return input.use { extractZipStream(it.buffered(), dest) }
    }

    /**
     * ZIP 解压主体。与 [extractZip] 拆开是为了可单测：本函数只依赖输入流，
     * 不需要 Context/Uri；上限参数默认取生产常量，测试注入小值即可低成本覆盖边界。
     *
     * @return null = 成功；否则为拒绝原因（含 IO/目录创建失败）
     */
    internal fun extractZipStream(
        input: InputStream,
        dest: File,
        maxEntries: Int = MAX_ZIP_ENTRIES,
        maxEntryBytes: Long = MAX_ENTRY_UNCOMPRESSED_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_UNCOMPRESSED_BYTES,
    ): RtpImportRejection? {
        val tempRoot = dest.canonicalPath
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // 先计数再判断跳过：__MACOSX/ 与 .DS_Store 也是条目，
                // 若跳过后才计数，构造大量此类条目即可绕过上限、持续消耗解析与 I/O
                entryCount += 1
                if (entryCount > maxEntries) {
                    return RtpImportRejection.EntryCountExceeded(entryCount, maxEntries)
                }
                // 声明大小检查对所有条目生效（含被过滤的）。流式 zip 的 entry.size 恒为 -1，
                // 故这里只是廉价的前置拒绝，真正的兜底是下面的有界读取。
                if (entry.size > maxEntryBytes) {
                    return RtpImportRejection.EntrySizeExceeded(entry.size, maxEntryBytes)
                }

                val name = entry.name.replace('\\', '/')
                val outFile = File(dest, name)
                val insideRoot = outFile.canonicalPath.startsWith(tempRoot + File.separator)
                val filtered = name.startsWith("__MACOSX/") || name.contains(".DS_Store")
                if (filtered || !insideRoot) {
                    // 跳过也必须**有界**读取：ZipInputStream 是顺序流，跳过条目必须读掉其数据，
                    // 而 closeEntry() 会把整条无界读完（DEFLATED 条目被完整解压）。
                    // 实测：255KB 的 zip（条目名 __MACOSX/big.bin、解压后 256MB）光
                    // closeEntry() 就耗时 586ms —— 被过滤条目因此是绕过字节上限的通道。
                    when (val drained = drainEntryBounded(zip, maxEntryBytes, maxTotalBytes, totalBytes)) {
                        is DrainResult.Drained -> totalBytes = drained.total
                        is DrainResult.EntryOver ->
                            return RtpImportRejection.EntrySizeExceeded(drained.bytes, maxEntryBytes)
                        is DrainResult.TotalOver ->
                            return RtpImportRejection.TotalSizeExceeded(drained.bytes, maxTotalBytes)
                    }
                    zip.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        return RtpImportRejection.DirectoryCreateFailed(outFile.name)
                    }
                } else {
                    outFile.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            return RtpImportRejection.DirectoryCreateFailed(parent.name)
                        }
                    }
                    // 上限与 NativePluginInstaller.unzipSafely 一致：用户挑选的 zip 也可能
                    // 是损坏或精心构造的（高压缩比），无界解压会写满共享存储。
                    // copyTo 不提供计数，故手工逐块读写并同时校验单条目与总量。
                    // 超限/IO 失败时删除半成品：被中止的条目会留下**截断文件**，
                    // 而导入失败后整个临时目录会被丢弃，残留文件只会误导排查；
                    // 若调用方复用了目标目录，截断文件还会被当成有效内容。
                    var written = false
                    try {
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(ZIP_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                // 与排空路径同类判定：这里此前只返回 false 不带原因，
                                // 用户拿到的是一句无信息量的「导入失败」
                                if (entryBytes > maxEntryBytes) {
                                    return RtpImportRejection.EntrySizeExceeded(entryBytes, maxEntryBytes)
                                }
                                if (totalBytes > maxTotalBytes) {
                                    return RtpImportRejection.TotalSizeExceeded(totalBytes, maxTotalBytes)
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                        written = true
                    } catch (_: Throwable) {
                        return RtpImportRejection.IoFailed(outFile.name)
                    } finally {
                        if (!written) {
                            runCatching { outFile.delete() }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return if (dest.listFiles()?.isNotEmpty() == true) null else RtpImportRejection.EmptyArchive
    }

    /** 有界排空的结果：成功给出新的累计值，超限给出**区分类型**的拒绝原因。 */
    private sealed interface DrainResult {
        data class Drained(val total: Long) : DrainResult

        /** 单条目超限（[bytes] 为该条目实际解压量）。 */
        data class EntryOver(val bytes: Long) : DrainResult

        /** 累计超限（[bytes] 为触发时的实际累计值）。 */
        data class TotalOver(val bytes: Long) : DrainResult
    }

    /**
     * 有界排空当前条目的剩余数据并计入累计字节。
     *
     * 与解压写入路径共用同一对上限，确保被过滤/越界的条目不能成为绕过字节上限的通道。
     * 两类超限分别上报：合并成一个错误码会产出「0 B 超过 1 MiB」这类自相矛盾的提示
     * （单条目超限时，进入该条目之前的累计值可能远小于上限）。
     */
    private fun drainEntryBounded(
        zip: ZipInputStream,
        maxEntryBytes: Long,
        maxTotalBytes: Long,
        runningTotal: Long,
    ): DrainResult {
        val buffer = ByteArray(ZIP_BUFFER_SIZE)
        var entryBytes = 0L
        var total = runningTotal
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            entryBytes += read
            total += read
            if (entryBytes > maxEntryBytes) return DrainResult.EntryOver(entryBytes)
            if (total > maxTotalBytes) return DrainResult.TotalOver(total)
        }
        return DrainResult.Drained(total)
    }

    /** 递归复制的累计计数（跨递归层共享）。 */
    private class CopyCounters(var entries: Int = 0, var totalBytes: Long = 0L)

    /**
     * 递归复制 SAF 目录树；失败给出类型化原因（null = 成功）。
     *
     * 与 zip 路径共用同一组上限（条目数 / 单文件 / 累计字节，见 [extractZipStream]）：
     * 目录树同样可能被构造得巨大（SAF 树没有压缩比问题，但条目数与总量仍可无界），
     * 无上限复制会写满共享存储。
     */
    private fun copyDocumentTree(
        context: Context,
        source: DocumentFile,
        dest: File,
        maxEntries: Int = MAX_ZIP_ENTRIES,
        maxEntryBytes: Long = MAX_ENTRY_UNCOMPRESSED_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_UNCOMPRESSED_BYTES,
        counters: CopyCounters = CopyCounters(),
    ): RtpImportRejection? {
        for (child in source.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val dir = File(dest, name)
                if (!dir.exists() && !dir.mkdirs()) return RtpImportRejection.DirectoryCreateFailed(name)
                copyDocumentTree(context, child, dir, maxEntries, maxEntryBytes, maxTotalBytes, counters)
                    ?.let { return it }
            } else {
                // 条目数先计数再复制：构造海量小文件同样能拖垮导入
                counters.entries += 1
                if (counters.entries > maxEntries) {
                    return RtpImportRejection.EntryCountExceeded(counters.entries, maxEntries)
                }
                val target = File(dest, name)
                // 上限超限走类型化原因；openInputStream 为 null / 拷贝抛异常归为 IoFailed。
                // 半成品文件必须删掉：调用方会把整个临时目录丢弃，但若将来复用，
                // 截断文件会被当成有效素材。
                var limitRejection: RtpImportRejection? = null
                val copied = runCatching {
                    val input = context.contentResolver.openInputStream(child.uri)
                    if (input == null) {
                        false
                    } else {
                        input.use { src ->
                            target.outputStream().use { dst ->
                                limitRejection = copyFileBounded(src, dst, maxEntryBytes, maxTotalBytes, counters)
                            }
                        }
                        limitRejection == null
                    }
                }.getOrDefault(false)
                if (!copied) {
                    runCatching { target.delete() }
                    return limitRejection ?: RtpImportRejection.IoFailed(name)
                }
            }
        }
        return null
    }

    /**
     * 有界拷贝单个文件：逐块读写并同时校验单文件与累计总量（与 zip 路径共用上限）。
     *
     * @return null = 成功；否则为超限拒绝原因
     */
    private fun copyFileBounded(
        input: InputStream,
        output: java.io.OutputStream,
        maxEntryBytes: Long,
        maxTotalBytes: Long,
        counters: CopyCounters,
    ): RtpImportRejection? {
        val buffer = ByteArray(ZIP_BUFFER_SIZE)
        var fileBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            fileBytes += read
            counters.totalBytes += read
            if (fileBytes > maxEntryBytes) {
                return RtpImportRejection.EntrySizeExceeded(fileBytes, maxEntryBytes)
            }
            if (counters.totalBytes > maxTotalBytes) {
                return RtpImportRejection.TotalSizeExceeded(counters.totalBytes, maxTotalBytes)
            }
            output.write(buffer, 0, read)
        }
        return null
    }

    /**
     * 若目录下仅有一个子目录且无文件，把该子目录内容上移一层：
     * 兼容官方 RTP 包 `RPGVXAce_RTP/Graphics/...` 这类多包一层根目录的结构。
     */
    private fun flattenSingleRoot(dir: File) {
        val children = dir.listFiles() ?: return
        if (children.size != 1 || !children[0].isDirectory) return
        val inner = children[0]
        val innerChildren = inner.listFiles() ?: return
        for (child in innerChildren) {
            if (!child.renameTo(File(dir, child.name))) {
                return
            }
        }
        inner.delete()
    }
}
