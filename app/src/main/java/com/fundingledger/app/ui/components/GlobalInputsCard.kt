package com.fundingledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fundingledger.app.ui.formatEditable
import com.fundingledger.app.ui.formatPrice
import com.fundingledger.app.ui.parseEditable

@Composable
fun GlobalInputsCard(
    target: Double,
    xauUsd: Double,
    pricePerGram: Double,
    onTargetChange: (Double) -> Unit,
    onXauUsdChange: (Double) -> Unit,
    onPricePerGramOverrideChange: (Double) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabeledNumberField(
                label = "Target (SAR)",
                value = target,
                modifier = Modifier.weight(1f),
                onCommit = onTargetChange
            )
            LabeledNumberField(
                label = "XAU/USD",
                value = xauUsd,
                modifier = Modifier.weight(1f),
                onCommit = onXauUsdChange
            )
            LabeledNumberField(
                label = "SAR/gram",
                value = pricePerGram,
                modifier = Modifier.weight(1f),
                displayFormatter = ::formatPrice,
                onCommit = onPricePerGramOverrideChange
            )
        }
    }
}

@Composable
private fun LabeledNumberField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    displayFormatter: (Double) -> String = ::formatEditable,
    onCommit: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(displayFormatter(value)) }

    fun commit() {
        parseEditable(text)?.let(onCommit)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) commit()
            }
    )
}
