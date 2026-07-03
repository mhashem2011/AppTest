package com.fundingledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.fundingledger.data.LedgerRepository
import com.fundingledger.ui.LedgerScreen
import com.fundingledger.ui.LedgerViewModel
import com.fundingledger.ui.theme.FundingLedgerTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels {
        LedgerViewModel.factory(LedgerRepository(File(filesDir, "ledger.json")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FundingLedgerTheme {
                LedgerScreen(viewModel = viewModel, onShareJson = ::shareJson)
            }
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
