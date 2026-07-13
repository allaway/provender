package com.provender.ai

/** Prompt templates for the on-device model. Keep them terse — small models drift. */
object Prompts {

    /** SPEC §4 vision extraction contract: ONLY a JSON array, fixed keys. */
    fun extraction(ocrText: String?): String = buildString {
        appendLine(
            "You are an inventory scanner. Look at the photo(s) of a kitchen storage area " +
                "and list every distinct food item you can identify.",
        )
        if (!ocrText.isNullOrBlank()) {
            appendLine("Text found on labels by OCR (may help with exact names):")
            appendLine(ocrText.trim())
        }
        appendLine(
            "Respond with ONLY a JSON array, no prose, no code fences. Each element must " +
                "have exactly these keys:",
        )
        appendLine(
            """{"name": string, "quantity": number or null, "unit": string or null, """ +
                """"category": one of [produce, dairy, protein, grain, canned, condiment, """ +
                """spice, snack, baking, beverage, frozen, other], "confidence": number """ +
                """between 0 and 1, "notes": string or null}""",
        )
        append("""Example: [{"name":"black beans","quantity":3,"unit":"can","category":"canned","confidence":0.9,"notes":"unopened"}]""")
    }

    /** The one retry allowed by SPEC §4 when output fails to parse. */
    const val JSON_REPRIMAND: String =
        "Your previous reply was not valid JSON. Respond again with ONLY the JSON array — " +
            "no explanation, no markdown, no code fences."

    fun ideas(request: IdeaRequest): String = buildString {
        appendLine(
            "Invent 3 to 5 simple \"${request.archetype}\" meal ideas for ${request.mealType} " +
                "using ONLY the ingredients listed below. Do not use any other ingredients.",
        )
        appendLine("Available ingredients: ${request.inventory.joinToString(", ")}")
        appendLine("Pantry staples (also allowed): ${request.staples.joinToString(", ")}")
        val taste = when {
            request.savorySweet < 0.34f -> "savory"
            request.savorySweet > 0.66f -> "sweet"
            else -> "either savory or sweet"
        }
        appendLine("Taste direction: $taste.")
        appendLine(
            "Respond with ONLY a JSON array, no prose, no code fences. Each element must " +
                "have exactly these keys:",
        )
        append(
            """{"name": string, "archetype": "${request.archetype}", "ingredientsUsed": """ +
                """array of strings (subset of the lists above), "steps": array of 3-6 """ +
                """short strings, "estMinutes": integer, "isSweet": boolean}""",
        )
    }
}
