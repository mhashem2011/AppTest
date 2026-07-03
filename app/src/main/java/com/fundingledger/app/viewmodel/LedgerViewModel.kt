package com.fundingledger.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fundingledger.app.data.LedgerRepository
import com.fundingledger.logic.LedgerCalculator
import com.fundingledger.logic.LedgerSnapshot
import com.fundingledger.logic.LedgerState
import com.fundingledger.logic.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(repository.load() ?: LedgerState.seed())
    val state: StateFlow<LedgerState> = _state.asStateFlow()

    // Every derived figure in the UI comes from this single computed stream - editing
    // any field in `_state` republishes it and the whole ledger recalculates.
    val snapshot: StateFlow<LedgerSnapshot> = _state
        .map { LedgerCalculator.snapshot(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LedgerCalculator.snapshot(_state.value)
        )

    init {
        viewModelScope.launch {
            _state.drop(1).collect { repository.save(it) }
        }
    }

    fun updateTarget(target: Double) {
        _state.value = _state.value.copy(target = target)
    }

    fun updateXauUsd(xauUsd: Double) {
        _state.value = _state.value.copy(xauUsd = xauUsd, pricePerGramOverride = null)
    }

    fun updatePricePerGramOverride(value: Double) {
        _state.value = _state.value.copy(pricePerGramOverride = value)
    }

    fun updateRowAmount(id: String, amount: Double) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { row ->
                if (row.id == id && row.mode == Mode.FIXED) row.copy(amount = amount) else row
            }
        )
    }

    fun updateRowGrams(id: String, grams: Double) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { row ->
                if (row.id == id && row.mode == Mode.GOLD) row.copy(grams = grams) else row
            }
        )
    }
}

class LedgerViewModelFactory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LedgerViewModel(repository) as T
    }
}
