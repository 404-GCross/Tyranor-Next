package com.tyranor.next.core.game.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Xs4Preflight]：GLES 3.0 判定（null 放行）与入口 ini 缺失优先级。 */
class Xs4PreflightTest {

    @Test
    fun gles3AndAboveIsAccepted() {
        assertTrue(Xs4Preflight.hasGles3(0x30000))
        assertTrue(Xs4Preflight.hasGles3(0x30001))
        assertTrue(Xs4Preflight.hasGles3(0x30200))
    }

    @Test
    fun gles2IsRejected() {
        assertFalse(Xs4Preflight.hasGles3(0x20000))
        assertFalse(Xs4Preflight.hasGles3(0x2FFFF))
    }

    @Test
    fun unknownConfigurationInfoIsAllowed() {
        assertTrue(Xs4Preflight.hasGles3(null))
        // 0 = 系统未报告版本，放行；负值同理
        assertTrue(Xs4Preflight.hasGles3(0))
        assertTrue(Xs4Preflight.hasGles3(-1))
    }

    @Test
    fun missingIniFailsBeforeGles() {
        assertEquals(LaunchResult.Failure.Xs4IniMissing, Xs4Preflight.check(iniFound = false, reqGlEsVersion = 0x20000))
    }

    @Test
    fun gles2WithIniIsRejected() {
        assertEquals(
            LaunchResult.Failure.Xs4Gles3Required,
            Xs4Preflight.check(iniFound = true, reqGlEsVersion = 0x20000),
        )
    }

    @Test
    fun passesWhenIniFoundAndGles3Supported() {
        assertNull(Xs4Preflight.check(iniFound = true, reqGlEsVersion = 0x30000))
        assertNull(Xs4Preflight.check(iniFound = true, reqGlEsVersion = null))
    }
}
