package com.provender.testing

import com.provender.ai.ModelRepository
import com.provender.ai.ModelState
import com.provender.ai.ModelVariant
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory ModelRepository for ViewModel tests; records calls. */
class FakeModelRepository : ModelRepository {

    val states = MutableStateFlow<Map<ModelVariant, ModelState>>(emptyMap())

    /** Calls recorded oldest-first, e.g. "download:E2B", "import:E2B:content://x". */
    val calls = mutableListOf<String>()

    /** When set, importModel throws this. */
    var importFailure: Exception? = null

    fun setState(variant: ModelVariant, state: ModelState) {
        states.value = states.value + (variant to state)
    }

    override fun observeState(variant: ModelVariant): Flow<ModelState> =
        states.map { it[variant] ?: ModelState.NotDownloaded }

    override fun installedModelFile(variant: ModelVariant): File? =
        if (states.value[variant] is ModelState.Downloaded) File("/fake/${variant.fileName}") else null

    override fun startDownload(variant: ModelVariant) {
        calls += "download:${variant.name}"
        setState(variant, ModelState.Downloading(null))
    }

    override fun cancelDownload(variant: ModelVariant) {
        calls += "cancel:${variant.name}"
        setState(variant, ModelState.NotDownloaded)
    }

    override fun deleteModel(variant: ModelVariant) {
        calls += "delete:${variant.name}"
        setState(variant, ModelState.NotDownloaded)
    }

    override suspend fun importModel(variant: ModelVariant, contentUri: String) {
        calls += "import:${variant.name}:$contentUri"
        importFailure?.let { throw it }
        setState(variant, ModelState.Downloaded(123L))
    }
}
