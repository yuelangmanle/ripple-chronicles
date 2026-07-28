package com.dlovel.plankton.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Creates a completed value with its cursor placed after the final character. */
fun textFieldValueAtEnd(text: String): TextFieldValue = TextFieldValue(
    text = text,
    selection = TextRange(text.length)
)

/** Keeps a user's active cursor when state only echoes the same text back. */
fun synchronizeTextFieldValue(current: TextFieldValue, externalText: String): TextFieldValue =
    if (current.text == externalText) current else textFieldValueAtEnd(externalText)
