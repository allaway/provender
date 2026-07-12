package com.provender.data.model

/**
 * Suggested units (SPEC.md §3.3). Free-form values are allowed; the fuzzy amounts
 * ("some", "low", "plenty") are units used with a null quantity.
 */
val QuantityUnits: List<String> = listOf(
    "count", "g", "kg", "oz", "lb", "ml", "l", "can", "jar", "bag", "box",
    "some", "low", "plenty",
)
