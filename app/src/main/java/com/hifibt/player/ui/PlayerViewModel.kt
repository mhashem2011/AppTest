package com.hifibt.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hifibt.player.HiFiApp
import com.hifibt.player.bluetooth.BtAudioStatus
import com.hifibt.player.content.Episode
import com.hifibt.player.content.PodcastShow
import com.hifibt.player.content.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Tab { RADIO, PODCASTS }

data class PlayerUiState(
    val tab: Tab = Tab.RADIO,
    val query: String = "",
    val hifiOnly: Boolean = false,
    val stations: List<Station> = emptyList(),
    val shows: List<PodcastShow> = emptyList(),
    val selectedShow: PodcastShow? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = false,
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val nowPlayingQuality: String? = null,
    val bt: BtAudioStatus = BtAudioStatus(),
    val eqEnabled: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel : ViewModel() {

    private val app = HiFiApp.instance
    private val engine = app.audioEngine

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.bluetoothMonitor.status.collect { bt -> _state.value = _state.value.copy(bt = bt) }
        }
        viewModelScope.launch {
            engine.playbackError.collect { err -> if (err != null) _state.value = _state.value.copy(error = err) }
        }
        loadTopStations()
    }

    fun selectTab(tab: Tab) {
        _state.value = _state.value.copy(tab = tab, error = null)
        if (tab == Tab.RADIO && _state.value.stations.isEmpty()) loadTopStations()
    }

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    fun toggleHiFiOnly(on: Boolean) {
        _state.value = _state.value.copy(hifiOnly = on)
        if (_state.value.tab == Tab.RADIO) {
            if (_state.value.query.isBlank()) loadTopStations() else search()
        }
    }

    private fun loadTopStations() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { app.radio.topStations(hifiOnly = _state.value.hifiOnly) }
                .onSuccess { _state.value = _state.value.copy(stations = it, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) { if (_state.value.tab == Tab.RADIO) loadTopStations(); return }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                if (_state.value.tab == Tab.RADIO) {
                    _state.value = _state.value.copy(stations = app.radio.search(q, _state.value.hifiOnly))
                } else {
                    _state.value = _state.value.copy(shows = app.podcasts.searchShows(q), selectedShow = null, episodes = emptyList())
                }
            }
                .onSuccess { _state.value = _state.value.copy(isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun openShow(show: PodcastShow) {
        _state.value = _state.value.copy(selectedShow = show, isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { app.podcasts.episodes(show) }
                .onSuccess { _state.value = _state.value.copy(episodes = it, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun closeShow() { _state.value = _state.value.copy(selectedShow = null, episodes = emptyList()) }

    fun playStation(station: Station) {
        engine.play(station.streamUrl, station.name, station.country)
        _state.value = _state.value.copy(
            nowPlayingTitle = station.name,
            nowPlayingSubtitle = station.country,
            nowPlayingQuality = station.qualityLabel,
            error = null,
        )
    }

    fun playEpisode(episode: Episode) {
        engine.play(episode.audioUrl, episode.title, episode.showTitle)
        _state.value = _state.value.copy(
            nowPlayingTitle = episode.title,
            nowPlayingSubtitle = episode.showTitle,
            nowPlayingQuality = "Podcast",
            error = null,
        )
    }

    fun toggleEq(on: Boolean) {
        if (on) engine.equalizer.enable() else engine.equalizer.disable()
        _state.value = _state.value.copy(eqEnabled = on)
    }

    fun setEqBand(band: Int, millibels: Short) = engine.equalizer.setBand(band, millibels)

    fun refreshBluetooth() = app.bluetoothMonitor.refresh()
}
