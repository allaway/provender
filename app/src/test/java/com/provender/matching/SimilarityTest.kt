package com.provender.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityTest {

    @Test
    fun `jaro-winkler matches published reference values`() {
        assertEquals(0.9611, Similarity.jaroWinkler("martha", "marhta"), 0.001)
        assertEquals(0.8400, Similarity.jaroWinkler("dwayne", "duane"), 0.001)
        assertEquals(0.8133, Similarity.jaroWinkler("dixon", "dicksonx"), 0.001)
    }

    @Test
    fun `identical and empty strings are handled`() {
        assertEquals(1.0, Similarity.jaroWinkler("beans", "beans"), 0.0)
        assertEquals(0.0, Similarity.jaroWinkler("", "beans"), 0.0)
        assertEquals(1.0, Similarity.similarity("", ""), 0.0) // equal strings short-circuit
    }

    @Test
    fun `token reordering scores as a match`() {
        assertTrue(Similarity.similarity("black bean", "bean black") >= Similarity.DEFAULT_THRESHOLD)
    }

    @Test
    fun `brand-prefixed names match via token subset`() {
        assertTrue(
            Similarity.similarity("trader joe black bean", "black bean") >=
                Similarity.DEFAULT_THRESHOLD,
        )
    }

    @Test
    fun `typos survive via jaro-winkler`() {
        assertTrue(Similarity.similarity("black bean", "black baen") >= Similarity.DEFAULT_THRESHOLD)
    }

    @Test
    fun `unrelated items stay below the threshold`() {
        assertTrue(Similarity.similarity("milk", "flour") < Similarity.DEFAULT_THRESHOLD)
        assertTrue(Similarity.similarity("green bean", "black bean") < 1.0)
    }
}
