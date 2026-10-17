package com.tyranor.next.core.game.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

/** [Xs4Ini] 纯函数：GameName / SaveFolder 提取、Shift-JIS 优先与 GBK 回退、BOM、容错。 */
class Xs4IniTest {

    @Test
    fun readsGameNameAndSaveFolder() {
        val values = Xs4Ini.parseText(
            """
            GameName = "Rance VI"
            SaveFolder = "SaveData2"
            """.trimIndent(),
        )

        assertEquals("Rance VI", values.gameName)
        assertEquals("SaveData2", values.saveFolder)
    }

    @Test
    fun keysAreCaseInsensitiveAndWhitespaceTolerant() {
        val values = Xs4Ini.parseText("  gamename   =   \"Spaced\"  \nsavefolder=\"Saves\"\n")

        assertEquals("Spaced", values.gameName)
        assertEquals("Saves", values.saveFolder)
    }

    @Test
    fun decodesShiftJisValue() {
        val title = "\u30e9\u30f3\u30b9".toByteArray(Charset.forName("Shift_JIS"))
        val bytes = "GameName = \"".toByteArray(Charsets.US_ASCII) + title + "\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals("\u30e9\u30f3\u30b9", Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun fallsBackToGbkWhenShiftJisHasReplacementChar() {
        // GB2312 二级汉字 E0FE：Shift-JIS 非法序列 → U+FFFD → 回退 GBK
        val title = byteArrayOf(0xE0.toByte(), 0xFE.toByte())
        val bytes = "GameName = \"".toByteArray(Charsets.US_ASCII) + title + "\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals(String(title, Charset.forName("GBK")), Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun stripsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "GameName = \"Bom Title\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals("Bom Title", Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun missingQuotesAreIgnored() {
        assertNull(Xs4Ini.parseText("GameName = Rance\nSaveFolder = Saves\n").gameName)
        assertNull(Xs4Ini.parseText("GameName = Rance\nSaveFolder = Saves\n").saveFolder)
    }

    @Test
    fun missingFieldsAndBlankValuesAreNull() {
        val values = Xs4Ini.parseText("Other = \"x\"\nGameName = \"   \"\n")

        assertNull(values.gameName)
        assertNull(values.saveFolder)
    }

    @Test
    fun commentLineIsNotAMatch() {
        assertNull(Xs4Ini.parseText(";GameName = \"Commented\"\n").gameName)
    }

    @Test
    fun gameNameDropsControlCharacters() {
        assertEquals("RanceX", Xs4Ini.parseText("GameName = \"Ra\u0000nce\tX\u0001\"\n").gameName)
    }

    @Test
    fun gameNameOnlyControlCharactersIsNull() {
        assertNull(Xs4Ini.parseText("GameName = \"\u0000\t\u0001\"\n").gameName)
    }

    @Test
    fun gameNameIsTruncatedTo128Characters() {
        val name = Xs4Ini.parseText("GameName = \"${"a".repeat(200)}\"\n").gameName

        assertEquals("a".repeat(128), name)
    }

    @Test
    fun gameNameTruncationDoesNotSplitSurrogatePairs() {
        val emoji = "\uD83D\uDE00"
        val name = Xs4Ini.parseText("GameName = \"${emoji.repeat(130)}\"\n").gameName

        assertEquals(emoji.repeat(128), name)
    }

    @Test
    fun saveFolderIsNotTruncatedByGameNameRule() {
        val folder = "f".repeat(200)

        assertEquals(folder, Xs4Ini.parseText("SaveFolder = \"$folder\"\n").saveFolder)
    }

    @Test
    fun emptyInputYieldsNulls() {
        val values = Xs4Ini.parse(ByteArray(0))

        assertNull(values.gameName)
        assertNull(values.saveFolder)
    }

    @Test
    fun gbkTitleWhoseBytesAreValidHalfwidthKanaIsDecodedAsChinese() {
        // GBK「我的游戏」「欧洲」「战国兰斯」：SJIS 解码无 U+FFFD，旧规则会误取 SJIS 半角片假名
        assertEquals("\u6211\u7684\u6e38\u620f", Xs4Ini.parse(gameName(hex("ced2b5c4d3cecfb7"))).gameName)
        assertEquals("\u6b27\u6d32", Xs4Ini.parse(gameName(hex("c5b7d6de"))).gameName)
        assertEquals("\u6218\u56fd\u5170\u65af", Xs4Ini.parse(gameName(hex("d5bdb9fac0bccbb9"))).gameName)
    }

    @Test
    fun shiftJisJapaneseTitlesAreDecodedAsJapanese() {
        // 戦国ランス / ランス・クエスト：SJIS 字节，全角假名与汉字，GBK 解码不得抢判
        assertEquals("\u6226\u56fd\u30e9\u30f3\u30b9", Xs4Ini.parse(gameName(hex("90ed8d91838983938358"))).gameName)
        assertEquals(
            "\u30e9\u30f3\u30b9\u30fb\u30af\u30a8\u30b9\u30c8",
            Xs4Ini.parse(gameName(hex("8389839383588145834e834783588367"))).gameName,
        )
    }

    @Test
    fun gbkTitleMixedWithAsciiIsDecodedAsChinese() {
        val title = "Rance \u6211\u7684\u6e38\u620f VI"
        val bytes = "GameName = \"Rance ".toByteArray(Charsets.US_ASCII) +
            hex("ced2b5c4d3cecfb7") +
            " VI\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals(title, Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun gbkSaveFolderIsDecodedWithSameRule() {
        val bytes = "GameName = \"x\"\nSaveFolder = \"".toByteArray(Charsets.US_ASCII) +
            hex("c5b7d6de") + "\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals("\u6b27\u6d32", Xs4Ini.parse(bytes).saveFolder)
    }

    @Test
    fun replacementCharWithInvalidGbkKeepsShiftJisResult() {
        // 0x81 0x20：SJIS 与 GBK 均非法 → 保留带 U+FFFD 的 SJIS 结果，不返回空
        val bytes = "GameName = \"".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x81.toByte(), 0x20, 0x41) +
            "\"\n".toByteArray(Charsets.US_ASCII)

        assertEquals("\uFFFD A", Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun bomThenGbkTitleIsDecodedAsChinese() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + gameName(hex("ced2b5c4d3cecfb7"))

        assertEquals("\u6211\u7684\u6e38\u620f", Xs4Ini.parse(bytes).gameName)
    }

    @Test
    fun pickDecodedFollowsReplacementAndHalfwidthKanaRules() {
        assertEquals("ﾎﾒ", Xs4Ini.pickDecoded(sjis = "ﾎﾒ", gbk = null))
        assertEquals("\u6211", Xs4Ini.pickDecoded(sjis = "ﾎﾒ", gbk = "\u6211"))
        assertEquals("\uFFFDA", Xs4Ini.pickDecoded(sjis = "\uFFFDA", gbk = null))
        assertEquals("\u6211", Xs4Ini.pickDecoded(sjis = "\uFFFDA", gbk = "\u6211"))
        // 无 CJK 统一表意文字的 GBK 结果不触发回退
        assertEquals("ｱｲｳ", Xs4Ini.pickDecoded(sjis = "ｱｲｳ", gbk = "ab"))
        // 半角假名占比不足 50%（1/3）：保留 SJIS
        assertEquals("ｱ\u6226\u30e9", Xs4Ini.pickDecoded(sjis = "ｱ\u6226\u30e9", gbk = "\u6211"))
        // 半角假名占比恰好 50%（1/2）：视为可疑，取 GBK
        assertEquals("\u6211", Xs4Ini.pickDecoded(sjis = "ｱ\u6226", gbk = "\u6211"))
        assertEquals("Rance", Xs4Ini.pickDecoded(sjis = "Rance", gbk = "Rance"))
    }

    private fun gameName(title: ByteArray): ByteArray =
        "GameName = \"".toByteArray(Charsets.US_ASCII) + title + "\"\n".toByteArray(Charsets.US_ASCII)

    /** 十六进制字节串（无空格，小写或大写均可）→ 字节数组；测试字节不依赖平台默认编码。 */
    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
