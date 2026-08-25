package com.tyranor.next.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 从游戏封面图提取主种子色（Material You 取色：Celebi 量化 + HCT 打分）。
 * 与 InstallerX AppIconRepositoryImpl 的取色流程一致：先缩放到 ≤128px
 * 控制像素量，再量化打分得到最突出的颜色；任何一步失败都回退 null，
 * 由调用方回退手动主色。
 */
suspend fun extractSeedColorFromCover(
    context: Context,
    coverUri: String?,
): Color? {
    if (coverUri.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val decoded = context.contentResolver.openInputStream(android.net.Uri.parse(coverUri))
                ?.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext null
            currentCoroutineContext().ensureActive()
            val seed = extractSeedColor(decoded)
            // 量化是纯 CPU 计算无挂起点，取消不会中断；写回前最后确认一次，
            // 避免快速切换封面时旧任务把陈旧种子色写入全局状态
            currentCoroutineContext().ensureActive()
            seed
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}

private fun extractSeedColor(bitmap: Bitmap): Color? {
    val maxSize = 128
    val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val scale = minOf(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height,
        )
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }
    try {
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val opaquePixels = pixels.filter { (it ushr 24) >= 0x40 }.toIntArray()
        if (opaquePixels.isEmpty()) return null
        val quantized = QuantizerCelebi.quantize(opaquePixels, 128)
        val scored = Score.score(quantized, 1, Blue40.toArgb(), true)
        return scored.firstOrNull()?.let { Color(it) }
    } finally {
        // 只回收临时缩放副本，不回收调用方位图
        if (scaled !== bitmap) {
            scaled.recycle()
        }
    }
}
