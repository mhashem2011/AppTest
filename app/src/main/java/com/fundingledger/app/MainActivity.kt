package com.fundingledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fundingledger.app.data.LedgerRepository
import com.fundingledger.app.ui.LedgerScreen
import com.fundingledger.app.ui.theme.FundingLedgerTheme
import com.fundingledger.app.viewmodel.LedgerViewModel
import com.fundingledger.app.viewmodel.LedgerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LedgerRepository(applicationContext)

        setContent {
            FundingLedgerTheme {
                val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
                LedgerScreen(viewModel)
            }
        }
    }
}
