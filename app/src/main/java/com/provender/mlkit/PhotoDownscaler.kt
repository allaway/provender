package com.provender.mlkit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downscales snapshot photos before inference (SPEC §2: ≤ 768–1024 px long edge). Writes a
 * `<name>.small.jpg` sibling and returns its path; falls back to the original on failure.
 */
@Singleton
class PhotoDownscaler @Inject constructor() {

    fun downscale(photoPath: String, maxLongEdgePx: Int = 1024): String {
        return runCatching {
            val source = File(photoPath)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return photoPath
            if (longEdge <= maxLongEdgePx) return photoPath

            // Power-of-two subsampling first (memory), then an exact scale.
            var sampleSize = 1
            while (longEdge / (sampleSize * 2) >= maxLongEdgePx) sampleSize *= 2
            val decoded = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return photoPath

            val decodedLongEdge = maxOf(decoded.width, decoded.height)
            val scaled = if (decodedLongEdge > maxLongEdgePx) {
                val ratio = maxLongEdgePx.toFloat() / decodedLongEdge
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }

            val target = File(source.parentFile, source.nameWithoutExtension + ".small.jpg")
            target.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            target.absolutePath
        }.getOrDefault(photoPath)
    }
}
