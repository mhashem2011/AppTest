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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("📒", fontSize = 20.sp) },
                    label = { Text("Ledger") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🥇", fontSize = 20.sp) },
                    label = { Text("Gold") },
                )
            }
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
