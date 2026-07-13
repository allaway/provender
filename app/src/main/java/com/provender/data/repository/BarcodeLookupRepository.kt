package com.provender.data.repository

import com.provender.data.dao.BarcodeCacheDao
import com.provender.data.entity.BarcodeCache
import com.provender.network.BarcodeProduct
import com.provender.network.BarcodeProductSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first barcode resolution (SPEC §2 Tier 1): cache table first, Open Food Facts only
 * on a miss, successful results cached forever.
 */
interface BarcodeLookupRepository {
    /** Null when the barcode is unknown everywhere (user falls back to manual entry). */
    suspend fun lookup(barcode: String): BarcodeProduct?
}

@Singleton
class DefaultBarcodeLookupRepository @Inject constructor(
    private val cacheDao: BarcodeCacheDao,
    private val remoteSource: BarcodeProductSource,
) : BarcodeLookupRepository {

    override suspend fun lookup(barcode: String): BarcodeProduct? {
        cacheDao.get(barcode)?.let {
            return BarcodeProduct(name = it.name, brand = it.brand, category = it.category)
        }
        val fetched = remoteSource.fetch(barcode) ?: return null
        cacheDao.upsert(
            BarcodeCache(
                barcode = barcode,
                name = fetched.name,
                brand = fetched.brand,
                category = fetched.category,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        return fetched
    }
}
