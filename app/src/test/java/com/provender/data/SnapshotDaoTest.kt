package com.provender.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.entity.StorageLocation
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
class SnapshotDaoTest {

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

    private suspend fun seedLocation(): Long {
        db.storageLocationDao().insertAll(listOf(StorageLocation(id = 1, name = "Pantry", sortOrder = 0)))
        return 1L
    }

    @Test
    fun `photoUris list round-trips through the converter`() = runTest {
        val locationId = seedLocation()
        val uris = listOf("file:///a/1.jpg", "file:///a/2.jpg")

        val id = db.snapshotDao().insert(
            Snapshot(
                locationId = locationId,
                createdAt = 5L,
                photoUris = uris,
                status = SnapshotStatus.ANALYZING,
            ),
        )

        assertEquals(uris, db.snapshotDao().getById(id)!!.photoUris)
    }

    @Test
    fun `status updates are observable`() = runTest {
        val locationId = seedLocation()
        val id = db.snapshotDao().insert(
            Snapshot(
                locationId = locationId,
                createdAt = 5L,
                photoUris = emptyList(),
                status = SnapshotStatus.ANALYZING,
            ),
        )

        val stored = db.snapshotDao().getById(id)!!
        db.snapshotDao().update(
            stored.copy(status = SnapshotStatus.READY, extractionJson = "[]"),
        )

        val observed = db.snapshotDao().observeById(id).first()!!
        assertEquals(SnapshotStatus.READY, observed.status)
        assertEquals("[]", observed.extractionJson)
    }

    @Test
    fun `missing snapshot observes as null`() = runTest {
        assertNull(db.snapshotDao().observeById(999L).first())
    }

    @Test
    fun `committed count only counts committed snapshots for the location`() = runTest {
        val locationId = seedLocation()
        val dao = db.snapshotDao()
        dao.insert(Snapshot(locationId = locationId, createdAt = 1L, photoUris = emptyList(), status = SnapshotStatus.COMMITTED))
        dao.insert(Snapshot(locationId = locationId, createdAt = 2L, photoUris = emptyList(), status = SnapshotStatus.ANALYZING))

        assertEquals(1, dao.committedCountForLocation(locationId))
    }
}
