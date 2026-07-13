package com.provender.ai

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Manages model weight files on disk: download (Wi-Fi only, via WorkManager), import from a
 * user-picked document, delete, and state observation. Weight files live in
 * `filesDir/models/` — app-private, excluded from backups, never committed anywhere.
 */
interface ModelRepository {

    fun observeState(variant: ModelVariant): Flow<ModelState>

    /** Absolute path of the installed weights, or null when not fully downloaded. */
    fun installedModelFile(variant: ModelVariant): File?

    /** Enqueues (or keeps) the Wi-Fi-only background download for [variant]. */
    fun startDownload(variant: ModelVariant)

    fun cancelDownload(variant: ModelVariant)

    fun deleteModel(variant: ModelVariant)

    /**
     * Copies a user-picked `.litertlm` document (content: URI string) into place for
     * [variant], reporting progress through [observeState]. The fallback path for the
     * license-gated Hugging Face downloads.
     */
    suspend fun importModel(variant: ModelVariant, contentUri: String)
}
