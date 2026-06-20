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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.hifibt.player.streaming.StreamQuality
import com.hifibt.player.streaming.Track
import com.hifibt.player.ui.PlayerViewModel

@Composable
fun MainScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshBluetooth() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "HiFi BT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Max-quality Bluetooth streaming",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        BluetoothStatusCard(state.bt.connectedDeviceName, state.bt.codecName, vm::refreshBluetooth)

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Search Deezer") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = vm::search) { Icon(Icons.Default.Search, contentDescription = "Search") }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        EqRow(state.eqEnabled, vm::toggleEq)

        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        state.error?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        state.nowPlaying?.let { NowPlaying(it, state.nowPlayingQuality) }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.results) { track ->
                TrackRow(track) { vm.play(track) }
            }
        }
    }
}

@Composable
private fun BluetoothStatusCard(deviceName: String?, codec: String?, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(deviceName ?: "No Bluetooth audio device", fontWeight = FontWeight.SemiBold)
                val codecText = when (codec) {
                    null -> "Codec: unknown — verify AAC in Developer Options"
                    "AAC" -> "Codec: AAC ✓ (best the car supports)"
                    "SBC" -> "Codec: SBC — switch to AAC for better quality"
                    else -> "Codec: $codec"
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Equalizer", fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) "On — shaped before Bluetooth encoding" else "Off — bit-perfect path",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun NowPlaying(track: Track, quality: StreamQuality?) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Now playing", style = MaterialTheme.typography.labelSmall)
            Text(track.title, fontWeight = FontWeight.Bold)
            Text(track.artist, style = MaterialTheme.typography.bodySmall)
            quality?.let {
                val label = when (it) {
                    StreamQuality.PREVIEW_LOSSY -> "30s preview (lossy)"
                    StreamQuality.MP3_320 -> "MP3 320"
                    StreamQuality.FLAC_CD -> "FLAC 16/44.1 (lossless)"
                    StreamQuality.FLAC_HIRES -> "FLAC hi-res"
                }
                Text("Quality: $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.Medium)
            Text("${track.artist} · ${track.album}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
    }
}
