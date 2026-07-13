package com.provender.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.provender.ai.LlmBackend
import com.provender.ai.ModelVariant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-level preferences (model variant, inference backend). */
interface AppSettingsRepository {
    val selectedVariant: StateFlow<ModelVariant>
    val backend: StateFlow<LlmBackend>

    fun setSelectedVariant(variant: ModelVariant)
    fun setBackend(backend: LlmBackend)
}

@Singleton
class DefaultAppSettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
) : AppSettingsRepository {

    private val variantFlow = MutableStateFlow(
        readEnum(KEY_MODEL_VARIANT, ModelVariant.E2B),
    )
    private val backendFlow = MutableStateFlow(
        readEnum(KEY_BACKEND, LlmBackend.CPU),
    )

    override val selectedVariant: StateFlow<ModelVariant> = variantFlow.asStateFlow()
    override val backend: StateFlow<LlmBackend> = backendFlow.asStateFlow()

    override fun setSelectedVariant(variant: ModelVariant) {
        prefs.edit { putString(KEY_MODEL_VARIANT, variant.name) }
        variantFlow.value = variant
    }

    override fun setBackend(backend: LlmBackend) {
        prefs.edit { putString(KEY_BACKEND, backend.name) }
        backendFlow.value = backend
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val stored = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(default)
    }

    private companion object {
        const val KEY_MODEL_VARIANT = "model_variant"
        const val KEY_BACKEND = "llm_backend"
    }
}
