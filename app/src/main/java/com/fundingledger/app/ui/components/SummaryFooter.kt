package com.fundingledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fundingledger.app.ui.formatPercent
import com.fundingledger.app.ui.formatSar
import com.fundingledger.app.ui.theme.StatusGreenText
import com.fundingledger.app.ui.theme.StatusRedText
import com.fundingledger.logic.LedgerSnapshot
import com.fundingledger.logic.Mode

@Composable
fun SummaryFooter(target: Double, snapshot: LedgerSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryColumn(label = "Target Amount", value = formatSar(target))
            SummaryColumn(label = "Current Total", value = formatSar(snapshot.grandTotal))
            StatusColumn(snapshot)
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusColumn(snapshot: LedgerSnapshot) {
    val fullySourced = snapshot.isBalanced && !snapshot.isOverfunded
    Column(horizontalAlignment = Alignment.End) {
        Text("Status", style = MaterialTheme.typography.labelMedium)
        when {
            fullySourced -> Text(
                "100% Fully Sourced",
                color = StatusGreenText,
                fontWeight = FontWeight.Bold
            )
            snapshot.isOverfunded -> Text(
                "Over-funded by ${formatSar(-snapshot.rows.first { it.row.mode == Mode.PLUG }.amount)}",
                color = StatusRedText,
                fontWeight = FontWeight.Bold
            )
            else -> Text(
                "${formatPercent(snapshot.percentSourced)}% sourced, gap ${formatSar(snapshot.fundingGap)}",
                color = StatusRedText,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
