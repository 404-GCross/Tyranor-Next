package com.tyranor.next.core.game.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * System4（xsystem4）游戏入口 ini（`System40.ini` / `AliceStart.ini`）的最小解析（纯函数）。
 *
 * 只提取两项：`GameName`（扫描期标题）与 `SaveFolder`（独立存档关闭时的存档目录名）。
 * 扫描、启动、存档三处共用本类，保证读到的值一致（单一实现）。
 *
 * 字节在 Shift-JIS 与 GBK（中文版 ini）之间择一解码，规则见 [pickDecoded]。
 * 不认识的行与键一律忽略，解析不会抛异常。
 */
object Xs4Ini {

    /** 调用方读取 ini 头部的上限字节数（与扫描器 headOf 的 64 KB 对齐）。 */
    const val HEAD_BYTES = 64 * 1024

    data class Values(
        val gameName: String?,
        val saveFolder: String?,
    )

    /** `key = "value"` 行（行首允许空白；注释行以 `;` 等开头不匹配）。 */
    private val LINE_RE = Regex("""^\s*(\w+)\s*=\s*"(.*)"""")

    /** GameName 长度上限（码点数）：标题只用于列表展示，超长值多半是损坏或恶意输入。 */
    private const val GAME_NAME_MAX_CODE_POINTS = 128

    private val SJIS: Charset = Charset.forName("Shift_JIS")
    private val GBK: Charset = Charset.forName("GBK")
    private const val REPLACEMENT = '\uFFFD'
    private val HALFWIDTH_KANA = '\uFF61'..'\uFF9F'
    private val CJK_IDEOGRAPH = '\u4E00'..'\u9FFF'

    fun parse(bytes: ByteArray): Values = parseText(decode(stripUtf8Bom(bytes)))

    fun parseText(text: String): Values {
        var gameName: String? = null
        var saveFolder: String? = null
        for (line in text.lineSequence()) {
            val match = LINE_RE.find(line) ?: continue
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
            when {
                gameName == null && key.equals("GameName", ignoreCase = true) ->
                    gameName = sanitizeGameName(match.groupValues[2])
                saveFolder == null && key.equals("SaveFolder", ignoreCase = true) ->
                    saveFolder = value.takeIf { it.isNotEmpty() }
            }
        }
        return Values(gameName = gameName, saveFolder = saveFolder)
    }

    /**
     * GameName 清洗（纯函数）：去除控制字符（NUL / 制表 / 换行等，ini 值来自不可信文件）→ 截断到
     * [GAME_NAME_MAX_CODE_POINTS] 个码点（按码点截断，不切断代理对）→ 去首尾空白；空则返回 null。
     */
    internal fun sanitizeGameName(raw: String): String? {
        val visible = buildString {
            raw.codePoints().forEach { codePoint ->
                if (!Character.isISOControl(codePoint)) appendCodePoint(codePoint)
            }
        }
        val kept = minOf(visible.codePointCount(0, visible.length), GAME_NAME_MAX_CODE_POINTS)
        val end = visible.offsetByCodePoints(0, kept)
        return visible.substring(0, end).trim().takeIf { it.isNotEmpty() }
    }

    private fun decode(bytes: ByteArray): String =
        pickDecoded(sjis = String(bytes, SJIS), gbk = decodeStrict(bytes, GBK))

    /**
     * 在 Shift-JIS 与 GBK 两份解码结果之间择一（纯函数）。
     *
     * - [sjis]：Shift-JIS 宽松解码结果（非法序列产生 U+FFFD）；[gbk]：GBK 严格解码结果，字节非法时为 null。
     * - SJIS 含 U+FFFD：GBK 合法则取 GBK；否则保留带替换符的 SJIS（不返回空）。
     * - SJIS 不含 U+FFFD，但非 ASCII 字符中半角片假名占比 ≥ 50%，且 GBK 合法并含 CJK 统一表意文字 → 取 GBK。
     *   理由：GB2312 常用汉字的首字节多落在 0xB0–0xDF，在 SJIS 中是合法的单字节半角片假名，
     *   此时 SJIS 不会产生 U+FFFD，仅靠 U+FFFD 回退会把中文标题解成乱码（如「我的游戏」→「ﾎﾒｵﾄﾓﾎﾏｷ」）。
     * - 其余取 SJIS（日文版默认编码）。
     *
     * 取舍：纯半角片假名的日文标题，且其字节恰好也是合法 GBK 并含汉字时会被误判为 GBK，属罕见情形，接受。
     * SaveFolder 与 GameName 同走此规则；SaveFolder 的合法性仍由 `Xs4Paths.sanitizeSaveFolder` 把关。
     */
    internal fun pickDecoded(sjis: String, gbk: String?): String {
        if (gbk == null) return sjis
        if (sjis.indexOf(REPLACEMENT) >= 0) return gbk
        return if (isHalfwidthKanaDominated(sjis) && gbk.any { it in CJK_IDEOGRAPH }) gbk else sjis
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun isHalfwidthKanaDominated(text: String): Boolean {
        var nonAscii = 0
        var halfwidthKana = 0
        for (ch in text) {
            if (ch.code <= 0x7F) continue
            nonAscii++
            if (ch in HALFWIDTH_KANA) halfwidthKana++
        }
        return nonAscii > 0 && halfwidthKana * 2 >= nonAscii
    }

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray =
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
}
