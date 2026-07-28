package com.dlovel.plankton.util

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class TextInputStateTest {
    @Test
    fun completionPlacesCursorAtEndOfText() {
        val value = textFieldValueAtEnd("小球藻")

        assertEquals("小球藻", value.text)
        assertEquals(value.text.length, value.selection.start)
        assertEquals(value.text.length, value.selection.end)
    }

    @Test
    fun echoingTheSameTextDoesNotResetTheActiveCursor() {
        val editing = TextFieldValue("小球", selection = TextRange(1))

        val synchronized = synchronizeTextFieldValue(editing, "小球")

        assertEquals(TextRange(1), synchronized.selection)
    }

    @Test
    fun externalReplacementMovesCursorToNewTextEnd() {
        val synchronized = synchronizeTextFieldValue(TextFieldValue("小球"), "小球藻")

        assertEquals(TextRange(3), synchronized.selection)
    }
}
