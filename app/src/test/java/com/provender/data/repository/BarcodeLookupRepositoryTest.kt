package com.provender.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.provender.data.ProvenderDatabase
import com.provender.network.BarcodeProduct
import com.provender.network.BarcodeProductSource
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
class BarcodeLookupRepositoryTest {

    private class FakeSource : BarcodeProductSource {
        val products = mutableMapOf<String, BarcodeProduct>()
        var fetchCount = 0
        override suspend fun fetch(barcode: String): BarcodeProduct? {
            fetchCount++
            return products[barcode]
        }
    }

    private lateinit var db: ProvenderDatabase
    private lateinit var source: FakeSource
    private lateinit var repository: DefaultBarcodeLookupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProvenderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = FakeSource()
        repository = DefaultBarcodeLookupRepository(db.barcodeCacheDao(), source)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `network hit is cached and served locally afterwards`() = runTest {
        source.products["123"] = BarcodeProduct(name = "Black beans", brand = "Acme")

        val first = repository.lookup("123")
        val second = repository.lookup("123")

        assertEquals("Black beans", first!!.name)
        assertEquals("Black beans", second!!.name)
        assertEquals("Acme", second.brand)
        assertEquals(1, source.fetchCount) // second lookup never touched the network
    }

    @Test
    fun `unknown barcode returns null and is not cached`() = runTest {
        assertNull(repository.lookup("999"))
        assertNull(repository.lookup("999"))

        // Not cached, so each attempt retries the network (it may come online later).
        assertEquals(2, source.fetchCount)
        assertNull(db.barcodeCacheDao().get("999"))
    }

    @Test
    fun `cache survives across repository instances`() = runTest {
        source.products["555"] = BarcodeProduct(name = "Oat milk")
        repository.lookup("555")

        val freshRepository = DefaultBarcodeLookupRepository(db.barcodeCacheDao(), FakeSource())

        assertEquals("Oat milk", freshRepository.lookup("555")!!.name)
    }
}
