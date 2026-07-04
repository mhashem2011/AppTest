package com.fundingledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.fundingledger.data.LedgerRepository
import com.fundingledger.domain.DerivedLedger
import com.fundingledger.domain.LedgerCalculator
import com.fundingledger.model.Category
import com.fundingledger.model.Ledger
import com.fundingledger.model.Mode
import com.fundingledger.model.Row
import com.fundingledger.model.SeedData
import com.fundingledger.model.TransferEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _ledger = MutableStateFlow(SeedData.ledger())
    val ledger: StateFlow<Ledger> = _ledger.asStateFlow()

    /** Derived values recompute automatically whenever the ledger changes. */
    val derived: StateFlow<DerivedLedger> = _ledger
        .map(LedgerCalculator::derive)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerCalculator.derive(_ledger.value))

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            _ledger.value = repository.load()
            _loaded.value = true
        }
    }

    private fun update(transform: (Ledger) -> Ledger) {
        val next = transform(_ledger.value)
        _ledger.value = next
        viewModelScope.launch { repository.save(next) }
    }

    private fun updateRow(id: String, transform: (Row) -> Row) =
        update { it.copy(rows = it.rows.map { row -> if (row.id == id) transform(row) else row }) }

    fun setTarget(value: Double) = update { it.copy(target = value) }

    /** Editing xauUsd drops any manual price override so the formula drives again. */
    fun setXauUsd(value: Double) = update { it.copy(xauUsd = value, pricePerGramOverride = null) }

    /**
     * Live-rate feed from the Gold page: push the fetched USD/oz into the ledger so
     * SAR/g, GOLD rows, and the plug all recompute. Clears any manual override
     * (live data wins). No-op when the value is unchanged, so recompositions and
     * repeated snapshots don't cause redundant disk writes.
     */
    fun syncGoldPrice(ozUsd: Double) {
        val current = _ledger.value
        if (current.xauUsd == ozUsd && current.pricePerGramOverride == null) return
        update { it.copy(xauUsd = ozUsd, pricePerGramOverride = null) }
    }

    fun setPricePerGram(value: Double) = update { it.copy(pricePerGramOverride = value) }

    fun setRowAmount(id: String, amount: Double) = updateRow(id) { it.copy(amount = amount) }

    fun setRowGrams(id: String, grams: Double) = updateRow(id) { it.copy(grams = grams) }

    fun setRowLabel(id: String, label: String) = updateRow(id) { it.copy(label = label) }

    fun setRowCategory(id: String, category: Category) = updateRow(id) { it.copy(category = category) }

    fun setRowInTransfer(id: String, inTransfer: Boolean) = updateRow(id) { it.copy(inTransfer = inTransfer) }

    /** Demote any current plug to FIXED (frozen at its last computed amount) and promote [id]. */
    fun setPlugRow(id: String) = update { ledger ->
        val currentPlugAmount = LedgerCalculator.derive(ledger)
            .rows.firstOrNull { it.row.mode == Mode.PLUG }?.amount ?: 0.0
        ledger.copy(rows = ledger.rows.map { row ->
            when {
                row.id == id -> row.copy(mode = Mode.PLUG)
                row.mode == Mode.PLUG -> row.copy(mode = Mode.FIXED, amount = currentPlugAmount)
                else -> row
            }
        })
    }

    /** Switch a row between FIXED and GOLD (PLUG is assigned via [setPlugRow]). */
    fun setRowMode(id: String, mode: Mode) = updateRow(id) { row ->
        when (mode) {
            Mode.GOLD -> row.copy(mode = Mode.GOLD, grams = row.grams ?: 0.0)
            else -> row.copy(mode = Mode.FIXED)
        }
    }

    fun addRow(label: String, category: Category, mode: Mode, inTransfer: Boolean) = update { ledger ->
        val row = Row(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "New row" },
            category = category,
            mode = if (mode == Mode.PLUG) Mode.FIXED else mode,
            grams = if (mode == Mode.GOLD) 0.0 else null,
            inTransfer = inTransfer,
        )
        ledger.copy(rows = ledger.rows + row)
    }

    fun deleteRow(id: String) = update { it.copy(rows = it.rows.filterNot { row -> row.id == id }) }

    fun addTransfer(label: String, amount: Double) = update { ledger ->
        val entry = TransferEntry(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Transfer" },
            amount = amount,
        )
        ledger.copy(transfers = ledger.transfers + entry)
    }

    private fun updateTransfer(id: String, transform: (TransferEntry) -> TransferEntry) =
        update { it.copy(transfers = it.transfers.map { t -> if (t.id == id) transform(t) else t }) }

    fun setTransferAmount(id: String, amount: Double) = updateTransfer(id) { it.copy(amount = amount) }

    fun setTransferLabel(id: String, label: String) = updateTransfer(id) { it.copy(label = label) }

    fun deleteTransfer(id: String) =
        update { it.copy(transfers = it.transfers.filterNot { t -> t.id == id }) }

    fun moveRow(id: String, up: Boolean) = update { ledger ->
        val rows = ledger.rows.toMutableList()
        val index = rows.indexOfFirst { it.id == id }
        val swapWith = if (up) index - 1 else index + 1
        if (index == -1 || swapWith !in rows.indices) return@update ledger
        rows[index] = rows[swapWith].also { rows[swapWith] = rows[index] }
        ledger.copy(rows = rows)
    }

    fun exportJson(): String = repository.exportJson(_ledger.value)

    companion object {
        fun factory(repository: LedgerRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                LedgerViewModel(repository) as T
        }
    }
}
