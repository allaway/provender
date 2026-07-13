package com.provender.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.ai.LlmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugLlmUiState(
    val prompt: String = "",
    val response: String? = null,
    val error: String? = null,
    val isRunning: Boolean = false,
    val lastLatencyMillis: Long? = null,
)

/** Backs the hidden debug screen; the only legitimate caller of [LlmEngine.rawPrompt]. */
@HiltViewModel
class DebugLlmViewModel @Inject constructor(
    private val llmEngine: LlmEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugLlmUiState())
    val uiState: StateFlow<DebugLlmUiState> = _uiState.asStateFlow()

    fun onPromptChange(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    fun onRun() {
        val prompt = _uiState.value.prompt
        if (prompt.isBlank() || _uiState.value.isRunning) return
        _uiState.update { it.copy(isRunning = true, error = null, response = null) }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            llmEngine.rawPrompt(prompt)
                .onSuccess { reply ->
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            response = reply,
                            lastLatencyMillis = System.currentTimeMillis() - startedAt,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            error = throwable.message ?: "Unknown engine error",
                            lastLatencyMillis = System.currentTimeMillis() - startedAt,
                        )
                    }
                }
        }
    }
}
