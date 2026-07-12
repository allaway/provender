package com.provender.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizerTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("black bean", NameNormalizer.normalize("  Black   Beans "))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("olive oil", NameNormalizer.normalize("olive\t\n oil"))
    }

    @Test
    fun `strips punctuation but keeps digits and hyphens`() {
        assertEquals("2 milk", NameNormalizer.normalize("2% Milk!"))
        assertEquals("gluten-free pasta", NameNormalizer.normalize("Gluten-Free Pasta"))
    }

    @Test
    fun `drops apostrophes instead of splitting on them`() {
        assertEquals("trader joe salsa", NameNormalizer.normalize("Trader Joe's Salsa"))
    }

    @Test
    fun `singularizes plain s plurals`() {
        assertEquals("bean", NameNormalizer.singularize("beans"))
        assertEquals("carrot", NameNormalizer.singularize("carrots"))
    }

    @Test
    fun `singularizes ies plurals`() {
        assertEquals("berry", NameNormalizer.singularize("berries"))
        assertEquals("cherry", NameNormalizer.singularize("cherries"))
    }

    @Test
    fun `singularizes oes plurals`() {
        assertEquals("tomato", NameNormalizer.singularize("tomatoes"))
        assertEquals("potato", NameNormalizer.singularize("potatoes"))
    }

    @Test
    fun `singularizes es plurals after sibilants`() {
        assertEquals("box", NameNormalizer.singularize("boxes"))
        assertEquals("dish", NameNormalizer.singularize("dishes"))
        assertEquals("peach", NameNormalizer.singularize("peaches"))
        assertEquals("glass", NameNormalizer.singularize("glasses"))
    }

    @Test
    fun `leaves ss words and short words alone`() {
        assertEquals("glass", NameNormalizer.singularize("glass"))
        assertEquals("gas", NameNormalizer.singularize("gas")) // <= 3 chars, untouched
        assertEquals("pea", NameNormalizer.singularize("peas"))
    }

    @Test
    fun `respects exception list`() {
        assertEquals("hummus", NameNormalizer.normalize("Hummus"))
        assertEquals("couscous", NameNormalizer.normalize("Couscous"))
        assertEquals("oats", NameNormalizer.normalize("OATS"))
    }

    @Test
    fun `empty and symbol-only input becomes empty`() {
        assertEquals("", NameNormalizer.normalize("   "))
        assertEquals("", NameNormalizer.normalize("!!!"))
    }
}
