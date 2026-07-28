package com.dlovel.plankton.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputStateTest {
    @Test
    fun completionPlacesCursorAtEndOfText() {
        val value = textFieldValueAtEnd("小球藻")

        assertEquals("小球藻", value.text)
        assertEquals(value.text.length, value.selection.start)
        assertEquals(value.text.length, value.selection.end)
    }
}
