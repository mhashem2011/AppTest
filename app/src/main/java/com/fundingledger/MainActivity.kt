package com.fundingledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fundingledger.data.LedgerRepository
import com.fundingledger.gold.GoldViewModel
import com.fundingledger.gold.GoldWorker
import com.fundingledger.ui.GoldScreen
import com.fundingledger.ui.LedgerScreen
import com.fundingledger.ui.LedgerViewModel
import com.fundingledger.ui.theme.FundingLedgerTheme
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val ledgerViewModel: LedgerViewModel by viewModels {
        LedgerViewModel.factory(LedgerRepository(File(filesDir, "ledger.json")))
    }

    private val goldViewModel: GoldViewModel by viewModels {
        GoldViewModel.factory(applicationContext)
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleHourlyGoldWork()
        maybeRequestNotificationPermission()

        setContent {
            FundingLedgerTheme {
                MainScreen(
                    ledgerViewModel = ledgerViewModel,
                    goldViewModel = goldViewModel,
                    onShareJson = ::shareJson,
                )
            }
        }
    }

    /** Same hourly background refresh + move alerts the standalone GoldMonitor ran. */
    private fun scheduleHourlyGoldWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<GoldWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("gold_hourly", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun shareJson(jsonText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "FundingLedger export")
            putExtra(Intent.EXTRA_TEXT, jsonText)
        }
        startActivity(Intent.createChooser(intent, "Export ledger JSON"))
    }
}

@Composable
private fun MainScreen(
    ledgerViewModel: LedgerViewModel,
    goldViewModel: GoldViewModel,
    onShareJson: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Live-rate bridge: whenever a fresh gold snapshot lands (app open, manual
    // refresh, or the hourly worker having updated the cache), push its USD/oz
    // into the ledger so SAR/g, GOLD rows, and the plug recompute instantly.
    val snapshot by goldViewModel.snapshot.collectAsState()
    LaunchedEffect(snapshot?.time) {
        snapshot?.let { ledgerViewModel.syncGoldPrice(it.ozUsd) }
    }

    // Render everything ~10% smaller so more of each page fits on screen.
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * 0.9f, base.fontScale)) {
        Scaffold(
            bottomBar = {
                CompactNavBar(selected = selectedTab, onSelect = { selectedTab = it })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    0 -> LedgerScreen(viewModel = ledgerViewModel, onShareJson = onShareJson)
                    else -> GoldScreen(viewModel = goldViewModel)
                }
            }
        }
    }
}

/** A short two-tab bar (the stock Material 3 NavigationBar is ~80dp tall). */
@Composable
private fun CompactNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(46.dp),
            ) {
                NavCell("📒", "Ledger", selected == 0, Modifier.weight(1f)) { onSelect(0) }
                NavCell("🥇", "Gold", selected == 1, Modifier.weight(1f)) { onSelect(1) }
            }
        }
    }
}

@Composable
private fun NavCell(
    emoji: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
