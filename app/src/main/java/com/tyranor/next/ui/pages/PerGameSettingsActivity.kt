package com.tyranor.next.ui.pages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tyranor.next.scanner.EngineType
import com.tyranor.next.scanner.ScanGame
import com.tyranor.next.theme.TyranorNextTheme

class PerGameSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val game = intent.readScanGame()
        if (game == null) {
            finish()
            return
        }

        setContent {
            TyranorNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PerGameSettingsScreen(game = game)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_ENGINE = "extra_engine"
        private const val EXTRA_LAUNCH_TARGET = "extra_launch_target"
        private const val EXTRA_COVER_URI = "extra_cover_uri"
        private const val EXTRA_VNDB_ID = "extra_vndb_id"
        private const val EXTRA_METADATA_TITLE = "extra_metadata_title"

        fun createIntent(context: Context, game: ScanGame): Intent {
            return Intent(context, PerGameSettingsActivity::class.java).apply {
                putExtra(EXTRA_TITLE, game.title)
                putExtra(EXTRA_URI, game.uri)
                putExtra(EXTRA_ENGINE, game.engine.name)
                putExtra(EXTRA_LAUNCH_TARGET, game.launchTarget)
                game.coverUri?.let { putExtra(EXTRA_COVER_URI, it) }
                game.vndbId?.let { putExtra(EXTRA_VNDB_ID, it) }
                game.metadataTitle?.let { putExtra(EXTRA_METADATA_TITLE, it) }
            }
        }

        private fun Intent.readScanGame(): ScanGame? {
            val title = getStringExtra(EXTRA_TITLE) ?: return null
            val uri = getStringExtra(EXTRA_URI) ?: return null
            val engineName = getStringExtra(EXTRA_ENGINE).orEmpty()
            val engine = runCatching { EngineType.valueOf(engineName) }.getOrDefault(EngineType.UNKNOWN)
            val launchTarget = getStringExtra(EXTRA_LAUNCH_TARGET).orEmpty()

            return ScanGame(
                title = title,
                uri = uri,
                engine = engine,
                launchTarget = launchTarget,
                coverUri = getStringExtra(EXTRA_COVER_URI),
                vndbId = getStringExtra(EXTRA_VNDB_ID),
                metadataTitle = getStringExtra(EXTRA_METADATA_TITLE),
            )
        }
    }
}
