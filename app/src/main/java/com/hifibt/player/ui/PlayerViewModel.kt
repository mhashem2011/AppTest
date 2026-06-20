package com.hifibt.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hifibt.player.HiFiApp
import com.hifibt.player.bluetooth.BtAudioStatus
import com.hifibt.player.streaming.StreamQuality
import com.hifibt.player.streaming.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val nowPlaying: Track? = null,
    val nowPlayingQuality: StreamQuality? = null,
    val bt: BtAudioStatus = BtAudioStatus(),
    val eqEnabled: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel : ViewModel() {

    private val app = HiFiApp.instance
    private val provider = app.provider
    private val engine = app.audioEngine

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        // Single, lifecycle-scoped collector for live Bluetooth status.
        viewModelScope.launch {
            app.bluetoothMonitor.status.collect { bt ->
                _state.value = _state.value.copy(bt = bt)
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        _state.value = _state.value.copy(isSearching = true, error = null)
        viewModelScope.launch {
            runCatching { provider.search(q) }
                .onSuccess { _state.value = _state.value.copy(results = it, isSearching = false) }
                .onFailure { _state.value = _state.value.copy(isSearching = false, error = it.message) }
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            runCatching { provider.resolveStream(track) }
                .onSuccess { stream ->
                    engine.play(stream, track.title, track.artist)
                    _state.value = _state.value.copy(
                        nowPlaying = track,
                        nowPlayingQuality = stream.quality,
                        error = null,
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun toggleEq(on: Boolean) {
        if (on) engine.equalizer.enable() else engine.equalizer.disable()
        _state.value = _state.value.copy(eqEnabled = on)
    }

    fun setEqBand(band: Int, millibels: Short) = engine.equalizer.setBand(band, millibels)

    fun refreshBluetooth() = app.bluetoothMonitor.refresh()
}
