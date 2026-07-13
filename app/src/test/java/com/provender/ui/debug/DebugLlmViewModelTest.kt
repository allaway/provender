package com.provender.ui.debug

import com.provender.ai.FakeLlmEngine
import com.provender.ai.ModelNotReadyException
import com.provender.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebugLlmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `run sends the prompt and stores the response with latency`() = runTest {
        val engine = FakeLlmEngine()
        val viewModel = DebugLlmViewModel(engine)

        viewModel.onPromptChange("list three beans")
        viewModel.onRun()

        val state = viewModel.uiState.value
        assertEquals("echo: list three beans", state.response)
        assertNull(state.error)
        assertTrue(!state.isRunning)
        assertNotNull(state.lastLatencyMillis)
        assertEquals(listOf("list three beans"), engine.rawPromptCalls)
    }

    @Test
    fun `engine failure surfaces as an error message`() = runTest {
        val engine = FakeLlmEngine().apply {
            rawPromptResult = { Result.failure(ModelNotReadyException("Model not downloaded")) }
        }
        val viewModel = DebugLlmViewModel(engine)

        viewModel.onPromptChange("hi")
        viewModel.onRun()

        val state = viewModel.uiState.value
        assertEquals("Model not downloaded", state.error)
        assertNull(state.response)
    }

    @Test
    fun `blank prompts are ignored`() = runTest {
        val engine = FakeLlmEngine()
        val viewModel = DebugLlmViewModel(engine)

        viewModel.onRun()

        assertTrue(engine.rawPromptCalls.isEmpty())
    }
}
