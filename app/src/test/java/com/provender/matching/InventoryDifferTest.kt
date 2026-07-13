package com.provender.matching

import com.provender.ai.ExtractedItem
import com.provender.data.entity.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryDifferTest {

    private var nextId = 1L

    private fun item(name: String, quantity: Double? = null): InventoryItem = InventoryItem(
        id = nextId++,
        name = name,
        nameNormalized = NameNormalizer.normalize(name),
        quantity = quantity,
        locationId = 1L,
        lastSeenAt = 0L,
    )

    @Test
    fun `unmatched extractions are new, unmatched inventory is missing`() {
        val inventory = listOf(item("Milk", 1.0))
        val extracted = listOf(ExtractedItem(name = "orange juice", quantity = 1.0))

        val diff = InventoryDiffer.diff(extracted, inventory)

        assertEquals(listOf("orange juice"), diff.newItems.map { it.name })
        assertEquals(listOf("Milk"), diff.missingItems.map { it.name })
        assertTrue(diff.changedQuantities.isEmpty())
        assertTrue(diff.confirmedItems.isEmpty())
    }

    @Test
    fun `matching with same quantity confirms the item`() {
        val inventory = listOf(item("Black beans", 3.0))
        val extracted = listOf(ExtractedItem(name = "black beans", quantity = 3.0))

        val diff = InventoryDiffer.diff(extracted, inventory)

        assertEquals(listOf("Black beans"), diff.confirmedItems.map { it.name })
        assertTrue(diff.newItems.isEmpty())
        assertTrue(diff.missingItems.isEmpty())
        assertTrue(diff.changedQuantities.isEmpty())
    }

    @Test
    fun `quantity difference lands in changedQuantities`() {
        val inventory = listOf(item("Black beans", 3.0))
        val extracted = listOf(ExtractedItem(name = "black beans", quantity = 1.0))

        val diff = InventoryDiffer.diff(extracted, inventory)

        val change = diff.changedQuantities.single()
        assertEquals(3.0, change.item.quantity!!, 0.0)
        assertEquals(1.0, change.extracted.quantity!!, 0.0)
    }

    @Test
    fun `null extracted quantity confirms instead of changing`() {
        val inventory = listOf(item("Flour", 2.0))
        val extracted = listOf(ExtractedItem(name = "flour", quantity = null))

        val diff = InventoryDiffer.diff(extracted, inventory)

        assertEquals(1, diff.confirmedItems.size)
        assertTrue(diff.changedQuantities.isEmpty())
    }

    @Test
    fun `null inventory quantity firming up to a number is a change`() {
        val inventory = listOf(item("Rice", quantity = null))
        val extracted = listOf(ExtractedItem(name = "rice", quantity = 2.0))

        val diff = InventoryDiffer.diff(extracted, inventory)

        assertEquals(1, diff.changedQuantities.size)
    }

    @Test
    fun `fuzzy names still match - plurals, reorder, brand words`() {
        val inventory = listOf(
            item("Tomatoes", 4.0),
            item("black bean"),
            item("olive oil"),
        )
        val extracted = listOf(
            ExtractedItem(name = "tomato", quantity = 4.0),
            ExtractedItem(name = "beans, black"),
            ExtractedItem(name = "Bertolli olive oil"),
        )

        val diff = InventoryDiffer.diff(extracted, inventory)

        assertTrue(diff.newItems.isEmpty())
        assertTrue(diff.missingItems.isEmpty())
        assertEquals(3, diff.confirmedItems.size)
    }

    @Test
    fun `each inventory item is matched at most once`() {
        val inventory = listOf(item("Black beans", 3.0))
        val extracted = listOf(
            ExtractedItem(name = "black beans", quantity = 3.0),
            ExtractedItem(name = "black bean", quantity = 1.0),
        )

        val diff = InventoryDiffer.diff(extracted, inventory)

        // The better-scoring extraction wins the single inventory row; the other is new.
        assertEquals(1, diff.newItems.size)
        assertEquals(1, diff.confirmedItems.size + diff.changedQuantities.size)
        assertTrue(diff.missingItems.isEmpty())
    }

    @Test
    fun `empty inventory means everything is new`() {
        val diff = InventoryDiffer.diff(
            listOf(ExtractedItem(name = "salt"), ExtractedItem(name = "pepper")),
            emptyList(),
        )

        assertEquals(2, diff.newItems.size)
        assertTrue(diff.missingItems.isEmpty())
    }

    @Test
    fun `empty extraction marks everything missing`() {
        val diff = InventoryDiffer.diff(emptyList(), listOf(item("Milk"), item("Eggs")))

        assertEquals(2, diff.missingItems.size)
        assertTrue(diff.newItems.isEmpty())
    }
}
