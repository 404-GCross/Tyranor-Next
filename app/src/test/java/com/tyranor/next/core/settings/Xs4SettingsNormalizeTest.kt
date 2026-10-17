package com.tyranor.next.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** System4 设置的纯归一化函数（非法值、大小写、空白、null 回落默认）；取值字面量同时锚定。 */
class Xs4SettingsNormalizeTest {

    @Test
    fun encodingValuesAreAnchored() {
        assertEquals("sjis", EngineSettingsStore.XSYS4_ENCODING_SJIS)
        assertEquals("gbk", EngineSettingsStore.XSYS4_ENCODING_GBK)
        assertEquals(setOf("sjis", "gbk"), EngineSettingsStore.XSYS4_ENCODING_VALUES)
    }

    @Test
    fun normalizeEncodingAcceptsCaseAndWhitespace() {
        assertEquals("gbk", EngineSettingsStore.normalizeXs4Encoding("gbk"))
        assertEquals("gbk", EngineSettingsStore.normalizeXs4Encoding(" GBK "))
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding("Sjis"))
    }

    @Test
    fun normalizeEncodingFallsBackToSjis() {
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding(null))
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding(""))
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding("   "))
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding("big5"))
        assertEquals("sjis", EngineSettingsStore.normalizeXs4Encoding("utf8"))
    }

    @Test
    fun joypadValuesAreAnchored() {
        assertEquals("auto", EngineSettingsStore.XSYS4_JOYPAD_AUTO)
        assertEquals("on", EngineSettingsStore.XSYS4_JOYPAD_ON)
        assertEquals("off", EngineSettingsStore.XSYS4_JOYPAD_OFF)
        assertEquals(setOf("auto", "on", "off"), EngineSettingsStore.XSYS4_JOYPAD_VALUES)
    }

    @Test
    fun normalizeJoypadAcceptsCaseAndWhitespace() {
        assertEquals("on", EngineSettingsStore.normalizeXs4Joypad("ON"))
        assertEquals("off", EngineSettingsStore.normalizeXs4Joypad(" off "))
        assertEquals("auto", EngineSettingsStore.normalizeXs4Joypad("Auto"))
    }

    @Test
    fun normalizeJoypadFallsBackToAuto() {
        assertEquals("auto", EngineSettingsStore.normalizeXs4Joypad(null))
        assertEquals("auto", EngineSettingsStore.normalizeXs4Joypad(""))
        assertEquals("auto", EngineSettingsStore.normalizeXs4Joypad("bogus"))
    }

    @Test
    fun msgskipDelayPresetsAreAnchored() {
        assertEquals(0, EngineSettingsStore.XSYS4_MSGSKIP_DELAY_DEFAULT)
        assertEquals(setOf(0, 100, 200, 400), EngineSettingsStore.XSYS4_MSGSKIP_DELAY_VALUES)
    }

    @Test
    fun normalizeMsgskipDelayKeepsPresets() {
        assertEquals(0, EngineSettingsStore.normalizeXs4MsgskipDelay(0))
        assertEquals(100, EngineSettingsStore.normalizeXs4MsgskipDelay(100))
        assertEquals(200, EngineSettingsStore.normalizeXs4MsgskipDelay(200))
        assertEquals(400, EngineSettingsStore.normalizeXs4MsgskipDelay(400))
    }

    @Test
    fun normalizeMsgskipDelayFallsBackToDefaultForNonPresets() {
        assertEquals(0, EngineSettingsStore.normalizeXs4MsgskipDelay(null))
        assertEquals(0, EngineSettingsStore.normalizeXs4MsgskipDelay(37))
        assertEquals(0, EngineSettingsStore.normalizeXs4MsgskipDelay(150))
        assertEquals(0, EngineSettingsStore.normalizeXs4MsgskipDelay(-100))
    }

    @Test
    fun normalizeFontPathTrimsAndNullBecomesEmpty() {
        assertEquals("", EngineSettingsStore.normalizeXs4FontPath(null))
        assertEquals("", EngineSettingsStore.normalizeXs4FontPath("   "))
        assertEquals("/data/user/0/app/files/fonts/a.ttf", EngineSettingsStore.normalizeXs4FontPath("  /data/user/0/app/files/fonts/a.ttf  "))
    }
}
