package com.provender.ai

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.provender.di.IoDispatcher
import com.provender.network.ModelDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class DefaultModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelRepository {

    private val modelsDir: File get() = File(context.filesDir, MODELS_DIR)

    /** Bumped after any direct file mutation (delete, import) to re-read disk state. */
    private val refreshTick = MutableStateFlow(0)

    /** Per-variant import progress; null entry = no import running. */
    private val importProgress = MutableStateFlow<Map<ModelVariant, Int?>>(emptyMap())

    override fun observeState(variant: ModelVariant): Flow<ModelState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(workName(variant)),
        importProgress,
        refreshTick,
    ) { workInfos, imports, _ ->
        val file = finalFile(variant)
        val work = workInfos.firstOrNull()
        ModelStateDeriver.derive(
            fileExists = file.exists(),
            fileSizeBytes = if (file.exists()) file.length() else 0L,
            workState = work?.state,
            workProgressPercent = work?.progress
                ?.getInt(ModelDownloadWorker.PROGRESS_PERCENT, -1)
                ?.takeIf { it >= 0 },
            workErrorMessage = work?.outputData?.getString(ModelDownloadWorker.KEY_ERROR),
            importProgressPercent = imports[variant],
        )
    }

    override fun installedModelFile(variant: ModelVariant): File? =
        finalFile(variant).takeIf { it.exists() }

    override fun startDownload(variant: ModelVariant) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(ModelDownloadWorker.inputData(variant, finalFile(variant)))
            .setConstraints(
                Constraints.Builder()
                    // Wi-Fi only per SPEC §2 — these files are multiple GB.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(workName(variant), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelDownload(variant: ModelVariant) {
        workManager.cancelUniqueWork(workName(variant))
        partFile(variant).delete()
        refreshTick.update { it + 1 }
    }

    override fun deleteModel(variant: ModelVariant) {
        workManager.cancelUniqueWork(workName(variant))
        partFile(variant).delete()
        finalFile(variant).delete()
        refreshTick.update { it + 1 }
    }

    override suspend fun importModel(variant: ModelVariant, contentUri: String) {
        withContext(ioDispatcher) {
            val uri = Uri.parse(contentUri)
            val totalBytes = context.contentResolver
                .openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?.takeIf { it > 0 }
            val part = partFile(variant)
            part.parentFile?.mkdirs()
            try {
                setImportProgress(variant, if (totalBytes != null) 0 else null)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open the selected file")
                input.use { source ->
                    part.outputStream().use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            copied += read
                            if (totalBytes != null) {
                                setImportProgress(
                                    variant,
                                    ((copied * 100) / totalBytes).toInt().coerceIn(0, 100),
                                )
                            }
                        }
                    }
                }
                if (!part.renameTo(finalFile(variant))) {
                    throw IOException("Could not move the model into place")
                }
            } catch (e: Exception) {
                part.delete()
                throw e
            } finally {
                importProgress.update { it - variant }
                refreshTick.update { it + 1 }
            }
        }
    }

    private fun setImportProgress(variant: ModelVariant, percent: Int?) {
        importProgress.update { it + (variant to percent) }
    }

    private fun finalFile(variant: ModelVariant) = File(modelsDir, variant.fileName)

    private fun partFile(variant: ModelVariant) = File(modelsDir, variant.fileName + ".part")

    private fun workName(variant: ModelVariant) = "model-download-${variant.name}"

    private companion object {
        const val MODELS_DIR = "models"
    }
}
