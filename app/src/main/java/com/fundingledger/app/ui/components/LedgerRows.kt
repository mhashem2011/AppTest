package com.fundingledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fundingledger.app.ui.formatEditable
import com.fundingledger.app.ui.formatPercent
import com.fundingledger.app.ui.formatSar
import com.fundingledger.app.ui.theme.DerivedValueText
import com.fundingledger.app.ui.theme.GreenRow
import com.fundingledger.app.ui.theme.RedRow
import com.fundingledger.app.ui.theme.StatusRedText
import com.fundingledger.app.ui.theme.TransferYellow
import com.fundingledger.logic.Category
import com.fundingledger.logic.Mode
import com.fundingledger.logic.RowComputed

private val RowHeight = 12.dp

@Composable
private fun LedgerRowContainer(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = RowHeight),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun TableHeaderRow() {
    LedgerRowContainer(background = Color.Transparent) {
        Text("Funding Stream", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.SemiBold)
        Text(
            "Amount (SAR)",
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
        Text(
            "% Contribution",
            modifier = Modifier.weight(0.9f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun LedgerDataRow(
    rowComputed: RowComputed,
    onAmountCommit: (Double) -> Unit,
    onGramsCommit: (Double) -> Unit
) {
    val row = rowComputed.row
    val background = when (row.category) {
        Category.GREEN -> GreenRow
        Category.RED -> RedRow
        Category.NONE -> Color.Transparent
    }
    val isDerived = row.mode == Mode.GOLD || row.mode == Mode.PLUG
    val amountColor = when {
        row.mode == Mode.PLUG && rowComputed.amount < 0 -> StatusRedText
        isDerived -> DerivedValueText
        else -> Color.Unspecified
    }

    LedgerRowContainer(background = background) {
        Text(row.label, modifier = Modifier.weight(1.6f))

        when (row.mode) {
            Mode.FIXED -> TapToEditCell(
                displayText = formatSar(rowComputed.amount),
                editableSeed = formatEditable(rowComputed.amount),
                editable = true,
                modifier = Modifier.weight(1f),
                onCommit = onAmountCommit
            )
            Mode.GOLD -> TapToEditCell(
                displayText = formatSar(rowComputed.amount),
                editableSeed = formatEditable(row.grams ?: 0.0),
                editable = true,
                color = amountColor,
                modifier = Modifier.weight(1f),
                onCommit = onGramsCommit
            )
            Mode.PLUG -> Text(
                text = formatSar(rowComputed.amount),
                color = amountColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "${formatPercent(rowComputed.percent)}%",
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SubtotalRow(label: String, amount: Double, background: Color) {
    LedgerRowContainer(background = background) {
        Text(label, modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Bold)
        Text(
            formatSar(amount),
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
        Text("", modifier = Modifier.weight(0.9f))
    }
}

@Composable
fun FundingGapRow(gap: Double) {
    val color = if (kotlin.math.abs(gap) < 0.005) DerivedValueText else StatusRedText
    LedgerRowContainer(background = Color.Transparent) {
        Text("Funding Gap", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Medium)
        Text(
            formatSar(gap),
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.End
        )
        Text("", modifier = Modifier.weight(0.9f))
    }
}

@Composable
fun TransferRow(total: Double) {
    LedgerRowContainer(background = TransferYellow) {
        Text("⇄ Transfer KSA → SY", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Bold)
        Text(
            formatSar(total),
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
        Text("—", modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
    }
}
