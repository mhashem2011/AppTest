package com.hifibt.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hifibt.player.ui.screens.MainScreen
import com.hifibt.player.ui.theme.HiFiTheme

class MainActivity : ComponentActivity() {

    private val requestBtPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* status refresh handled in screen */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBtPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            HiFiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen()
                }
            }
        }
    }
}
