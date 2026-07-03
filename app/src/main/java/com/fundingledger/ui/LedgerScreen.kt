package com.fundingledger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fundingledger.domain.ComputedRow
import com.fundingledger.domain.DerivedLedger
import com.fundingledger.model.Category
import com.fundingledger.model.Ledger
import com.fundingledger.model.Mode
import com.fundingledger.model.Row as LedgerRowModel
import com.fundingledger.ui.theme.LedgerColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val amountFormat: NumberFormat =
    NumberFormat.getIntegerInstance(Locale.US).apply { isGroupingUsed = true }

fun formatAmount(value: Double): String = amountFormat.format(value.roundToLong())
fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)
fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)

/** Raw text used to pre-fill a field when editing begins. */
private fun editText(value: Double): String =
    if (value == value.roundToLong().toDouble()) value.roundToLong().toString()
    else String.format(Locale.US, "%.2f", value)

private const val AMOUNT_COL_DP = 108
private const val PERCENT_COL_DP = 64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: LedgerViewModel, onShareJson: (String) -> Unit) {
    val ledger by viewModel.ledger.collectAsState()
    val derived by viewModel.derived.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    var optionsFor by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asset Balancing Ledger", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add row")
                    }
                    IconButton(onClick = { onShareJson(viewModel.exportJson()) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export JSON")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            item { GlobalsCard(ledger, derived, viewModel) }
            item { Spacer(Modifier.height(12.dp)) }
            item { TableHeader() }

            val green = derived.rows.filter { it.row.category == Category.GREEN }
            val red = derived.rows.filter { it.row.category == Category.RED }
            val other = derived.rows.filter { it.row.category == Category.NONE }

            items(green.size, key = { green[it].row.id }) { i ->
                LedgerRow(green[i], LedgerColors.GreenRow, viewModel) { optionsFor = green[i].row.id }
            }
            item {
                BandRow("▲ Green Subtotal", derived.greenSubtotal, derived.greenSubtotal / ledger.target * 100, LedgerColors.GreenBand)
            }
            items(red.size, key = { red[it].row.id }) { i ->
                LedgerRow(red[i], LedgerColors.RedRow, viewModel) { optionsFor = red[i].row.id }
            }
            item {
                BandRow("Funding Gap", derived.fundingGap, derived.fundingGap / ledger.target * 100, LedgerColors.RedRow, bold = false)
            }
            item {
                BandRow("▼ Red Subtotal", derived.redSubtotal, derived.redSubtotal / ledger.target * 100, LedgerColors.RedBand)
            }
            items(other.size, key = { other[it].row.id }) { i ->
                LedgerRow(other[i], MaterialTheme.colorScheme.surface, viewModel) { optionsFor = other[i].row.id }
            }
            item {
                BandRow("⇄ Transfer KSA → SY", derived.transferKsaToSy, null, LedgerColors.TransferYellow)
            }
            item { Spacer(Modifier.height(12.dp)) }
            item { SummaryCard(ledger, derived) }
        }
    }

    optionsFor?.let { id ->
        ledger.rows.firstOrNull { it.id == id }?.let { row ->
            RowOptionsDialog(row = row, viewModel = viewModel, onDismiss = { optionsFor = null })
        } ?: run { optionsFor = null }
    }
    if (showAddDialog) {
        AddRowDialog(viewModel = viewModel, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun GlobalsCard(ledger: Ledger, derived: DerivedLedger, viewModel: LedgerViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlobalField(
                label = "Target (SAR)",
                value = ledger.target,
                format = ::formatAmount,
                modifier = Modifier.weight(1.2f),
                onCommit = viewModel::setTarget,
            )
            GlobalField(
                label = "XAU/USD (oz)",
                value = ledger.xauUsd,
                format = ::format2,
                modifier = Modifier.weight(1f),
                onCommit = viewModel::setXauUsd,
            )
            GlobalField(
                label = "Gold SAR/g" + if (ledger.pricePerGramOverride != null) " (manual)" else "",
                value = derived.pricePerGram,
                format = ::format2,
                modifier = Modifier.weight(1f),
                onCommit = viewModel::setPricePerGram,
            )
        }
    }
}

@Composable
private fun GlobalField(
    label: String,
    value: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    onCommit: (Double) -> Unit,
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EditableNumber(
            value = value,
            format = format,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LedgerColors.Ink),
            onCommit = onCommit,
        )
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().background(LedgerColors.HeaderGray).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Funding Stream", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            "Amount (SAR)", Modifier.width(AMOUNT_COL_DP.dp),
            fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End,
        )
        Text(
            "% Contribution", Modifier.width(PERCENT_COL_DP.dp),
            fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End,
        )
    }
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerRow(
    computed: ComputedRow,
    background: Color,
    viewModel: LedgerViewModel,
    onLongPress: () -> Unit,
) {
    val row = computed.row
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, fontSize = 14.sp, color = LedgerColors.Ink)
                if (row.mode == Mode.PLUG) {
                    Text("  PLUG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LedgerColors.NegativeRed)
                }
            }
            if (row.mode == Mode.GOLD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EditableNumber(
                        value = row.grams ?: 0.0,
                        format = { String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') },
                        textStyle = TextStyle(fontSize = 12.sp, color = LedgerColors.Ink, fontWeight = FontWeight.Medium),
                        onCommit = { viewModel.setRowGrams(row.id, it) },
                    )
                    Text(" g  × SAR/g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.width(AMOUNT_COL_DP.dp), contentAlignment = Alignment.CenterEnd) {
            if (row.mode == Mode.FIXED) {
                EditableNumber(
                    value = computed.amount,
                    format = ::formatAmount,
                    textStyle = TextStyle(fontSize = 14.sp, color = LedgerColors.Ink, textAlign = TextAlign.End),
                    onCommit = { viewModel.setRowAmount(row.id, it) },
                )
            } else {
                // Derived (GOLD/PLUG): read-only and visually distinct.
                Text(
                    formatAmount(computed.amount),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = if (computed.amount < 0) LedgerColors.NegativeRed else LedgerColors.Ink.copy(alpha = 0.75f),
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            formatPercent(computed.percentOfTarget),
            Modifier.width(PERCENT_COL_DP.dp),
            fontSize = 12.sp,
            color = LedgerColors.Ink.copy(alpha = 0.8f),
            textAlign = TextAlign.End,
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.6f))
}

@Composable
private fun BandRow(label: String, amount: Double, percent: Double?, background: Color, bold: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val weight = if (bold) FontWeight.Bold else FontWeight.Medium
        Text(label, Modifier.weight(1f), fontWeight = weight, fontSize = 14.sp, color = LedgerColors.Ink)
        Text(
            formatAmount(amount), Modifier.width(AMOUNT_COL_DP.dp),
            fontWeight = weight, fontSize = 14.sp, textAlign = TextAlign.End,
            color = if (amount < 0) LedgerColors.NegativeRed else LedgerColors.Ink,
        )
        Text(
            percent?.let(::formatPercent) ?: "—",
            Modifier.width(PERCENT_COL_DP.dp),
            fontWeight = weight, fontSize = 12.sp, textAlign = TextAlign.End, color = LedgerColors.Ink,
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.6f))
}

@Composable
private fun SummaryCard(ledger: Ledger, derived: DerivedLedger) {
    val balanced = abs(derived.fundingGap) < 0.005 && derived.overFundedBy < 0.005
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryCell("Target Amount", formatAmount(ledger.target), LedgerColors.Ink)
            SummaryCell("Current Total", formatAmount(derived.grandTotal), LedgerColors.Ink)
            when {
                derived.overFundedBy >= 0.005 -> SummaryCell(
                    "Status",
                    "Over-funded by " + formatAmount(derived.overFundedBy),
                    LedgerColors.NegativeRed,
                )
                balanced -> SummaryCell("Status", "100% Fully Sourced", LedgerColors.PositiveGreen)
                else -> SummaryCell(
                    "Status",
                    "Gap " + formatAmount(derived.fundingGap) + " · " +
                        formatPercent(derived.grandTotal / ledger.target * 100) + " sourced",
                    LedgerColors.NegativeRed,
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/**
 * Shows a formatted number; tap to edit in place, commit on Done or focus loss.
 */
@Composable
fun EditableNumber(
    value: Double,
    format: (Double) -> String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    onCommit: (Double) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    if (editing) {
        var hadFocus by remember { mutableStateOf(false) }
        fun commit() {
            text.trim().replace(",", "").toDoubleOrNull()?.let(onCommit)
            editing = false
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true
                    else if (hadFocus) commit()
                }
                .width(96.dp),
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Text(
            text = format(value),
            style = textStyle,
            modifier = modifier.clickable {
                text = editText(value)
                editing = true
            },
        )
    }
}

@Composable
private fun CategoryPicker(selected: Category, onSelect: (Category) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.entries.forEach { cat ->
            FilterChip(selected = selected == cat, onClick = { onSelect(cat) }, label = { Text(cat.name) })
        }
    }
}

@Composable
private fun RowOptionsDialog(row: LedgerRowModel, viewModel: LedgerViewModel, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(row.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit row") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                Text("Category", fontSize = 12.sp)
                CategoryPicker(row.category) { viewModel.setRowCategory(row.id, it) }
                Text("Mode", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = row.mode == Mode.FIXED,
                        onClick = { viewModel.setRowMode(row.id, Mode.FIXED) },
                        label = { Text("FIXED") },
                    )
                    FilterChip(
                        selected = row.mode == Mode.GOLD,
                        onClick = { viewModel.setRowMode(row.id, Mode.GOLD) },
                        label = { Text("GOLD") },
                    )
                    FilterChip(
                        selected = row.mode == Mode.PLUG,
                        onClick = { viewModel.setPlugRow(row.id) },
                        label = { Text("PLUG") },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In KSA → SY transfer", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = row.inTransfer, onCheckedChange = { viewModel.setRowInTransfer(row.id, it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.moveRow(row.id, up = true) }) { Text("Move up") }
                    TextButton(onClick = { viewModel.moveRow(row.id, up = false) }) { Text("Move down") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.deleteRow(row.id); onDismiss() }) {
                        Text("Delete", color = LedgerColors.NegativeRed)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.setRowLabel(row.id, label); onDismiss() }) { Text("Done") }
        },
    )
}

@Composable
private fun AddRowDialog(viewModel: LedgerViewModel, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.GREEN) }
    var mode by remember { mutableStateOf(Mode.FIXED) }
    var inTransfer by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add row") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                Text("Category", fontSize = 12.sp)
                CategoryPicker(category) { category = it }
                Text("Mode", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == Mode.FIXED, onClick = { mode = Mode.FIXED }, label = { Text("FIXED") })
                    FilterChip(selected = mode == Mode.GOLD, onClick = { mode = Mode.GOLD }, label = { Text("GOLD") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In KSA → SY transfer", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = inTransfer, onCheckedChange = { inTransfer = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.addRow(label, category, mode, inTransfer); onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
