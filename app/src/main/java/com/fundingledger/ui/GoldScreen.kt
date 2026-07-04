package com.fundingledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fundingledger.gold.GoldConfig
import com.fundingledger.gold.GoldRepository
import com.fundingledger.gold.GoldViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** GoldMonitor's dark dashboard palette, kept as-is for the merged page. */
private object GoldColors {
    val Bg = Color(0xFF0E0B07)
    val Gold = Color(0xFFE8C558)
    val Warm = Color(0xFFEFE7D4)
    val Muted = Color(0xFF97896C)
    val Gain = Color(0xFF57D98F)
    val Loss = Color(0xFFF2766B)
    val Card = Color(0xFF17120B)
    val CardStroke = Color(0xFF2A2114)
    val OnGold = Color(0xFF1A1206)
}

private fun colorFor(v: Double): Color = when {
    v > 0 -> GoldColors.Gain
    v < 0 -> GoldColors.Loss
    else -> GoldColors.Muted
}

@Composable
fun GoldScreen(viewModel: GoldViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val failed by viewModel.failed.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GoldColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Gold Monitor (24k)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = GoldColors.Warm,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        val s = snapshot
        if (s == null) {
            Spacer(Modifier.height(40.dp))
            if (refreshing) {
                CircularProgressIndicator(color = GoldColors.Gold)
                Spacer(Modifier.height(12.dp))
                Text("Fetching live rate…", color = GoldColors.Muted, fontSize = 13.sp)
            } else {
                Text(
                    if (failed) "Price unavailable — source failed. Tap Refresh to retry."
                    else "No reading yet. Tap Refresh.",
                    color = GoldColors.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            GoldDashboard(s)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = viewModel::refresh,
            enabled = !refreshing,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldColors.Gain,
                contentColor = GoldColors.OnGold,
                disabledContainerColor = Color(0xFF5A5A5A),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (refreshing) "Updating…" else "Refresh", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        val footer = when {
            refreshing -> "Updating…"
            failed && s != null -> "Source failed — showing last reading"
            s != null -> {
                val updated = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(s.time))
                "${s.source} · $updated"
            }
            else -> ""
        }
        Text(footer, color = GoldColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Live rate feeds the Ledger's gold price automatically",
            color = GoldColors.Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun GoldDashboard(s: GoldRepository.Snapshot) {
    val (arrow, arrowColor) = when (s.trend) {
        GoldRepository.Trend.UP -> "↗️" to GoldColors.Gain
        GoldRepository.Trend.DOWN -> "↘️" to GoldColors.Loss
        GoldRepository.Trend.FLAT -> "➡️" to GoldColors.Muted
    }

    // ---- USD/oz header ----
    Text(
        "$" + GoldConfig.price2(s.ozUsd) + " / oz",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = GoldColors.Warm,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UsdChip("1H", s.ref1h, s.ozUsd, Modifier.weight(1f))
        UsdChip("3H", s.ref3h, s.ozUsd, Modifier.weight(1f))
        UsdChip("YST", s.yesterday, s.ozUsd, Modifier.weight(1f))
    }

    Spacer(Modifier.height(18.dp))

    // ---- SAR/g hero ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            GoldConfig.price2(s.sar),
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = GoldColors.Gold,
        )
        Spacer(Modifier.padding(4.dp))
        Text(arrow, fontSize = 28.sp, color = arrowColor)
    }
    Text("SAR / gram", color = GoldColors.Muted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "$" + GoldConfig.price2(s.ozUsd) + " ÷ 31.1 × 3.75 = " + GoldConfig.price2(s.sar) + " SAR/g",
        color = GoldColors.Muted,
        fontSize = 12.sp,
    )

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SarChip("1H", s.ref1h, s.sarRaw, Modifier.weight(1f))
        SarChip("3H", s.ref3h, s.sarRaw, Modifier.weight(1f))
        SarChip("YST", s.yesterday, s.sarRaw, Modifier.weight(1f))
    }

    Spacer(Modifier.height(10.dp))
    val chg1hSar = s.ref1h?.let { (s.sarRaw - it) * GoldConfig.TOTAL_G }
    Text(
        if (chg1hSar == null) "1h: —" else "1h (200g): " + GoldConfig.signed0(chg1hSar) + " SAR",
        color = if (chg1hSar == null) GoldColors.Muted else colorFor(chg1hSar),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )

    Spacer(Modifier.height(16.dp))

    // ---- Portfolio card ----
    Column(
        Modifier
            .fillMaxWidth()
            .background(GoldColors.Card, RoundedCornerShape(14.dp))
            .border(1.dp, GoldColors.CardStroke, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Portfolio (24k)", color = GoldColors.Muted, fontSize = 12.sp)
        PortfolioRow("150 g", s.val1, s.pl1)
        PortfolioRow("50 g", s.val2, s.pl2)
        HorizontalDivider(color = GoldColors.CardStroke)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TOTAL (200 g)",
                Modifier.weight(1f),
                color = GoldColors.Warm,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    GoldConfig.val0(s.total) + " SAR",
                    color = GoldColors.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    GoldConfig.signed0(s.totalPl) + " (" + GoldConfig.pct2(s.totalPct) + ")",
                    color = colorFor(s.totalPl),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PortfolioRow(label: String, value: Double, pl: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = GoldColors.Warm, fontSize = 13.sp)
        Text(GoldConfig.val0(value), color = GoldColors.Warm, fontSize = 13.sp)
        Spacer(Modifier.padding(6.dp))
        Text(GoldConfig.signed0(pl), color = colorFor(pl), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UsdChip(label: String, refSar: Double?, currentOz: Double, modifier: Modifier = Modifier) {
    val refUsd = refSar?.let { GoldConfig.ozFromSar(it) }
    Chip(
        label = label,
        value = refUsd?.let { "$" + GoldConfig.price2(it) } ?: "—",
        delta = refUsd?.let { currentOz - it },
        modifier = modifier,
    )
}

@Composable
private fun SarChip(label: String, ref: Double?, sarRaw: Double, modifier: Modifier = Modifier) {
    Chip(
        label = label,
        value = ref?.let { GoldConfig.price2(it) } ?: "—",
        delta = ref?.let { sarRaw - it },
        modifier = modifier,
    )
}

@Composable
private fun Chip(label: String, value: String, delta: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(GoldColors.Card, RoundedCornerShape(10.dp))
            .border(1.dp, GoldColors.CardStroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = GoldColors.Muted, fontSize = 10.sp)
        Text(value, color = GoldColors.Warm, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            delta?.let { GoldConfig.signed2(it) } ?: "",
            color = delta?.let { colorFor(it) } ?: GoldColors.Muted,
            fontSize = 11.sp,
        )
    }
}
