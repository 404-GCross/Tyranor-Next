package com.example.tyranornext.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tyranornext.scanner.EngineLauncher
import com.example.tyranornext.scanner.EngineScanner
import com.example.tyranornext.scanner.ScanGame

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var recentGames by remember { mutableStateOf(EngineScanner.loadRecentGames(context)) }
    var launchError by remember { mutableStateOf<String?>(null) }

    fun launch(game: ScanGame) {
        launchError = EngineLauncher.launch(context, game)
        recentGames = EngineScanner.loadRecentGames(context)
    }

    Column(modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("首页", style = MaterialTheme.typography.titleLarge)
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (recentGames.isEmpty()) {
                Text(
                    "暂无最近打开的游戏",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentGames, key = { it.uri }) { game ->
                        GameCard(game, onClick = { launch(game) })
                    }
                }
            }

            launchError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}
