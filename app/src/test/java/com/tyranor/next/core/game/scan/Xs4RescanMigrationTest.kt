package com.tyranor.next.core.game.scan

import com.tyranor.next.core.engine.EngineType
import com.tyranor.next.core.game.model.ScanGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** System4 定点重检测的纯函数部分（[Xs4RescanMigration]）。 */
class Xs4RescanMigrationTest {

    private fun game(
        engine: EngineType,
        title: String = "Rance6",
        uri: String = "/games/Rance6",
    ) = ScanGame(
        title = title,
        uri = uri,
        engine = engine,
        launchTarget = "DIR",
        coverUri = "file:///cover.png",
        vndbId = "v123",
        openTime = 42L,
    )

    private fun system4Detection(titleHint: String? = "Rance VI", launchTarget: String = "DIR") =
        EngineScanner.Detection(EngineType.XSYS4, 97, launchTarget, titleHint = titleHint)

    @Test
    fun targetsOnlyXsys35Entries() {
        val games = listOf(game(EngineType.ONS), game(EngineType.XSYS35), game(EngineType.XSYS4))

        assertEquals(listOf(EngineType.XSYS35), Xs4RescanMigration.targetsOf(games).map { it.engine })
    }

    @Test
    fun targetsEmptyWithoutXsys35() {
        assertTrue(Xs4RescanMigration.targetsOf(listOf(game(EngineType.ONS), game(EngineType.XSYS4))).isEmpty())
    }

    @Test
    fun misstoredSystem4EntryIsRedetectedAndTitleReplacedWhenStillDirName() {
        val legacy = game(EngineType.XSYS35, title = "Rance6")

        val updated = Xs4RescanMigration.applyDetection(legacy, system4Detection(), dirName = "Rance6")

        assertEquals(EngineType.XSYS4, updated.engine)
        assertEquals("DIR", updated.launchTarget)
        assertEquals("Rance VI", updated.title)
    }

    @Test
    fun userRenamedTitleIsKeptWhenRedetected() {
        val renamed = game(EngineType.XSYS35, title = "My Favourite")

        val updated = Xs4RescanMigration.applyDetection(renamed, system4Detection(), dirName = "Rance6")

        assertEquals(EngineType.XSYS4, updated.engine)
        assertEquals("My Favourite", updated.title)
    }

    @Test
    fun titleKeptWhenDetectionHasNoGameName() {
        val legacy = game(EngineType.XSYS35, title = "Rance6")

        val updated = Xs4RescanMigration.applyDetection(legacy, system4Detection(titleHint = null), dirName = "Rance6")

        assertEquals("Rance6", updated.title)
    }

    @Test
    fun otherFieldsAreNotTouchedOnRedetect() {
        val legacy = game(EngineType.XSYS35)

        val updated = Xs4RescanMigration.applyDetection(legacy, system4Detection(), dirName = "Rance6")

        assertEquals(legacy.uri, updated.uri)
        assertEquals(legacy.coverUri, updated.coverUri)
        assertEquals(legacy.vndbId, updated.vndbId)
        assertEquals(legacy.openTime, updated.openTime)
    }

    @Test
    fun genuineXsys35EntryIsUnchanged() {
        val genuine = game(EngineType.XSYS35)
        val detection = EngineScanner.Detection(EngineType.XSYS35, 96, "DIR")

        assertSame(genuine, Xs4RescanMigration.applyDetection(genuine, detection, dirName = "Rance6"))
    }

    @Test
    fun unreadableEntryIsKeptWhenDetectionIsNull() {
        val unreadable = game(EngineType.XSYS35)

        assertSame(unreadable, Xs4RescanMigration.applyDetection(unreadable, detection = null, dirName = "Rance6"))
    }

    @Test
    fun nonXsys35EntryIsNeverChanged() {
        val onsGame = game(EngineType.ONS)

        assertSame(onsGame, Xs4RescanMigration.applyDetection(onsGame, system4Detection(), dirName = "Rance6"))
    }

    @Test
    fun unknownRedetectionKeepsEntry() {
        val legacy = game(EngineType.XSYS35)
        val unknown = EngineScanner.Detection(EngineType.UNKNOWN, 0, "")

        assertSame(legacy, Xs4RescanMigration.applyDetection(legacy, unknown, dirName = "Rance6"))
    }

    private val done = Xs4RedetectOutcome.Done(system4Detection(), dirName = "Rance6")

    @Test
    fun allDoneCanMarkMigrationDone() {
        assertTrue(Xs4RescanMigration.canMarkDone(listOf(done, done)))
    }

    @Test
    fun doneWithNonSystem4ResultStillCountsAsDone() {
        val notSystem4 = Xs4RedetectOutcome.Done(EngineScanner.Detection(EngineType.XSYS35, 96, "DIR"), "Rance6")

        assertTrue(Xs4RescanMigration.canMarkDone(listOf(done, notSystem4)))
    }

    @Test
    fun missingEntryBlocksMarkingMigrationDone() {
        assertFalse(Xs4RescanMigration.canMarkDone(listOf(done, Xs4RedetectOutcome.Missing)))
    }

    @Test
    fun unreadableEntryBlocksMarkingMigrationDone() {
        assertFalse(Xs4RescanMigration.canMarkDone(listOf(done, Xs4RedetectOutcome.Unreadable)))
    }

    @Test
    fun noTargetsCanMarkMigrationDone() {
        assertTrue(Xs4RescanMigration.canMarkDone(emptyList()))
    }
}
