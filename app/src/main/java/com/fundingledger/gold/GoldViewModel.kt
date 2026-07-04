package com.fundingledger.gold

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the latest gold snapshot for the dashboard page. Paints the persisted
 * reading instantly on open, then refreshes if it is stale — the same behavior
 * the standalone GoldMonitor app had.
 */
class GoldViewModel(private val appContext: Context) : ViewModel() {

    private val _snapshot = MutableStateFlow(GoldRepository.lastSnapshot(appContext))
    val snapshot: StateFlow<GoldRepository.Snapshot?> = _snapshot.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        val last = _snapshot.value
        if (last == null || System.currentTimeMillis() - last.time > GoldConfig.STALE_MS) {
            refresh()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GoldRepository.refresh(appContext) }
            when (result) {
                is GoldRepository.Result.Success -> {
                    _snapshot.value = result.snapshot
                    _failed.value = false
                }
                is GoldRepository.Result.Failure -> {
                    GoldNotifier.showUnavailable(appContext)
                    // Keep showing the last good reading; just flag the failure.
                    _failed.value = true
                }
            }
            _refreshing.value = false
        }
    }

    companion object {
        fun factory(appContext: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                GoldViewModel(appContext) as T
        }
    }
}
