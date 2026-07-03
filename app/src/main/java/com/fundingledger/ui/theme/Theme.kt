package com.fundingledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The ledger's fixed banding colors (kept identical in light/dark for legibility). */
object LedgerColors {
    val GreenRow = Color(0xFFD8ECDC)
    val RedRow = Color(0xFFE9C9C9)
    val GreenBand = Color(0xFFA8D5B0)
    val RedBand = Color(0xFFC97A7A)
    val TransferYellow = Color(0xFFFFF3A3)
    val Ink = Color(0xFF1C2520)
    val PositiveGreen = Color(0xFF1B7A34)
    val NegativeRed = Color(0xFFB3261E)
    val HeaderGray = Color(0xFFE7E4DE)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B3E),
    secondary = Color(0xFF8C4A4A),
    surface = Color(0xFFFCFBF8),
    background = Color(0xFFF6F4EF),
)

@Composable
fun FundingLedgerTheme(content: @Composable () -> Unit) {
    // The ledger bands are light pastels, so keep a light scheme even in dark mode.
    MaterialTheme(colorScheme = LightColors, content = content)
}
