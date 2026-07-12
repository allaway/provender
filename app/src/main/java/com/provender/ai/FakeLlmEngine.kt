package com.provender.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Deterministic stand-in for the real LiteRT-LM engine (which arrives in Phase 2).
 *
 * Bound as the app-wide [LlmEngine] until then, and used by tests forever after. Responses
 * and failure modes are configurable so downstream code can exercise both paths.
 */
@Singleton
class FakeLlmEngine @Inject constructor() : LlmEngine {

    var extractionResult: Result<List<ExtractedItem>> = Result.success(
        listOf(
            ExtractedItem(
                name = "black beans",
                quantity = 3.0,
                unit = "can",
                category = "canned",
                confidence = 0.9f,
                notes = "unopened",
            ),
            ExtractedItem(
                name = "rolled oats",
                quantity = 1.0,
                unit = "count",
                category = "grain",
                confidence = 0.75f,
            ),
        ),
    )

    var ideasResult: Result<List<MealIdea>> = Result.success(
        listOf(
            MealIdea(
                name = "Black bean bowl",
                archetype = "bowl",
                ingredientsUsed = listOf("black beans", "rolled oats"),
                steps = listOf("Rinse beans", "Warm through", "Assemble bowl"),
                estMinutes = 15,
                isSweet = false,
            ),
        ),
    )

    /** Simulated inference latency; keep 0 in tests. */
    var delayMillis: Long = 0

    /** Recorded calls, oldest first, for assertions. */
    val extractCalls = mutableListOf<Pair<List<PhotoInput>, String?>>()
    val ideaCalls = mutableListOf<IdeaRequest>()

    override suspend fun extractInventory(
        photos: List<PhotoInput>,
        ocrText: String?,
    ): Result<List<ExtractedItem>> {
        extractCalls += photos to ocrText
        if (delayMillis > 0) delay(delayMillis)
        return extractionResult
    }

    override suspend fun generateIdeas(request: IdeaRequest): Result<List<MealIdea>> {
        ideaCalls += request
        if (delayMillis > 0) delay(delayMillis)
        return ideasResult
    }
}
