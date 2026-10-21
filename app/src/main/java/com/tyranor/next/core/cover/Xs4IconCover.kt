package com.tyranor.next.core.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tyranor.next.core.engine.EngineType
import com.tyranor.next.core.game.model.ScanGame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException

/** 游戏根下的一个直接子文件；`open` 惰性打开，File 与 SAF 两种来源共用。 */
internal class Xs4GameFile(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val open: () -> InputStream?,
)

/**
 * 候选文件排序与读取规则（纯函数，JVM 可测）：`*.ico` 在前（按名排序），其后为 `*.exe`
 * （排除启动器辅助程序，按名排序）。
 */
internal object Xs4IconRules {
    const val MAX_EXE_BYTES = 64L * 1024 * 1024
    const val MAX_ICO_BYTES = 8L * 1024 * 1024

    // 来源是对 AliceSoft 游戏发行目录自带辅助程序的观察，不是取自任何第三方代码。
    private val EXCLUDED_EXE_NAMES = listOf("OpenSaveFolder.exe", "ResetConfig.exe", "Uninstaller.exe")

    fun candidates(files: List<Xs4GameFile>): List<Xs4GameFile> {
        val icos = files.filter { it.name.endsWith(".ico", ignoreCase = true) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        val exes = files.filter { isUsableExe(it.name) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        return icos + exes
    }

    private fun isUsableExe(name: String): Boolean =
        name.endsWith(".exe", ignoreCase = true) && EXCLUDED_EXE_NAMES.none { it.equals(name, ignoreCase = true) }

    /** 单个候选解析为 ICO 字节；不可用返回 null。`.ico` 直读，其余按 PE 解析。 */
    fun icoBytesOf(file: Xs4GameFile): ByteArray? {
        val isIco = file.name.endsWith(".ico", ignoreCase = true)
        val limit = if (isIco) MAX_ICO_BYTES else MAX_EXE_BYTES
        if (file.size > limit) return null
        val bytes = readCapped(file.open, limit) ?: return null
        if (bytes.isEmpty()) return null
        return if (isIco) bytes.takeIf { isIcoHeader(it) } else Xs4PeIcon.extractIco(bytes)
    }

    /** 按候选顺序返回第一个可解析的 ICO 字节。 */
    fun firstIco(files: List<Xs4GameFile>): ByteArray? =
        candidates(files).asSequence().mapNotNull(::icoBytesOf).firstOrNull()

    fun isIcoHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        val reserved = u16(bytes, 0)
        val type = u16(bytes, 2)
        val count = u16(bytes, 4)
        return reserved == 0 && type == 1 && count in 1..Xs4PeIcon.MAX_GROUP_ENTRIES &&
            bytes.size >= 6 + 16 * count
    }

    /** 读取至多 [maxBytes] 字节；超限返回 null，不截断。 */
    internal fun readCapped(open: () -> InputStream?, maxBytes: Long): ByteArray? {
        val input = open() ?: return null
        return input.use { stream -> readAllCapped(stream, maxBytes) }
    }

    private fun readAllCapped(stream: InputStream, maxBytes: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            // 调用方可能被中断（协程取消经线程中断传递）：按取消处理，原样向上抛出
            if (Thread.currentThread().isInterrupted) throw CancellationException("xs4 icon read interrupted")
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

/** PE 解析上限，[PeImage] 与 [ResourceTree] 共用。 */
private const val MAX_PE_SECTIONS = 96
private const val MAX_PE_RESOURCE_ENTRIES = 1024

/**
 * PE `.rsrc` 图标提取：只做有界读取，任何越界 / 结构异常一律抛 IllegalArgumentException，
 * 入口 [extractIco] 统一收敛为 null。
 */
internal object Xs4PeIcon {
    const val MAX_GROUP_ENTRIES = 64
    private const val MAX_ICON_BYTES = 8 * 1024 * 1024
    private const val RT_ICON = 3L
    private const val RT_GROUP_ICON = 14L
    private const val TARGET_SIZE = 256
    private const val ICO_HEADER_SIZE = 6
    private const val ICO_ENTRY_SIZE = 16

    fun extractIco(exe: ByteArray): ByteArray? = try {
        buildIco(PeImage(exe))
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun buildIco(pe: PeImage): ByteArray? {
        val layout = pe.layout() ?: return null
        val tree = ResourceTree(pe, layout)
        val icons = tree.leaves(RT_ICON)
        val groups = tree.leaves(RT_GROUP_ICON)
        if (icons.isEmpty() || groups.isEmpty()) return null

        // 按距 256px 排序后逐个尝试：某个图标组或图标数据结构异常只放弃该项，不影响其它候选
        val ranked = groups.values
            .flatMap { readGroupOrEmpty(pe, layout, it) }
            .filter { icons.containsKey(it.id) }
            .sortedBy { it.rank() }
        for (entry in ranked) {
            val ico = try {
                val leaf = icons.getValue(entry.id)
                require(leaf.size in 16..MAX_ICON_BYTES.toLong())
                singleEntryIco(entry, pe.slice(pe.rvaToOffset(layout, leaf.rva), leaf.size))
            } catch (_: IllegalArgumentException) {
                null
            }
            if (ico != null) return ico
        }
        return null
    }

    private fun readGroupOrEmpty(pe: PeImage, layout: PeLayout, group: ResourceTree.Leaf): List<GroupEntry> =
        try {
            readGroup(pe, layout, group)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    private fun readGroup(pe: PeImage, layout: PeLayout, group: ResourceTree.Leaf): List<GroupEntry> {
        val base = pe.rvaToOffset(layout, group.rva)
        require(group.size >= ICO_HEADER_SIZE)
        require(pe.u16(base + 2) == 1)
        val count = pe.u16(base + 4)
        require(count <= MAX_GROUP_ENTRIES)
        require(group.size >= ICO_HEADER_SIZE + 14L * count)
        return (0 until count).map { index ->
            val e = base + ICO_HEADER_SIZE + 14L * index
            GroupEntry(
                width = pe.u8(e),
                height = pe.u8(e + 1),
                colorCount = pe.u8(e + 2),
                planes = pe.u16(e + 4),
                bitCount = pe.u16(e + 6),
                id = pe.u16(e + 12).toLong(),
            )
        }
    }

    private fun singleEntryIco(entry: GroupEntry, image: ByteArray): ByteArray {
        require(image.size in 16..MAX_ICON_BYTES)
        val out = ByteArray(ICO_HEADER_SIZE + ICO_ENTRY_SIZE + image.size)
        putLe(out, 2, 1, 2)
        putLe(out, 4, 1, 2)
        val e = ICO_HEADER_SIZE
        out[e] = entry.width.toByte()
        out[e + 1] = entry.height.toByte()
        out[e + 2] = entry.colorCount.toByte()
        putLe(out, e + 4, entry.planes.toLong(), 2)
        putLe(out, e + 6, entry.bitCount.toLong(), 2)
        putLe(out, e + 8, image.size.toLong(), 4)
        putLe(out, e + 12, (ICO_HEADER_SIZE + ICO_ENTRY_SIZE).toLong(), 4)
        image.copyInto(out, ICO_HEADER_SIZE + ICO_ENTRY_SIZE)
        return out
    }

    private fun putLe(out: ByteArray, offset: Int, value: Long, length: Int) {
        for (i in 0 until length) out[offset + i] = (value shr (8 * i)).toByte()
    }

    private data class GroupEntry(
        val width: Int,
        val height: Int,
        val colorCount: Int,
        val planes: Int,
        val bitCount: Int,
        val id: Long,
    ) {
        /** 距 256px 越近越优，同距离取更高色深（越小越优）。宽度字节 0 表示 256。 */
        fun rank(): Int {
            val size = if (width == 0) TARGET_SIZE else width
            return kotlin.math.abs(size - TARGET_SIZE) * 64 + (64 - bitCount.coerceIn(0, 32))
        }
    }
}

private class PeLayout(val resourceRva: Long, val resourceSize: Long, val sections: List<PeSection>)

private class PeSection(val va: Long, val virtualSize: Long, val raw: Long, val ptr: Long)

/** 有界的小端 PE 读取器：所有偏移 / 长度先校验，越界抛 IllegalArgumentException。 */
private class PeImage(private val data: ByteArray) {
    private val size = data.size.toLong()

    private fun need(offset: Long, length: Long) =
        require(offset >= 0 && length >= 0 && length <= size && offset <= size - length)

    fun u8(offset: Long): Int {
        need(offset, 1)
        return data[offset.toInt()].toInt() and 0xFF
    }

    fun u16(offset: Long): Int {
        need(offset, 2)
        val o = offset.toInt()
        return (data[o].toInt() and 0xFF) or ((data[o + 1].toInt() and 0xFF) shl 8)
    }

    fun u32(offset: Long): Long {
        need(offset, 4)
        val o = offset.toInt()
        return (0 until 4).fold(0L) { acc, i -> acc or ((data[o + i].toLong() and 0xFF) shl (8 * i)) }
    }

    fun slice(offset: Long, length: Long): ByteArray {
        need(offset, length)
        return data.copyOfRange(offset.toInt(), (offset + length).toInt())
    }

    /** 解析 DOS/PE/可选头/节表；无 `.rsrc` 目录或非 PE 返回 null。 */
    fun layout(): PeLayout? {
        if (u16(0) != 0x5A4D) return null
        val pe = u32(0x3C)
        if (u32(pe) != 0x00004550L) return null
        val coff = pe + 4
        val sectionCount = u16(coff + 2)
        val optionalSize = u16(coff + 16)
        val opt = coff + 20
        require(sectionCount in 1..MAX_PE_SECTIONS)
        val (dirCountOffset, dirBase) = when (u16(opt)) {
            0x10B -> Pair(opt + 92, opt + 96)
            0x20B -> Pair(opt + 108, opt + 112)
            else -> return null
        }
        require(optionalSize >= dirBase - opt + 24)
        if (u32(dirCountOffset) <= RESOURCE_DIRECTORY_INDEX) return null
        val rsrcRva = u32(dirBase + RESOURCE_DIRECTORY_INDEX * 8)
        val rsrcSize = u32(dirBase + RESOURCE_DIRECTORY_INDEX * 8 + 4)
        if (rsrcRva == 0L) return null
        val sectionTable = opt + optionalSize
        val sections = (0 until sectionCount).map { i ->
            val s = sectionTable + 40L * i
            PeSection(va = u32(s + 12), virtualSize = u32(s + 8), raw = u32(s + 16), ptr = u32(s + 20))
        }
        return PeLayout(rsrcRva, rsrcSize, sections)
    }

    /** RVA → 文件偏移；必须落在某节的原始数据范围内。 */
    fun rvaToOffset(layout: PeLayout, rva: Long): Long {
        for (s in layout.sections) {
            val delta = rva - s.va
            val span = if (s.virtualSize != 0L) s.virtualSize else s.raw
            if (delta >= 0 && delta < span && delta < s.raw) return s.ptr + delta
        }
        throw IllegalArgumentException("rva outside sections")
    }

    private companion object {
        const val RESOURCE_DIRECTORY_INDEX = 2L
    }
}

/**
 * 固定三层（类型 → 名称 → 语言）的资源树遍历。层数固定，循环在结构上不可能；
 * 目录条目数有上限，子目录偏移必须落在资源节内。
 */
private class ResourceTree(private val pe: PeImage, private val layout: PeLayout) {
    class Leaf(val rva: Long, val size: Long)

    private class Entry(val id: Long, val isDirectory: Boolean, val target: Long)

    private val base = pe.rvaToOffset(layout, layout.resourceRva)
    private val limit = layout.resourceSize

    /** 返回 类型 ID → (名称 ID → 数据叶子)；命名条目与非数值 ID 忽略。 */
    fun leaves(typeId: Long): Map<Long, Leaf> {
        val result = LinkedHashMap<Long, Leaf>()
        for (type in entries(0)) {
            if (type.id != typeId || !type.isDirectory) continue
            for (name in entries(type.target)) {
                if (!name.isDirectory) continue
                // 单个名称子树结构异常只跳过该项，不影响同类型的其它图标
                val leaf = try {
                    firstLeaf(name.target)
                } catch (_: IllegalArgumentException) {
                    null
                }
                if (leaf == null) continue
                result.putIfAbsent(name.id, leaf)
            }
        }
        return result
    }

    private fun firstLeaf(dirRel: Long): Leaf? {
        val lang = entries(dirRel).firstOrNull() ?: return null
        if (lang.isDirectory) return null
        require(lang.target < limit)
        val data = base + lang.target
        return Leaf(pe.u32(data), pe.u32(data + 4))
    }

    private fun entries(dirRel: Long): List<Entry> {
        require(dirRel >= 0 && dirRel < limit)
        val dir = base + dirRel
        val count = pe.u16(dir + 12) + pe.u16(dir + 14)
        require(count <= MAX_PE_RESOURCE_ENTRIES)
        return (0 until count).mapNotNull { i ->
            val e = dir + 16 + 8L * i
            val name = pe.u32(e)
            val target = pe.u32(e + 4)
            if ((name and NAMED_FLAG) != 0L) {
                null
            } else {
                Entry(id = name and 0xFFFF, isDirectory = (target and DIRECTORY_FLAG) != 0L, target = target and DIRECTORY_FLAG.inv())
            }
        }
    }

    private companion object {
        const val NAMED_FLAG = 0x80000000L
        const val DIRECTORY_FLAG = 0x80000000L
    }
}

/**
 * XSYS4 游戏的 exe 图标封面兜底（仅在本地与在线来源都没有封面时调用）：PNG 写入持久封面目录
 * （`filesDir/covers_remote`，与在线封面共用，删除/替换时由 [CoverImageCache.deleteCachedCover] 回收），
 * 不写游戏目录。调用方须在 IO 线程；失败一律返回 null，取消异常原样抛出。
 */
internal object Xs4IconCover {
    private const val TAG = "Xs4IconCover"

    /** 缓存文件名前缀：`xs4_<游戏 key>_<指纹>.png`。 */
    const val FILE_PREFIX = "xs4_"
    private const val TEMP_PREFIX = "xs4-"
    private const val TEMP_SUFFIX = ".tmp"
    private const val MAX_DECODE_SIDE = 512
    private const val STALE_TEMP_MS = 10L * 60L * 1000L

    /** 图标兜底的启用条件：XSYS4 且当前无封面（用户已有封面、或其它引擎，均不覆盖）。 */
    fun needsFallback(engine: EngineType, coverUri: String?): Boolean =
        engine == EngineType.XSYS4 && coverUri.isNullOrBlank()

    /** 封面 URI 是否为本对象产生的图标缓存（按文件名前缀识别，与 [fileName] 同源）。 */
    fun isIconCoverUri(coverUri: String?): Boolean {
        if (coverUri.isNullOrBlank()) return false
        val name = coverUri.substringAfterLast('/')
        return name.startsWith(FILE_PREFIX) && name.endsWith(".png")
    }

    internal fun fileName(gameUri: String, fingerprint: String): String =
        "${ownPrefix(gameUri)}${stableKey(fingerprint)}.png"

    private fun ownPrefix(gameUri: String): String = "$FILE_PREFIX${stableKey(gameUri)}_"

    fun resolveCoverUri(context: Context, game: ScanGame): String? = try {
        resolve(context, game)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "xsys4 icon cover failed", e)
        null
    }

    private fun resolve(context: Context, game: ScanGame): String? {
        val files = gameFiles(context, game) ?: return null
        val candidates = Xs4IconRules.candidates(files)
        if (candidates.isEmpty()) return null
        val fingerprint = candidates.joinToString("|") { "${it.name}:${it.size}:${it.lastModified}" }
        val dir = persistentCoverDir(context)
        val target = File(dir, fileName(game.uri, fingerprint))
        if (target.isFile && target.length() > 0L) return Uri.fromFile(target).toString()
        val bitmap = candidates.asSequence().mapNotNull(::decodeCandidate).firstOrNull() ?: return null
        return try {
            writePng(bitmap, dir, target, ownPrefix(game.uri))
        } finally {
            bitmap.recycle()
        }
    }

    /** 单个候选失败只跳过该候选（记日志），取消异常原样抛出。 */
    private fun decodeCandidate(file: Xs4GameFile): Bitmap? = try {
        Xs4IconRules.icoBytesOf(file)?.let(::decodeBounded)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "xsys4 icon candidate skipped: ${file.name}", e)
        null
    }

    /** 先读尺寸，超过 [MAX_DECODE_SIDE] 时按 2 的幂下采样，限制解码内存。 */
    private fun decodeBounded(ico: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(ico, 0, ico.size, bounds)
        val sample = downsampleFactor(bounds.outWidth, bounds.outHeight, MAX_DECODE_SIDE) ?: return null
        return BitmapFactory.decodeByteArray(ico, 0, ico.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 使最长边不超过 [maxSide] 的 2 的幂采样率；尺寸非法返回 null（纯函数，JVM 可测）。 */
    internal fun downsampleFactor(width: Int, height: Int, maxSide: Int): Int? {
        if (width <= 0 || height <= 0 || maxSide <= 0) return null
        val side = maxOf(width, height)
        var sample = 1
        while (side / sample > maxSide) sample *= 2
        return sample
    }

    /** 过期的 `xs4-*.tmp` 孤儿（写入中途进程被杀时遗留）；未过期的不动，避免误删并发写入。 */
    internal fun isStaleTempName(name: String, lastModified: Long, now: Long): Boolean =
        name.startsWith(TEMP_PREFIX) && name.endsWith(TEMP_SUFFIX) && now - lastModified > STALE_TEMP_MS

    private fun writePng(bitmap: Bitmap, dir: File, target: File, ownPrefix: String): String? {
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val tmp = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, dir)
        try {
            val written = FileOutputStream(tmp).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!written || !tmp.renameTo(target)) return null
        } finally {
            tmp.delete()
        }
        pruneStale(dir, ownPrefix, keep = target.name)
        return Uri.fromFile(target).toString()
    }

    /** 删除本游戏的旧版本图标，以及过期的临时孤儿文件。 */
    private fun pruneStale(dir: File, ownPrefix: String, keep: String) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            val oldOwn = file.name.startsWith(ownPrefix) && file.name != keep
            if (oldOwn || isStaleTempName(file.name, file.lastModified(), now)) file.delete()
        }
    }

    /** File 路径（绝对路径或 file://）与 SAF tree / document（content://）两种游戏 uri 形态。 */
    private fun gameFiles(context: Context, game: ScanGame): List<Xs4GameFile>? {
        val text = game.uri
        if (text.startsWith('/')) return fileEntries(File(text))
        val uri = Uri.parse(text)
        return when {
            uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let { fileEntries(File(it)) }
            uri.scheme.equals("content", ignoreCase = true) && DocumentsContract.isTreeUri(uri) ->
                documentEntries(context, uri)
            else -> null
        }
    }

    private fun fileEntries(dir: File): List<Xs4GameFile>? {
        if (!dir.isDirectory) return null
        return dir.listFiles()?.filter { it.isFile }?.map { file ->
            Xs4GameFile(file.name, file.length(), file.lastModified()) { FileInputStream(file) }
        }
    }

    private fun documentEntries(context: Context, uri: Uri): List<Xs4GameFile>? {
        val root = DocumentFile.fromTreeUri(context.applicationContext, uri) ?: return null
        return root.listFiles().filter { !it.isDirectory }.mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            Xs4GameFile(name, doc.length(), doc.lastModified()) {
                context.contentResolver.openInputStream(doc.uri)
            }
        }
    }
}
