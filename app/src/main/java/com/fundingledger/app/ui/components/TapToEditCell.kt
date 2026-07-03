package com.fundingledger.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fundingledger.app.ui.parseEditable

/**
 * A cell that displays [displayText] and, when tapped (and [editable]), swaps to a numeric
 * TextField seeded with [editableSeed]. Commits on IME "done" or on losing focus.
 */
@Composable
fun TapToEditCell(
    displayText: String,
    editableSeed: String,
    editable: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    bold: Boolean = false,
    onCommit: (Double) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(editing) { mutableStateOf(editableSeed) }

    fun commit() {
        parseEditable(text)?.let(onCommit)
    }

    if (editing) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                commit()
                editing = false
            }),
            modifier = modifier
                .width(130.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && editing) {
                        commit()
                        editing = false
                    }
                }
        )
    } else {
        Text(
            text = displayText,
            color = if (color == Color.Unspecified) LocalContentColor.current else color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = modifier
                .then(if (editable) Modifier.clickable { editing = true } else Modifier)
        )
    }
}
