package com.provender.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmJsonTest {

    @Test
    fun `parses a clean json array`() {
        val raw = """[{"name":"black beans","quantity":3,"unit":"can","category":"canned","confidence":0.9,"notes":"unopened"}]"""

        val items = LlmJson.parseArray<ExtractedItem>(raw)!!

        assertEquals(1, items.size)
        assertEquals("black beans", items[0].name)
        assertEquals(3.0, items[0].quantity!!, 0.0)
        assertEquals(0.9f, items[0].confidence, 1e-6f)
    }

    @Test
    fun `strips code fences and surrounding prose`() {
        val raw = """
            Sure! Here is the inventory:
            ```json
            [{"name":"rolled oats","confidence":0.7}]
            ```
            Let me know if you need anything else.
        """.trimIndent()

        val items = LlmJson.parseArray<ExtractedItem>(raw)!!

        assertEquals("rolled oats", items.single().name)
        assertNull(items.single().quantity)
    }

    @Test
    fun `tolerates missing optional keys and unknown extra keys`() {
        val raw = """[{"name":"salt","brand":"Diamond","confidence":0.5,"shelf":"top"}]"""

        val items = LlmJson.parseArray<ExtractedItem>(raw)!!

        assertEquals("salt", items.single().name)
        assertNull(items.single().unit)
    }

    @Test
    fun `parses meal ideas`() {
        val raw = """
            [{"name":"Bean wrap","archetype":"wrap","ingredientsUsed":["black beans","tortilla"],
              "steps":["Warm tortilla","Fill","Roll"],"estMinutes":10,"isSweet":false}]
        """.trimIndent()

        val ideas = LlmJson.parseArray<MealIdea>(raw)!!

        assertEquals("Bean wrap", ideas.single().name)
        assertEquals(3, ideas.single().steps.size)
        assertEquals(false, ideas.single().isSweet)
    }

    @Test
    fun `returns null for garbage, prose without json, or truncated arrays`() {
        assertNull(LlmJson.parseArray<ExtractedItem>("I could not read the image, sorry."))
        assertNull(LlmJson.parseArray<ExtractedItem>(""))
        assertNull(LlmJson.parseArray<ExtractedItem>("""[{"name": "beans", """))
    }

    @Test
    fun `extractJsonArray cuts to the outermost brackets`() {
        assertEquals("[1, 2]", LlmJson.extractJsonArray("noise [1, 2] trailing"))
        assertNull(LlmJson.extractJsonArray("no brackets here"))
        assertNull(LlmJson.extractJsonArray("] backwards ["))
    }
}
