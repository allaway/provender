package com.provender.testing

import com.provender.ai.LlmBackend
import com.provender.ai.ModelVariant
import com.provender.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppSettingsRepository(
    initialVariant: ModelVariant = ModelVariant.E2B,
    initialBackend: LlmBackend = LlmBackend.CPU,
) : AppSettingsRepository {

    private val variantFlow = MutableStateFlow(initialVariant)
    private val backendFlow = MutableStateFlow(initialBackend)

    override val selectedVariant: StateFlow<ModelVariant> = variantFlow.asStateFlow()
    override val backend: StateFlow<LlmBackend> = backendFlow.asStateFlow()

    override fun setSelectedVariant(variant: ModelVariant) {
        variantFlow.value = variant
    }

    override fun setBackend(backend: LlmBackend) {
        backendFlow.value = backend
    }
}
