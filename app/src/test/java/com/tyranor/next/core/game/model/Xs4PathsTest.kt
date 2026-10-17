package com.tyranor.next.core.game.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [Xs4Paths]：独立存档目录、SaveFolder 校验（sanitize 全部边界）与入口 ini 大小写解析。 */
class Xs4PathsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scopedSaveDirUsesSafeSaveName() {
        val external = File("ext-files")
        val expected = File(File(external, "save"), "Rance VI")

        assertEquals(
            expected.absolutePath,
            Xs4Paths.scopedSaveDir(external, "/storage/emulated/0/Games/Rance VI").absolutePath,
        )
    }

    @Test
    fun gameDirSaveDirUsesIniSaveFolder() {
        val root = temporaryFolder.newFolder("Game")
        root.resolve("System40.ini").writeText("SaveFolder = \"Saves\"\n")

        assertEquals(File(root, "Saves").absolutePath, Xs4Paths.gameDirSaveDir(root).absolutePath)
    }

    @Test
    fun gameDirSaveDirFallsBackWithoutIni() {
        val root = temporaryFolder.newFolder("Game No Ini")

        assertEquals(File(root, "SaveData").absolutePath, Xs4Paths.gameDirSaveDir(root).absolutePath)
    }

    @Test
    fun gameDirSaveDirRejectsUnsafeSaveFolder() {
        val root = temporaryFolder.newFolder("Game Unsafe")
        root.resolve("AliceStart.ini").writeText("SaveFolder = \"../../escape\"\n")

        assertEquals(File(root, "SaveData").absolutePath, Xs4Paths.gameDirSaveDir(root).absolutePath)
    }

    @Test
    fun sanitizeUsesDefaultForNullOrBlank() {
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder(null))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder(""))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("   "))
    }

    @Test
    fun sanitizeRejectsParentSegments() {
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("../x"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("a/../b"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder(".."))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("a\\..\\b"))
    }

    @Test
    fun sanitizeRejectsAbsolutePaths() {
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("/data/save"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("\\\\server\\share"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("C:\\Saves"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("c:/Saves"))
    }

    @Test
    fun sanitizeRejectsDotOnlyValues() {
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("."))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder("./"))
    }

    @Test
    fun sanitizeNormalizesBackslashesAndTrims() {
        assertEquals("Saves/sub", Xs4Paths.sanitizeSaveFolder("Saves\\sub"))
        assertEquals("SaveData", Xs4Paths.sanitizeSaveFolder(" SaveData "))
        assertEquals("Saves", Xs4Paths.sanitizeSaveFolder(" Saves "))
    }

    @Test
    fun resolveIniFileIsCaseInsensitive() {
        val root = temporaryFolder.newFolder("Case Ini")
        root.resolve("SYSTEM40.INI").writeText("fake")

        assertEquals("SYSTEM40.INI", Xs4Paths.resolveIniFile(root)?.name)
    }

    @Test
    fun resolveIniFilePrefersSystem40OverAliceStart() {
        val root = temporaryFolder.newFolder("Both Ini")
        root.resolve("AliceStart.ini").writeText("fake")
        root.resolve("System40.ini").writeText("fake")

        assertEquals("System40.ini", Xs4Paths.resolveIniFile(root)?.name)
    }

    @Test
    fun resolveIniFileFindsAliceStartAlone() {
        val root = temporaryFolder.newFolder("Alice Only")
        root.resolve("alicestart.ini").writeText("fake")

        assertEquals("alicestart.ini", Xs4Paths.resolveIniFile(root)?.name)
    }

    @Test
    fun resolveIniFileIgnoresDirectoriesAndMissing() {
        val dirRoot = temporaryFolder.newFolder("Dir Named Ini")
        dirRoot.resolve("System40.ini").mkdirs()
        assertNull(Xs4Paths.resolveIniFile(dirRoot))

        val empty = temporaryFolder.newFolder("Empty")
        assertNull(Xs4Paths.resolveIniFile(empty))
    }
}
