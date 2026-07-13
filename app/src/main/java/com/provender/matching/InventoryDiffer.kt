package com.provender.matching

import com.provender.ai.ExtractedItem
import com.provender.data.entity.InventoryItem
import kotlin.math.abs

/** A matched pair whose quantity moved (or firmed up from a fuzzy amount). */
data class ChangedQuantity(
    val item: InventoryItem,
    val extracted: ExtractedItem,
)

/** Three-part diff of a snapshot against a location's current inventory (SPEC §3.2). */
data class SnapshotDiff(
    /** Extracted items with no inventory counterpart — proposed additions. */
    val newItems: List<ExtractedItem>,
    /** Matched pairs whose quantities differ — proposed quantity updates. */
    val changedQuantities: List<ChangedQuantity>,
    /** Inventory items no extraction matched — user decides consumed / hidden / moved. */
    val missingItems: List<InventoryItem>,
    /** Matched pairs with no quantity signal or an unchanged quantity — just re-confirmed. */
    val confirmedItems: List<InventoryItem>,
)

/**
 * Fuzzy-matches extraction results against the location's inventory. Deterministic, pure
 * Kotlin (SPEC §2 Tier 3): greedy one-to-one assignment of the highest-scoring pairs at or
 * above [threshold], comparing normalized names.
 */
object InventoryDiffer {

    fun diff(
        extracted: List<ExtractedItem>,
        inventory: List<InventoryItem>,
        threshold: Double = Similarity.DEFAULT_THRESHOLD,
    ): SnapshotDiff {
        val normalizedExtracted = extracted.map { NameNormalizer.normalize(it.name) }

        data class Candidate(val extractedIndex: Int, val itemIndex: Int, val score: Double)

        val candidates = buildList {
            normalizedExtracted.forEachIndexed { e, extractedName ->
                inventory.forEachIndexed { i, item ->
                    val score = Similarity.similarity(extractedName, item.nameNormalized)
                    if (score >= threshold) add(Candidate(e, i, score))
                }
            }
        }.sortedByDescending { it.score }

        val extractedAssigned = BooleanArray(extracted.size)
        val itemAssigned = BooleanArray(inventory.size)
        val matches = mutableListOf<Pair<Int, Int>>()
        for (candidate in candidates) {
            if (extractedAssigned[candidate.extractedIndex] || itemAssigned[candidate.itemIndex]) continue
            extractedAssigned[candidate.extractedIndex] = true
            itemAssigned[candidate.itemIndex] = true
            matches += candidate.extractedIndex to candidate.itemIndex
        }

        val changed = mutableListOf<ChangedQuantity>()
        val confirmed = mutableListOf<InventoryItem>()
        for ((e, i) in matches) {
            val extractedItem = extracted[e]
            val item = inventory[i]
            if (isQuantityChange(oldQuantity = item.quantity, newQuantity = extractedItem.quantity)) {
                changed += ChangedQuantity(item = item, extracted = extractedItem)
            } else {
                confirmed += item
            }
        }

        return SnapshotDiff(
            newItems = extracted.filterIndexed { index, _ -> !extractedAssigned[index] },
            changedQuantities = changed,
            missingItems = inventory.filterIndexed { index, _ -> !itemAssigned[index] },
            confirmedItems = confirmed,
        )
    }

    /**
     * A null extracted quantity means "seen, but the model gave no amount" — that's a
     * confirmation, not a change. A null inventory quantity firming up to a number is a change.
     */
    private fun isQuantityChange(oldQuantity: Double?, newQuantity: Double?): Boolean = when {
        newQuantity == null -> false
        oldQuantity == null -> true
        else -> abs(oldQuantity - newQuantity) > 1e-9
    }
}
