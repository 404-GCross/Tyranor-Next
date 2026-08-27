package com.tyranor.next.ui.game

import com.tyranor.next.core.engine.EngineType
import com.tyranor.next.core.game.model.ScanGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameCardItemKeyTest {
    private val game = ScanGame(
        title = "测试游戏",
        uri = "content://games/test",
        engine = EngineType.KIRIKIRI,
        launchTarget = "data.xp3",
    )

    @Test
    fun changesWhenCoverChanges() {
        val covered = game.copy(coverUri = "file:///covers/new.jpg", coverSource = "vndb")

        assertNotEquals(gameCardItemKey(game), gameCardItemKey(covered))
    }

    @Test
    fun staysStableForNonVisualLibraryFields() {
        val recentlyOpened = game.copy(openTime = 1234L)

        assertEquals(gameCardItemKey(game), gameCardItemKey(recentlyOpened))
    }
}
