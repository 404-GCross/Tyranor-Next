package com.tyranor.next.scanner

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoverScrapeTaskState(
    val running: Boolean = false,
    val result: CoverScrapeResult? = null,
    val error: String? = null,
    val eventId: Long = 0L,
)

object CoverScrapeTaskManager {
    val state: MutableState<CoverScrapeTaskState> = mutableStateOf(CoverScrapeTaskState())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null
    private var nextEventId = 0L

    fun start(context: Context, games: List<ScanGame>? = null): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (job?.isActive == true) return false
            state.value = CoverScrapeTaskState(running = true)
            job = scope.launch {
                try {
                    val input = games ?: EngineScanner.loadGames(appContext)
                    val result = CoverScraperService.scrapeLibraryCovers(appContext, input)
                    withContext(NonCancellable + Dispatchers.IO) {
                        EngineScanner.saveGames(appContext, result.games)
                    }
                    postFinished(result = result, error = null)
                } catch (e: CancellationException) {
                    postFinished(result = null, error = "批量刮削已取消")
                    throw e
                } catch (e: Exception) {
                    postFinished(result = null, error = e.message ?: "批量刮削失败")
                } finally {
                    synchronized(lock) {
                        job = null
                    }
                }
            }
        }
        return true
    }

    fun clearFinished(eventId: Long) {
        val current = state.value
        if (!current.running && current.eventId == eventId) {
            state.value = current.copy(result = null, error = null)
        }
    }

    private suspend fun postFinished(result: CoverScrapeResult?, error: String?) {
        val eventId = synchronized(lock) {
            nextEventId += 1
            nextEventId
        }
        withContext(Dispatchers.Main.immediate) {
            state.value = CoverScrapeTaskState(
                running = false,
                result = result,
                error = error,
                eventId = eventId,
            )
        }
    }
}
