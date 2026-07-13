package com.provender.network

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.provender.ai.ModelVariant
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams a `.litertlm` weights file to app storage with resume support (HTTP Range against
 * a `.part` file). Wi-Fi-only via the UNMETERED constraint set by the enqueuer; this is the
 * one-time, non-AI network use called out in SPEC.md.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return failure("Missing download URL")
        val destPath = inputData.getString(KEY_DEST) ?: return failure("Missing destination")
        val hfPage = inputData.getString(KEY_HF_PAGE)
        val dest = File(destPath)
        val part = File(destPath + ".part")
        part.parentFile?.mkdirs()

        val existingBytes = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(url)
            .apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> return failure(
                        "Hugging Face requires accepting Google's Gemma license before " +
                            "downloading. Open ${hfPage ?: "the model page"} in a browser, " +
                            "accept the license, download the file, then use Import here.",
                    )
                    !response.isSuccessful -> return failure("Download failed (HTTP ${response.code})")
                }

                val body = response.body ?: return failure("Empty response from server")

                // 206 = resuming; anything else means the server sent the whole file again.
                val resuming = response.code == 206
                var written = if (resuming) existingBytes else 0L
                val totalBytes = body.contentLength()
                    .takeIf { it > 0 }
                    ?.plus(if (resuming) existingBytes else 0L)

                body.byteStream().use { source ->
                    java.io.FileOutputStream(part, resuming).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var lastReportedPercent = -1
                        while (true) {
                            if (isStopped) throw IOException("Download cancelled")
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (totalBytes != null) {
                                val percent = ((written * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    setProgress(workDataOf(PROGRESS_PERCENT to percent))
                                }
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(dest)) return failure("Could not move the model into place")
            Result.success()
        } catch (e: IOException) {
            // Keep the .part file so a retry resumes instead of starting over.
            failure(e.message ?: "Network error during download")
        }
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message))

    companion object {
        const val KEY_URL = "url"
        const val KEY_DEST = "dest"
        const val KEY_HF_PAGE = "hfPage"
        const val KEY_ERROR = "error"
        const val PROGRESS_PERCENT = "percent"

        fun inputData(variant: ModelVariant, destination: File): Data = workDataOf(
            KEY_URL to variant.downloadUrl,
            KEY_DEST to destination.absolutePath,
            KEY_HF_PAGE to variant.huggingFacePage,
        )
    }
}
