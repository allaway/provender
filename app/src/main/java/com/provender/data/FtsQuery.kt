package com.provender.data

/** Turns raw user input into safe FTS4 MATCH syntax. */
object FtsQuery {

    /**
     * Returns a prefix-match query (`toma* sau*`) or null when the input has no searchable
     * tokens. Quotes and MATCH operators in user input are stripped rather than interpreted.
     */
    fun fromUserInput(input: String): String? {
        val tokens = input
            .split(Regex("\\s+"))
            // Lowercase so tokens can never form FTS operators (OR/AND/NOT are uppercase-only);
            // the default tokenizer is case-insensitive anyway.
            .map { it.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
