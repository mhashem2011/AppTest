package com.fundingledger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.fundingledger.app.ui.components.FundingGapRow
import com.fundingledger.app.ui.components.GlobalInputsCard
import com.fundingledger.app.ui.components.LedgerDataRow
import com.fundingledger.app.ui.components.SubtotalRow
import com.fundingledger.app.ui.components.SummaryFooter
import com.fundingledger.app.ui.components.TableHeaderRow
import com.fundingledger.app.ui.components.TransferRow
import com.fundingledger.app.ui.theme.GreenSubtotal
import com.fundingledger.app.ui.theme.RedSubtotal
import com.fundingledger.app.viewmodel.LedgerViewModel
import com.fundingledger.logic.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: LedgerViewModel) {
    val state by viewModel.state.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    val greenRows = snapshot.rows.filter { it.row.category == Category.GREEN }
    val redRows = snapshot.rows.filter { it.row.category == Category.RED }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Asset Balancing Ledger") }) },
        bottomBar = { SummaryFooter(target = state.target, snapshot = snapshot) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            GlobalInputsCard(
                target = state.target,
                xauUsd = state.xauUsd,
                pricePerGram = snapshot.pricePerGram,
                onTargetChange = viewModel::updateTarget,
                onXauUsdChange = viewModel::updateXauUsd,
                onPricePerGramOverrideChange = viewModel::updatePricePerGramOverride
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { TableHeaderRow() }

                items(greenRows, key = { it.row.id }) { rowComputed ->
                    LedgerDataRow(
                        rowComputed = rowComputed,
                        onAmountCommit = { amount -> viewModel.updateRowAmount(rowComputed.row.id, amount) },
                        onGramsCommit = { grams -> viewModel.updateRowGrams(rowComputed.row.id, grams) }
                    )
                }
                item { SubtotalRow("▲ Green Subtotal", snapshot.greenSubtotal, GreenSubtotal) }

                items(redRows, key = { it.row.id }) { rowComputed ->
                    LedgerDataRow(
                        rowComputed = rowComputed,
                        onAmountCommit = { amount -> viewModel.updateRowAmount(rowComputed.row.id, amount) },
                        onGramsCommit = { grams -> viewModel.updateRowGrams(rowComputed.row.id, grams) }
                    )
                }
                item { FundingGapRow(snapshot.fundingGap) }
                item { SubtotalRow("▼ Red Subtotal", snapshot.redSubtotal, RedSubtotal) }

                item { TransferRow(snapshot.transferTotal) }
            }
        }
    }
}
