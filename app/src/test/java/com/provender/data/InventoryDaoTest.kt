package com.provender.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.provender.data.entity.InventoryChange
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import com.provender.data.model.ChangeReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryDaoTest {

    private lateinit var db: ProvenderDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProvenderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedLocation(id: Long = 1, name: String = "Pantry"): Long {
        db.storageLocationDao().insertAll(listOf(StorageLocation(id = id, name = name, sortOrder = 0)))
        return id
    }

    private fun item(
        name: String,
        normalized: String,
        locationId: Long,
        quantity: Double? = 1.0,
        notes: String? = null,
    ) = InventoryItem(
        name = name,
        nameNormalized = normalized,
        category = Category.OTHER,
        quantity = quantity,
        unit = "count",
        locationId = locationId,
        lastSeenAt = 1_000L,
        notes = notes,
    )

    @Test
    fun `insert and observe items ordered by normalized name`() = runTest {
        val locationId = seedLocation()
        db.inventoryItemDao().insert(item("Zucchini", "zucchini", locationId))
        db.inventoryItemDao().insert(item("Apples", "apple", locationId))

        val items = db.inventoryItemDao().observeAll().first()

        assertEquals(listOf("apple", "zucchini"), items.map { it.nameNormalized })
    }

    @Test
    fun `update and getById round-trip`() = runTest {
        val locationId = seedLocation()
        val id = db.inventoryItemDao().insert(item("Rice", "rice", locationId))

        val stored = db.inventoryItemDao().getById(id)!!
        db.inventoryItemDao().update(stored.copy(quantity = 5.0, isStaple = true))

        val updated = db.inventoryItemDao().getById(id)!!
        assertEquals(5.0, updated.quantity!!, 0.0)
        assertEquals(true, updated.isStaple)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val locationId = seedLocation()
        val id = db.inventoryItemDao().insert(item("Rice", "rice", locationId))

        db.inventoryItemDao().delete(db.inventoryItemDao().getById(id)!!)

        assertNull(db.inventoryItemDao().getById(id))
        assertEquals(0, db.inventoryItemDao().observeAll().first().size)
    }

    @Test
    fun `fts search matches name prefixes`() = runTest {
        val locationId = seedLocation()
        db.inventoryItemDao().insert(item("Tomatoes", "tomato", locationId))
        db.inventoryItemDao().insert(item("Black beans", "black bean", locationId))

        val query = FtsQuery.fromUserInput("toma")!!
        val results = db.inventoryItemDao().search(query).first()

        assertEquals(listOf("Tomatoes"), results.map { it.name })
    }

    @Test
    fun `fts search matches notes and stays in sync after update`() = runTest {
        val locationId = seedLocation()
        val id = db.inventoryItemDao().insert(
            item("Salsa", "salsa", locationId, notes = "half full"),
        )

        assertEquals(1, db.inventoryItemDao().search(FtsQuery.fromUserInput("half")!!).first().size)

        db.inventoryItemDao().update(db.inventoryItemDao().getById(id)!!.copy(notes = "unopened"))

        assertEquals(0, db.inventoryItemDao().search(FtsQuery.fromUserInput("half")!!).first().size)
        assertEquals(1, db.inventoryItemDao().search(FtsQuery.fromUserInput("unopened")!!).first().size)
    }

    @Test
    fun `locations observed in sort order`() = runTest {
        db.storageLocationDao().insertAll(
            listOf(
                StorageLocation(name = "Spices", sortOrder = 3),
                StorageLocation(name = "Pantry", sortOrder = 0),
                StorageLocation(name = "Fridge", sortOrder = 1),
            ),
        )

        val names = db.storageLocationDao().observeAll().first().map { it.name }

        assertEquals(listOf("Pantry", "Fridge", "Spices"), names)
    }

    @Test
    fun `change log returns newest first`() = runTest {
        val locationId = seedLocation()
        val id = db.inventoryItemDao().insert(item("Rice", "rice", locationId))
        val dao = db.inventoryChangeDao()
        dao.insert(
            InventoryChange(
                itemId = id, itemName = "Rice", delta = 1.0,
                reason = ChangeReason.MANUAL_ADD, createdAt = 100L,
            ),
        )
        dao.insert(
            InventoryChange(
                itemId = id, itemName = "Rice", delta = 2.0,
                reason = ChangeReason.MANUAL_EDIT, createdAt = 200L,
            ),
        )

        val recent = dao.observeRecent(limit = 10).first()

        assertEquals(listOf(200L, 100L), recent.map { it.createdAt })
        assertEquals(ChangeReason.MANUAL_EDIT, recent.first().reason)
    }
}
