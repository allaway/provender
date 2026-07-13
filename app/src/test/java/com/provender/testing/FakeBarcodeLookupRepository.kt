package com.provender.testing

import com.provender.data.repository.BarcodeLookupRepository
import com.provender.network.BarcodeProduct

class FakeBarcodeLookupRepository : BarcodeLookupRepository {

    val products = mutableMapOf<String, BarcodeProduct>()
    val lookups = mutableListOf<String>()

    override suspend fun lookup(barcode: String): BarcodeProduct? {
        lookups += barcode
        return products[barcode]
    }
}
