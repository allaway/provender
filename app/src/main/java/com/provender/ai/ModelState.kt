package com.provender.ai

import androidx.work.WorkInfo

/** Lifecycle of a model variant's weights on this device. */
sealed interface ModelState {
    data object NotDownloaded : ModelState

    /** [progressPercent] is null while enqueued / waiting for Wi-Fi / size unknown. */
    data class Downloading(val progressPercent: Int?) : ModelState

    data class Downloaded(val sizeBytes: Long) : ModelState

    data class Failed(val message: String) : ModelState
}

/** Pure derivation of [ModelState] from disk + WorkManager facts; kept free of Android types
 *  beyond the WorkInfo.State enum so it is unit-testable on the JVM. */
object ModelStateDeriver {

    fun derive(
        fileExists: Boolean,
        fileSizeBytes: Long,
        workState: WorkInfo.State?,
        workProgressPercent: Int?,
        workErrorMessage: String?,
        importProgressPercent: Int?,
    ): ModelState = when {
        importProgressPercent != null -> ModelState.Downloading(importProgressPercent)
        fileExists -> ModelState.Downloaded(fileSizeBytes)
        workState == WorkInfo.State.RUNNING -> ModelState.Downloading(workProgressPercent)
        workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.BLOCKED ->
            ModelState.Downloading(null)
        workState == WorkInfo.State.FAILED ->
            ModelState.Failed(workErrorMessage ?: "Download failed")
        else -> ModelState.NotDownloaded
    }
}
