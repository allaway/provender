package com.provender.ai

import kotlinx.serialization.Serializable

/**
 * The single gateway to on-device generative AI (SPEC.md §4 "AI abstraction").
 *
 * The LiteRT-LM + Gemma 3n implementation arrives in Phase 2 and is the only planned v1
 * implementation; an optional cloud implementation could be added later behind this same
 * interface without touching feature code. Feature code and tests must depend on this
 * interface only — never on a concrete engine.
 */
interface LlmEngine {

    /**
     * Vision extraction: shelf/fridge photos (plus optional ML Kit OCR text as auxiliary
     * context) → structured inventory items. Returns a failed [Result] instead of throwing;
     * malformed model output must land in a recoverable error, never a crash.
     */
    suspend fun extractInventory(
        photos: List<PhotoInput>,
        ocrText: String? = null,
    ): Result<List<ExtractedItem>>

    /**
     * Text-only generation of flexible meal ideas (bowls, wraps, …) from current inventory.
     * Ideas referencing items outside `request.inventory + request.staples` are validated
     * out downstream (Phase 8), not here.
     */
    suspend fun generateIdeas(request: IdeaRequest): Result<List<MealIdea>>
}

/** A photo handed to the engine; URI string of an app-private, already-downscaled image. */
@JvmInline
value class PhotoInput(val uri: String)

/** One item extracted from photos — mirrors the strict-JSON vision contract in SPEC.md §4. */
@Serializable
data class ExtractedItem(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val confidence: Float = 0f,
    val notes: String? = null,
)

/** Inputs for flexible-recipe generation (SPEC.md §3.8). */
data class IdeaRequest(
    val inventory: List<String>,
    val staples: List<String>,
    val archetype: String,
    val mealType: String,
    /** 0.0 = fully savory … 1.0 = fully sweet. */
    val savorySweet: Float,
)

/** One generated meal idea. */
@Serializable
data class MealIdea(
    val name: String,
    val archetype: String,
    val ingredientsUsed: List<String>,
    val steps: List<String>,
    val estMinutes: Int,
    val isSweet: Boolean,
)
