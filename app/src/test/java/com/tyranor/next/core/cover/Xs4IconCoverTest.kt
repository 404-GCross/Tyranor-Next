package com.tyranor.next.core.cover

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class Xs4IconCoverTest {

    private class Icon(val width: Int, val bitCount: Int, val id: Int, val payload: ByteArray)

    private fun dib(tag: Int): ByteArray = ByteArray(48).also {
        it[0] = 40
        it[40] = tag.toByte()
    }

    /** 合成最小 PE：DOS 头 + PE 头 + 单个 `.rsrc` 节，资源树为 类型 → 名称 → 语言 三层。 */
    private fun buildPe(
        icons: List<Icon>,
        groupEntries: List<Icon> = icons,
        pe32Plus: Boolean = false,
        groupLoop: Boolean = false,
    ): ByteArray {
        val n = icons.size
        val iconTypeOff = 32
        val groupTypeOff = iconTypeOff + 16 + 8 * n
        var cursor = groupTypeOff + 16 + 8
        val iconNameOff = IntArray(n)
        val iconLeafOff = IntArray(n)
        for (i in 0 until n) {
            iconNameOff[i] = cursor; cursor += 24
            iconLeafOff[i] = cursor; cursor += 16
        }
        val groupNameOff = cursor; cursor += 24
        val groupLeafOff = cursor; cursor += 16
        val iconDataOff = IntArray(n)
        for (i in 0 until n) {
            iconDataOff[i] = cursor; cursor += align4(icons[i].payload.size)
        }
        val group = groupBytes(groupEntries)
        val groupDataOff = cursor; cursor += align4(group.size)
        val rsrc = ByteArray(cursor)

        dir(rsrc, 0, 2)
        entry(rsrc, 16, 3, iconTypeOff.toLong(), dirFlag = true)
        entry(rsrc, 24, 14, if (groupLoop) 0L else groupTypeOff.toLong(), dirFlag = true)
        dir(rsrc, iconTypeOff, n)
        for (i in 0 until n) {
            entry(rsrc, iconTypeOff + 16 + 8 * i, icons[i].id.toLong(), iconNameOff[i].toLong(), dirFlag = true)
            dir(rsrc, iconNameOff[i], 1)
            entry(rsrc, iconNameOff[i] + 16, 0x409, iconLeafOff[i].toLong(), dirFlag = false)
            leaf(rsrc, iconLeafOff[i], 0x1000L + iconDataOff[i], icons[i].payload.size)
            icons[i].payload.copyInto(rsrc, iconDataOff[i])
        }
        dir(rsrc, groupTypeOff, 1)
        entry(rsrc, groupTypeOff + 16, 1, groupNameOff.toLong(), dirFlag = true)
        dir(rsrc, groupNameOff, 1)
        entry(rsrc, groupNameOff + 16, 0x409, groupLeafOff.toLong(), dirFlag = false)
        leaf(rsrc, groupLeafOff, 0x1000L + groupDataOff, group.size)
        group.copyInto(rsrc, groupDataOff)

        val optSize = if (pe32Plus) 240 else 224
        val sectionRaw = align(cursor, 0x200)
        val file = ByteArray(0x200 + sectionRaw)
        put(file, 0, 0x5A4D, 2)
        put(file, 0x3C, 0x40, 4)
        put(file, 0x40, 0x00004550, 4)
        put(file, 0x44 + 2, 1, 2)
        put(file, 0x44 + 16, optSize.toLong(), 2)
        val opt = 0x58
        put(file, opt, if (pe32Plus) 0x20B else 0x10B, 2)
        put(file, opt + if (pe32Plus) 108 else 92, 16, 4)
        val dirBase = opt + if (pe32Plus) 112 else 96
        put(file, dirBase + 16, 0x1000, 4)
        put(file, dirBase + 20, cursor.toLong(), 4)
        val section = opt + optSize
        ".rsrc".toByteArray().copyInto(file, section)
        put(file, section + 8, cursor.toLong(), 4)
        put(file, section + 12, 0x1000, 4)
        put(file, section + 16, sectionRaw.toLong(), 4)
        put(file, section + 20, 0x200, 4)
        rsrc.copyInto(file, 0x200)
        return file
    }

    private fun groupBytes(entries: List<Icon>): ByteArray {
        val g = ByteArray(6 + 14 * entries.size)
        put(g, 2, 1, 2)
        put(g, 4, entries.size.toLong(), 2)
        entries.forEachIndexed { i, icon ->
            val e = 6 + 14 * i
            g[e] = icon.width.toByte()
            g[e + 1] = icon.width.toByte()
            put(g, e + 4, 1, 2)
            put(g, e + 6, icon.bitCount.toLong(), 2)
            put(g, e + 8, icon.payload.size.toLong(), 4)
            put(g, e + 12, icon.id.toLong(), 2)
        }
        return g
    }

    private fun dir(buf: ByteArray, off: Int, ids: Int) = put(buf, off + 14, ids.toLong(), 2)

    private fun entry(buf: ByteArray, off: Int, id: Long, target: Long, dirFlag: Boolean) {
        put(buf, off, id, 4)
        put(buf, off + 4, if (dirFlag) target or 0x80000000L else target, 4)
    }

    private fun leaf(buf: ByteArray, off: Int, rva: Long, size: Int) {
        put(buf, off, rva, 4)
        put(buf, off + 4, size.toLong(), 4)
    }

    private fun put(buf: ByteArray, off: Int, value: Long, len: Int) {
        for (i in 0 until len) buf[off + i] = (value shr (8 * i)).toByte()
    }

    private fun put(buf: ByteArray, off: Int, value: Int, len: Int) = put(buf, off, value.toLong(), len)

    private fun le(buf: ByteArray, off: Int, len: Int): Long =
        (0 until len).fold(0L) { acc, i -> acc or ((buf[off + i].toLong() and 0xFF) shl (8 * i)) }

    private fun align(value: Int, to: Int) = (value + to - 1) / to * to

    private fun align4(value: Int) = align(value, 4)

    private fun file(name: String, bytes: ByteArray) =
        Xs4GameFile(name, bytes.size.toLong(), 0L) { ByteArrayInputStream(bytes) }

    private val sample = listOf(
        Icon(width = 16, bitCount = 32, id = 1, payload = dib(1)),
        Icon(width = 0, bitCount = 32, id = 2, payload = dib(2)),
        Icon(width = 48, bitCount = 8, id = 3, payload = dib(3)),
    )

    @Test
    fun rebuildsSingleEntryIcoFromClosestTo256() {
        val ico = Xs4PeIcon.extractIco(buildPe(sample))!!

        assertEquals(0L, le(ico, 0, 2))
        assertEquals(1L, le(ico, 2, 2))
        assertEquals(1L, le(ico, 4, 2))
        assertEquals(0L, le(ico, 6, 1))
        assertEquals(32L, le(ico, 6 + 6, 2))
        assertEquals(sample[1].payload.size.toLong(), le(ico, 6 + 8, 4))
        assertEquals(22L, le(ico, 6 + 12, 4))
        assertArrayEquals(sample[1].payload, ico.copyOfRange(22, ico.size))
    }

    @Test
    fun tieBreaksOnHigherBitDepth() {
        val icons = listOf(
            Icon(width = 32, bitCount = 8, id = 1, payload = dib(1)),
            Icon(width = 32, bitCount = 32, id = 2, payload = dib(2)),
        )
        val ico = Xs4PeIcon.extractIco(buildPe(icons))!!
        assertArrayEquals(icons[1].payload, ico.copyOfRange(22, ico.size))
    }

    @Test
    fun skipsGroupEntriesWithoutIconData() {
        val groups = listOf(
            Icon(width = 0, bitCount = 32, id = 9, payload = dib(9)),
            Icon(width = 16, bitCount = 32, id = 1, payload = dib(1)),
        )
        val pe = buildPe(icons = listOf(Icon(16, 32, 1, dib(1))), groupEntries = groups)
        val ico = Xs4PeIcon.extractIco(pe)!!
        assertArrayEquals(dib(1), ico.copyOfRange(22, ico.size))
    }

    @Test
    fun parsesPe32PlusHeader() {
        val ico = Xs4PeIcon.extractIco(buildPe(sample, pe32Plus = true))!!
        assertArrayEquals(sample[1].payload, ico.copyOfRange(22, ico.size))
    }

    @Test
    fun returnsNullForNonPeInput() {
        assertNull(Xs4PeIcon.extractIco("hello world".toByteArray()))
        assertNull(Xs4PeIcon.extractIco(ByteArray(0)))
    }

    @Test
    fun returnsNullWithoutResourceDirectory() {
        val pe = buildPe(sample)
        put(pe, 0x58 + 96 + 16, 0, 4)
        assertNull(Xs4PeIcon.extractIco(pe))
    }

    @Test
    fun returnsNullWhenPeOffsetIsOutOfRange() {
        val pe = buildPe(sample)
        put(pe, 0x3C, 0x7FFFFFF0L, 4)
        assertNull(Xs4PeIcon.extractIco(pe))
    }

    @Test
    fun returnsNullWhenSectionPointerIsOutOfFile() {
        val pe = buildPe(sample)
        put(pe, 0x58 + 224 + 20, 0x7FFF0000L, 4)
        assertNull(Xs4PeIcon.extractIco(pe))
    }

    @Test
    fun returnsNullOnResourceLoop() {
        assertNull(Xs4PeIcon.extractIco(buildPe(sample, groupLoop = true)))
    }

    @Test
    fun icoDirectoryFileIsReadAsIs() {
        val ico = Xs4PeIcon.extractIco(buildPe(sample))!!
        val picked = Xs4IconRules.icoBytesOf(file("Game.ICO", ico))
        assertArrayEquals(ico, picked)
    }

    @Test
    fun rejectsOversizedAndEmptyFiles() {
        val pe = buildPe(sample)
        val oversized = Xs4GameFile("big.exe", Xs4IconRules.MAX_EXE_BYTES + 1, 0L) { ByteArrayInputStream(pe) }
        assertNull(Xs4IconRules.icoBytesOf(oversized))
        assertNull(Xs4IconRules.icoBytesOf(file("empty.exe", ByteArray(0))))
        assertNull(Xs4IconRules.icoBytesOf(file("bad.ico", "not an icon".toByteArray())))
    }

    @Test
    fun readCappedHonoursLimitExactly() {
        val data = ByteArray(10) { it.toByte() }
        assertNull(Xs4IconRules.readCapped({ ByteArrayInputStream(data) }, 9))
        assertArrayEquals(data, Xs4IconRules.readCapped({ ByteArrayInputStream(data) }, 10))
    }

    @Test
    fun candidatesExcludeHelpersAndPreferIco() {
        val names = listOf("OpenSaveFolder.exe", "Rance.exe", "UNINSTALLER.EXE", "resetconfig.exe", "b.ico", "A.ico")
        val picked = Xs4IconRules.candidates(names.map { file(it, ByteArray(0)) }).map { it.name }
        assertEquals(listOf("A.ico", "b.ico", "Rance.exe"), picked)
    }

    @Test
    fun firstParsableExeWins() {
        val valid = buildPe(sample)
        val expected = Xs4PeIcon.extractIco(valid)!!
        val files = listOf(file("a.exe", "junk".toByteArray()), file("b.exe", valid))
        assertArrayEquals(expected, Xs4IconRules.firstIco(files))
    }

    @Test
    fun icoFileWinsOverExe() {
        val valid = buildPe(sample)
        val ico = Xs4PeIcon.extractIco(valid)!!
        val files = listOf(file("game.exe", valid), file("icon.ico", ico))
        assertArrayEquals(ico, Xs4IconRules.firstIco(files))
    }

    @Test
    fun unusableIcoFallsBackToExe() {
        val valid = buildPe(sample)
        val files = listOf(file("broken.ico", "xx".toByteArray()), file("game.exe", valid))
        assertTrue(Xs4IconRules.firstIco(files) != null)
    }

    @Test
    fun unusableIconDataDoesNotHideNextCandidate() {
        val icons = listOf(
            Icon(width = 0, bitCount = 32, id = 1, payload = ByteArray(4)),
            Icon(width = 16, bitCount = 32, id = 2, payload = dib(2)),
        )
        val ico = Xs4PeIcon.extractIco(buildPe(icons))!!
        assertArrayEquals(dib(2), ico.copyOfRange(22, ico.size))
    }

    @Test
    fun readCappedTreatsInterruptAsCancellation() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(CancellationException::class.java) {
                Xs4IconRules.readCapped({ ByteArrayInputStream(ByteArray(10)) }, 100)
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun downsampleKeepsLongSideWithinLimit() {
        assertEquals(1, Xs4IconCover.downsampleFactor(256, 256, 512))
        assertEquals(1, Xs4IconCover.downsampleFactor(512, 300, 512))
        assertEquals(2, Xs4IconCover.downsampleFactor(513, 10, 512))
        assertEquals(2, Xs4IconCover.downsampleFactor(1024, 768, 512))
        assertEquals(4, Xs4IconCover.downsampleFactor(2048, 1024, 512))
        assertNull(Xs4IconCover.downsampleFactor(0, 100, 512))
    }

    @Test
    fun iconFileNameIsRecognisedAsIconCover() {
        val name = Xs4IconCover.fileName("content://tree/game", "fp")
        assertTrue(name.matches(Regex("xs4_[0-9a-f]{16}_[0-9a-f]{16}\\.png")))
        assertTrue(Xs4IconCover.isIconCoverUri("file:///data/covers_remote/$name"))
        assertFalse(Xs4IconCover.isIconCoverUri("file:///data/covers_remote/vndb_0123.jpg"))
        assertFalse(Xs4IconCover.isIconCoverUri(null))
    }

    @Test
    fun onlyOldXs4TempFilesAreStale() {
        val now = 100L * 60L * 1000L
        assertTrue(Xs4IconCover.isStaleTempName("xs4-123.tmp", now - 11L * 60L * 1000L, now))
        assertFalse(Xs4IconCover.isStaleTempName("xs4-123.tmp", now - 60L * 1000L, now))
        assertFalse(Xs4IconCover.isStaleTempName("vndb_abc.tmp", 0L, now))
        assertFalse(Xs4IconCover.isStaleTempName("xs4_abc.png", 0L, now))
    }
}
