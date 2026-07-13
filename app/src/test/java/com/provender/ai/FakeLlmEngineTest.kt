package com.provender.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins down the LlmEngine contract that downstream phases build against. */
class FakeLlmEngineTest {

    @Test
    fun `extraction returns configured items and records the call`() = runTest {
        val engine = FakeLlmEngine()
        val photos = listOf(PhotoInput("file:///photo1.jpg"))

        val result = engine.extractInventory(photos, ocrText = "BLACK BEANS")

        assertTrue(result.isSuccess)
        assertEquals("black beans", result.getOrThrow().first().name)
        assertEquals(photos to "BLACK BEANS", engine.extractCalls.single())
    }

    @Test
    fun `failures surface as failed Results, never as exceptions`() = runTest {
        val engine = FakeLlmEngine()
        engine.extractionResult = Result.failure(IllegalStateException("model produced non-JSON"))

        val result = engine.extractInventory(emptyList())

        assertTrue(result.isFailure)
    }

    @Test
    fun `rawPrompt echoes and records for the debug screen`() = runTest {
        val engine = FakeLlmEngine()

        val result = engine.rawPrompt("hello model")

        assertEquals("echo: hello model", result.getOrThrow())
        assertEquals(listOf("hello model"), engine.rawPromptCalls)
    }

    @Test
    fun `warmUp succeeds by default and counts calls`() = runTest {
        val engine = FakeLlmEngine()

        assertTrue(engine.warmUp().isSuccess)
        assertEquals(1, engine.warmUpCount)
    }

    @Test
    fun `idea generation returns configured ideas`() = runTest {
        val engine = FakeLlmEngine()
        val request = IdeaRequest(
            inventory = listOf("black beans", "tortilla"),
            staples = listOf("salt", "oil"),
            archetype = "wrap",
            mealType = "lunch",
            savorySweet = 0.1f,
        )

        val result = engine.generateIdeas(request)

        assertTrue(result.isSuccess)
        assertEquals(request, engine.ideaCalls.single())
        assertEquals("bowl", result.getOrThrow().first().archetype)
    }
}
