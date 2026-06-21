package com.hifibt.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hifibt.player.content.Episode
import com.hifibt.player.content.PodcastShow
import com.hifibt.player.content.Station
import com.hifibt.player.ui.PlayerViewModel
import com.hifibt.player.ui.Tab as ContentTab

@Composable
fun MainScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshBluetooth() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("HiFi Radio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "High-quality radio & podcasts for Android Auto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        BluetoothStatusCard(state.bt.connectedDeviceName, state.bt.codecName, vm::refreshBluetooth)

        TabRow(selectedTabIndex = if (state.tab == ContentTab.RADIO) 0 else 1) {
            Tab(selected = state.tab == ContentTab.RADIO, onClick = { vm.selectTab(ContentTab.RADIO) }, text = { Text("Radio") })
            Tab(selected = state.tab == ContentTab.PODCASTS, onClick = { vm.selectTab(ContentTab.PODCASTS) }, text = { Text("Podcasts") })
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text(if (state.tab == ContentTab.RADIO) "Search stations" else "Search podcasts") },
            singleLine = true,
            trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.Default.Search, contentDescription = "Search") } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.tab == ContentTab.RADIO) {
                FilterChip(
                    selected = state.hifiOnly,
                    onClick = { vm.toggleHiFiOnly(!state.hifiOnly) },
                    label = { Text("Hi-Fi only (FLAC / ≥256 kbps)") },
                )
            }
        }

        EqRow(state.eqEnabled, vm::toggleEq)

        if (state.isLoading) CircularProgressIndicator(modifier = Modifier.padding(12.dp))
        state.error?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        NowPlaying(state.nowPlayingTitle, state.nowPlayingSubtitle, state.nowPlayingQuality)

        when {
            state.tab == ContentTab.RADIO -> StationList(state.stations, vm::playStation)
            state.selectedShow != null -> EpisodeList(state.selectedShow!!, state.episodes, vm::closeShow, vm::playEpisode)
            else -> ShowList(state.shows, vm::openShow)
        }
    }
}

@Composable
private fun BluetoothStatusCard(deviceName: String?, codec: String?, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(deviceName ?: "Best quality: connect via wireless Android Auto", fontWeight = FontWeight.SemiBold)
                val codecText = when (codec) {
                    null -> "Over Bluetooth, verify AAC in Developer Options"
                    "AAC" -> "Bluetooth codec: AAC ✓"
                    "SBC" -> "Bluetooth codec: SBC — switch to AAC"
                    else -> "Bluetooth codec: $codec"
                }
                Text(codecText, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Search, contentDescription = "Refresh") }
        }
    }
}

@Composable
private fun EqRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Equalizer", fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) "On" else "Off — transparent playback",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun NowPlaying(title: String?, subtitle: String?, quality: String?) {
    if (title == null) return
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Now playing", style = MaterialTheme.typography.labelSmall)
            Text(title, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            quality?.let {
                Text("Quality: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StationList(stations: List<Station>, onPlay: (Station) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(stations) { s ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Medium)
                    Text("${s.qualityLabel} · ${s.tags.take(40)}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onPlay(s) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
            }
        }
    }
}

@Composable
private fun ShowList(shows: List<PodcastShow>, onOpen: (PodcastShow) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(shows) { show ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(show.title, fontWeight = FontWeight.Medium)
                    Text(show.author, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onOpen(show) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Open") }
            }
        }
    }
}

@Composable
private fun EpisodeList(show: PodcastShow, episodes: List<Episode>, onBack: () -> Unit, onPlay: (Episode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Text(show.title, fontWeight = FontWeight.SemiBold)
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(episodes) { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.title, fontWeight = FontWeight.Medium)
                    Text(e.durationLabel.ifEmpty { e.publishedDate }, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onPlay(e) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
            }
        }
    }
}
