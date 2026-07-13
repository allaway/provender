package com.provender.network

import com.provender.di.OpenFoodFacts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** A product resolved from a barcode, regardless of where it came from. */
data class BarcodeProduct(
    val name: String,
    val brand: String? = null,
    val category: String? = null,
)

/** Remote source for barcode lookups; behind an interface so tests use a fake. */
interface BarcodeProductSource {
    /** Returns null when the barcode is unknown or the network is unavailable. */
    suspend fun fetch(barcode: String): BarcodeProduct?
}

@Serializable
data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String> = emptyList(),
)

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,brands,categories_tags",
    ): OffResponse
}

@Singleton
class OpenFoodFactsSource @Inject constructor(
    @OpenFoodFacts retrofit: Retrofit,
) : BarcodeProductSource {

    private val api = retrofit.create(OpenFoodFactsApi::class.java)

    override suspend fun fetch(barcode: String): BarcodeProduct? =
        runCatching { api.product(barcode).toBarcodeProduct() }.getOrNull()
}

/** Maps [OffResponse] to a [BarcodeProduct]; pure and unit-testable. */
fun OffResponse.toBarcodeProduct(): BarcodeProduct? {
    val name = product?.productName?.takeIf { it.isNotBlank() } ?: return null
    if (status != 1) return null
    return BarcodeProduct(
        name = name,
        brand = product.brands?.takeIf { it.isNotBlank() },
        category = product.categoriesTags.firstOrNull()
            ?.removePrefix("en:")?.replace('-', ' '),
    )
}
