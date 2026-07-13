package com.provender.matching

/**
 * Deterministic string similarity for inventory diffing (SPEC §3.2). Pure Kotlin, no model.
 *
 * [similarity] blends three signals over normalized names:
 *  - Jaro-Winkler on the whole strings (catches typos and OCR noise),
 *  - Jaccard over word tokens (catches reordering: "beans black" = "black beans"),
 *  - a fixed subset score when one token set contains the other (catches brand prefixes:
 *    "trader joe black bean" ~ "black bean").
 */
object Similarity {

    /** Score at or above which two normalized names are considered the same item. */
    const val DEFAULT_THRESHOLD = 0.85

    /** Fixed score for a strict token-subset match; above threshold but below exactness. */
    private const val SUBSET_SCORE = 0.90

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val tokensA = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() }.toSet()
        val intersection = tokensA intersect tokensB
        val jaccard =
            if (tokensA.isEmpty() || tokensB.isEmpty()) 0.0
            else intersection.size.toDouble() / (tokensA union tokensB).size
        val subset = if (
            intersection.isNotEmpty() &&
            intersection.size == minOf(tokensA.size, tokensB.size)
        ) {
            SUBSET_SCORE
        } else {
            0.0
        }
        return maxOf(jaroWinkler(a, b), jaccard, subset)
    }

    /** Standard Jaro-Winkler with the usual 0.1 prefix scale, capped at a 4-char prefix. */
    fun jaroWinkler(a: String, b: String): Double {
        val jaro = jaro(a, b)
        var prefix = 0
        for (i in 0 until minOf(a.length, b.length, 4)) {
            if (a[i] == b[i]) prefix++ else break
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    fun jaro(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val window = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)

        var matches = 0
        for (i in a.indices) {
            val from = (i - window).coerceAtLeast(0)
            val to = (i + window).coerceAtMost(b.length - 1)
            for (j in from..to) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var j = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[j]) j++
            if (a[i] != b[j]) transpositions++
            j++
        }

        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
    }
}
