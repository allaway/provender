package com.provender.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a real-shaped OFF payload`() {
        val payload = """
            {"code":"3017620422003","status":1,"status_verbose":"product found",
             "product":{"product_name":"Nutella","brands":"Ferrero",
                        "categories_tags":["en:breakfasts","en:spreads"]}}
        """.trimIndent()

        val product = json.decodeFromString<OffResponse>(payload).toBarcodeProduct()!!

        assertEquals("Nutella", product.name)
        assertEquals("Ferrero", product.brand)
        assertEquals("breakfasts", product.category)
    }

    @Test
    fun `status 0 or missing name maps to null`() {
        assertNull(json.decodeFromString<OffResponse>("""{"status":0}""").toBarcodeProduct())
        assertNull(
            json.decodeFromString<OffResponse>(
                """{"status":1,"product":{"product_name":""}}""",
            ).toBarcodeProduct(),
        )
    }

    @Test
    fun `blank brand becomes null`() {
        val response = OffResponse(
            status = 1,
            product = OffProduct(productName = "Salt", brands = " "),
        )

        val product = response.toBarcodeProduct()!!

        assertEquals("Salt", product.name)
        assertNull(product.brand)
        assertNull(product.category)
    }
}
