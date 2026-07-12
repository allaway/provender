package com.provender.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.provender.data.ProvenderDatabase
import com.provender.data.model.Category
import com.provender.data.model.ChangeReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultInventoryRepositoryTest {

    private lateinit var db: ProvenderDatabase
    private lateinit var repository: DefaultInventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProvenderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultInventoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun pantryId(): Long {
        repository.seedDefaultLocationsIfEmpty()
        return repository.observeLocations().first().first { it.name == "Pantry" }.id
    }

    @Test
    fun `seeding is idempotent and creates the four defaults in order`() = runTest {
        repository.seedDefaultLocationsIfEmpty()
        repository.seedDefaultLocationsIfEmpty()

        val names = repository.observeLocations().first().map { it.name }

        assertEquals(listOf("Pantry", "Fridge", "Freezer", "Spices"), names)
    }

    @Test
    fun `addItem stores normalized name and writes a MANUAL_ADD change`() = runTest {
        val locationId = pantryId()

        val id = repository.addItem(
            ItemDraft(name = "  Black Beans ", category = Category.CANNED, quantity = 3.0, unit = "can", locationId = locationId),
        )

        val item = db.inventoryItemDao().getById(id)!!
        assertEquals("Black Beans", item.name)
        assertEquals("black bean", item.nameNormalized)

        val changes = db.inventoryChangeDao().getForItem(id)
        assertEquals(1, changes.size)
        assertEquals(ChangeReason.MANUAL_ADD, changes.single().reason)
        assertEquals(3.0, changes.single().delta, 0.0)
    }

    @Test
    fun `updateItem writes a MANUAL_EDIT change with the quantity delta`() = runTest {
        val locationId = pantryId()
        val id = repository.addItem(
            ItemDraft(name = "Rice", quantity = 2.0, unit = "kg", locationId = locationId),
        )

        repository.updateItem(
            id,
            ItemDraft(name = "Rice", quantity = 0.5, unit = "kg", locationId = locationId),
        )

        val changes = db.inventoryChangeDao().getForItem(id)
        assertEquals(2, changes.size)
        val edit = changes.first { it.reason == ChangeReason.MANUAL_EDIT }
        assertEquals(-1.5, edit.delta, 1e-9)
    }

    @Test
    fun `deleteItem removes the item but keeps its history`() = runTest {
        val locationId = pantryId()
        val id = repository.addItem(
            ItemDraft(name = "Milk", quantity = 1.0, unit = "l", locationId = locationId),
        )

        repository.deleteItem(id)

        assertEquals(0, db.inventoryItemDao().observeAll().first().size)
        val changes = db.inventoryChangeDao().getForItem(id)
        assertEquals(2, changes.size)
        val deletion = changes.first { it.reason == ChangeReason.MANUAL_DELETE }
        assertEquals(-1.0, deletion.delta, 0.0)
        assertEquals("Milk", deletion.itemName)
    }

    @Test
    fun `setStaple writes a zero-delta change and is a no-op when unchanged`() = runTest {
        val locationId = pantryId()
        val id = repository.addItem(ItemDraft(name = "Olive oil", locationId = locationId))

        repository.setStaple(id, true)
        repository.setStaple(id, true) // unchanged -> no extra row

        assertTrue(db.inventoryItemDao().getById(id)!!.isStaple)
        val changes = db.inventoryChangeDao().getForItem(id)
        assertEquals(2, changes.size) // add + one staple toggle
        assertEquals(0.0, changes.first { it.reason == ChangeReason.MANUAL_EDIT }.delta, 0.0)
    }

    @Test
    fun `observeSections groups items under their locations`() = runTest {
        repository.seedDefaultLocationsIfEmpty()
        val locations = repository.observeLocations().first()
        val pantry = locations.first { it.name == "Pantry" }
        val fridge = locations.first { it.name == "Fridge" }
        repository.addItem(ItemDraft(name = "Rice", locationId = pantry.id))
        repository.addItem(ItemDraft(name = "Milk", locationId = fridge.id))

        val sections = repository.observeSections().first()

        assertEquals(4, sections.size) // all seeded locations, empty ones included
        assertEquals(listOf("Rice"), sections.first { it.location.id == pantry.id }.items.map { it.name })
        assertEquals(listOf("Milk"), sections.first { it.location.id == fridge.id }.items.map { it.name })
    }

    @Test
    fun `search finds items through fts from raw input`() = runTest {
        val locationId = pantryId()
        repository.addItem(ItemDraft(name = "Tomatoes", locationId = locationId))
        repository.addItem(ItemDraft(name = "Black beans", locationId = locationId))

        assertEquals(listOf("Tomatoes"), repository.search("TOMA").first().map { it.name })
        assertEquals(0, repository.search("  ").first().size)
    }
}
