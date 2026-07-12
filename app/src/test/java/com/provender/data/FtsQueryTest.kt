package com.provender.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `builds prefix query per token`() {
        assertEquals("toma*", FtsQuery.fromUserInput("toma"))
        assertEquals("black* bean*", FtsQuery.fromUserInput("black bean"))
    }

    @Test
    fun `strips match syntax from user input`() {
        assertEquals("tomato*", FtsQuery.fromUserInput("\"tom*ato\""))
    }

    @Test
    fun `lowercases tokens so they cannot form FTS operators`() {
        assertEquals("beans* or*", FtsQuery.fromUserInput("Beans OR"))
    }

    @Test
    fun `blank or symbol-only input yields null`() {
        assertNull(FtsQuery.fromUserInput(""))
        assertNull(FtsQuery.fromUserInput("   "))
        assertNull(FtsQuery.fromUserInput("*\"-"))
    }
}
