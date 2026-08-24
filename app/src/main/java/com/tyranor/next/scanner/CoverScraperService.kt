package com.tyranor.next.scanner

import android.content.Context
import android.net.Uri
import com.tyranor.next.settings.AppSettingsStore
import com.tyranor.next.settings.HikarinagiAuthService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class CoverScrapeResult(
    val games: List<ScanGame>,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
)

object CoverScraperService {
    fun scrapeLibraryCovers(context: Context, games: List<ScanGame>): CoverScrapeResult {
        val onlyMissing = AppSettingsStore.isCoverScraperOnlyMissing(context)
        val sources = AppSettingsStore.getCoverScraperSourceOrder(context)
            .filter { AppSettingsStore.isCoverScraperSourceEnabled(context, it) }

        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0

        val updatedGames = games.map { game ->
            val local = runCatching { EngineScanner.applyLocalCover(context, game) }.getOrDefault(game)
            if (onlyMissing && !local.coverUri.isNullOrBlank()) {
                if (local.coverUri != game.coverUri) updatedCount++ else skippedCount++
                return@map local
            }

            val searchBase = if (onlyMissing) local else local.copy(coverUri = null)
            val scraped = sources.firstNotNullOfOrNull { source ->
                runCatching { scrapeWithSource(context, searchBase, source) }.getOrNull()
                    ?.asCoverOnlyUpdateFrom(local)
            }
            if (scraped != null && !scraped.coverUri.isNullOrBlank()) {
                updatedCount++
                scraped
            } else {
                if (local.coverUri != game.coverUri) {
                    updatedCount++
                } else if (sources.isEmpty()) {
                    skippedCount++
                } else {
                    failedCount++
                }
                local
            }
        }
        return CoverScrapeResult(updatedGames, updatedCount, skippedCount, failedCount)
    }

    private fun scrapeWithSource(context: Context, game: ScanGame, source: String): ScanGame? =
        when (source) {
            AppSettingsStore.COVER_SOURCE_HIKARINAGI -> HikarinagiCoverSource.fetchBestCover(context, game)
            AppSettingsStore.COVER_SOURCE_BANGUMI -> BangumiCoverSource.fetchBestCover(context, game)
            AppSettingsStore.COVER_SOURCE_STEAM -> SteamCoverSource.fetchBestCover(context, game)
            AppSettingsStore.COVER_SOURCE_VNDB -> VndbCoverService.fetchBestCover(context, game)
            else -> null
        }
}

private object HikarinagiCoverSource {
    private const val SEARCH_URL = "https://api.hikarinagi.org/v3/search"
    private const val IMAGE_BASE_URL = "https://imagesp.yurari.moe/"

    fun fetchBestCover(context: Context, game: ScanGame): ScanGame? {
        val token = HikarinagiAuthService.getValidAccessToken(context) ?: return null
        val query = cleanTitle(game.title)
        if (query.isBlank()) return null

        val url = "$SEARCH_URL?q=${query.urlEncode()}&types=galgame&page=1&page_size=5"
        val json = httpJson(url, headers = mapOf("Authorization" to "Bearer $token")) ?: return null
        if (!json.optBoolean("success", false)) return null
        val items = json.optJSONObject("data")?.optJSONArray("items") ?: return null

        val candidate = (0 until items.length()).asSequence()
            .mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("type") == "galgame" }
            .mapNotNull { item ->
                val cover = item.optJSONObject("cover") ?: return@mapNotNull null
                val coverUrl = resolveHikarinagiImageUrl(cover.optString("url", ""))
                if (coverUrl.isBlank()) return@mapNotNull null
                CoverCandidate(
                    coverUrl = coverUrl,
                    score = cover.optInt("votes", 0),
                )
            }
            .maxByOrNull { it.score }
            ?: return null

        val cover = CoverImageCache.download(context, candidate.coverUrl, "hikarinagi_${stableKey(game.uri)}")
            ?: return null
        return game.copy(
            coverUri = cover,
            coverSource = AppSettingsStore.COVER_SOURCE_HIKARINAGI,
        )
    }

    private fun resolveHikarinagiImageUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return ""
        if (parsed.isAbsolute) return parsed.toString()
        return Uri.parse(IMAGE_BASE_URL).buildUpon().appendEncodedPath(value.trimStart('/')).build().toString()
    }
}

private object BangumiCoverSource {
    private const val SEARCH_URL = "https://api.bgm.tv/v0/search/subjects?limit=5&offset=0"

    fun fetchBestCover(context: Context, game: ScanGame): ScanGame? {
        val query = cleanTitle(game.title)
        if (query.isBlank()) return null
        val body = JSONObject()
            .put("keyword", query)
            .put("sort", "rank")
            .put(
                "filter",
                JSONObject()
                    .put("type", JSONArray().put(4))
                    .put("nsfw", true),
            )
            .toString()
        val json = httpJson(SEARCH_URL, method = "POST", body = body) ?: return null
        val item = json.optJSONArray("data")?.let { data ->
            (0 until data.length()).asSequence()
                .mapNotNull { data.optJSONObject(it) }
                .firstOrNull { it.optInt("type") == 4 }
        } ?: return null
        val images = item.optJSONObject("images") ?: return null
        val coverUrl = firstNonEmpty(images.optString("large", ""), images.optString("common", ""))
        if (coverUrl.isBlank()) return null
        val cover = CoverImageCache.download(context, coverUrl, "bangumi_${stableKey(game.uri)}", referer = "https://bgm.tv/")
            ?: return null
        return game.copy(
            coverUri = cover,
            coverSource = AppSettingsStore.COVER_SOURCE_BANGUMI,
        )
    }
}

private object SteamCoverSource {
    private const val SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
    private const val DETAILS_URL = "https://store.steampowered.com/api/appdetails"

    fun fetchBestCover(context: Context, game: ScanGame): ScanGame? {
        val query = cleanTitle(game.title)
        if (query.isBlank()) return null
        val search = httpJson("$SEARCH_URL?term=${query.urlEncode()}&l=schinese&cc=CN") ?: return null
        val items = search.optJSONArray("items") ?: return null
        for (i in 0 until minOf(items.length(), 3)) {
            val item = items.optJSONObject(i) ?: continue
            val appId = item.optInt("id", 0)
            if (appId <= 0) continue
            val portrait = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
            val cover = CoverImageCache.download(context, portrait, "steam_${stableKey(game.uri)}", referer = "https://store.steampowered.com/")
                ?: downloadSteamHeader(context, game, appId)
            if (cover != null) {
                return game.copy(
                    coverUri = cover,
                    coverSource = AppSettingsStore.COVER_SOURCE_STEAM,
                )
            }
        }
        return null
    }

    private fun downloadSteamHeader(context: Context, game: ScanGame, appId: Int): String? {
        val details = httpJson("$DETAILS_URL?appids=$appId&l=schinese&cc=CN") ?: return null
        val header = details.optJSONObject(appId.toString())
            ?.optJSONObject("data")
            ?.optString("header_image", "")
            .orEmpty()
        if (header.isBlank()) return null
        return CoverImageCache.download(context, header, "steam_${stableKey(game.uri)}", referer = "https://store.steampowered.com/")
    }
}

private data class CoverCandidate(
    val coverUrl: String,
    val score: Int,
)

private fun ScanGame.asCoverOnlyUpdateFrom(base: ScanGame): ScanGame =
    base.copy(
        coverUri = coverUri,
        coverSource = coverSource,
    )

private object CoverImageCache {
    private const val MAX_COVER_BYTES = 20L * 1024L * 1024L

    fun download(context: Context, imageUrl: String, prefix: String, referer: String? = null): String? {
        if (imageUrl.isBlank()) return null
        val dir = File(context.filesDir, "covers_remote")
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, "${prefix}_${stableKey(imageUrl)}.jpg")
        if (target.isFile && target.length() > 0) return Uri.fromFile(target).toString()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                referer?.let { setRequestProperty("Referer", it) }
            }
            if (conn.responseCode !in 200..299) return null
            if (conn.contentLengthLong > MAX_COVER_BYTES) return null
            var total = 0L
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_COVER_BYTES) error("cover too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }
}

private fun httpJson(
    url: String,
    method: String = "GET",
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
): JSONObject? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TyranorNext/1.0")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body != null) {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        if (conn.responseCode !in 200..299) return null
        val text = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        JSONObject(text)
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

private fun cleanTitle(s: String): String {
    val cleaned = s.replace("""\[[^\]]*\]|【[^】]*】""".toRegex(), " ")
        .replace("[\\[\\]【】]".toRegex(), " ")
        .replace("[（）()].*".toRegex(), " ")
        .replace("(?i)complete|汉化|中文版|日文版|体验版|trial|patch".toRegex(), " ")
        .replace('_', ' ')
        .trim()
    return cleaned.ifEmpty { s.trim() }
}

private fun stableKey(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(StandardCharsets.UTF_8))
    return bytes.take(8).joinToString("") { "%02x".format(it) }
}

private fun firstNonEmpty(a: String?, b: String?): String {
    return when {
        !a.isNullOrBlank() && a != "null" -> a
        !b.isNullOrBlank() && b != "null" -> b
        else -> ""
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
