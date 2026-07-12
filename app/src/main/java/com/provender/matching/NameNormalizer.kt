package com.provender.matching

/**
 * Canonicalizes food names for matching and sorting: lowercase, trim, collapse whitespace,
 * strip punctuation, naive singularization. The matching engine (Phase 5) builds on this;
 * keep it deterministic and dependency-free.
 */
object NameNormalizer {

    /** Words the naive singularizer must leave alone. */
    private val singularExceptions = setOf(
        "hummus", "couscous", "asparagus", "molasses", "swiss", "citrus",
        "oats", // "oats" reads better than "oat" and matches recipe usage
    )

    fun normalize(raw: String): String {
        val cleaned = raw
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        return cleaned
            .split(" ")
            .joinToString(" ") { singularize(it) }
    }

    /** Naive English singularization — intentionally simple; synonyms come in Phase 5. */
    fun singularize(word: String): String {
        if (word.length <= 3 || word in singularExceptions) return word
        return when {
            word.endsWith("ies") -> word.dropLast(3) + "y" // berries -> berry
            word.endsWith("oes") -> word.dropLast(2) // tomatoes -> tomato
            word.endsWith("sses") -> word.dropLast(2) // glasses -> glass
            word.endsWith("shes") || word.endsWith("ches") ||
                word.endsWith("xes") || word.endsWith("zes") -> word.dropLast(2) // boxes -> box
            word.endsWith("ss") -> word // glass stays glass
            word.endsWith("s") -> word.dropLast(1) // beans -> bean
            else -> word
        }
    }
}
