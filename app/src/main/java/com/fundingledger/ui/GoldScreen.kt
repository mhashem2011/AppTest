package com.fundingledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/** GoldMonitor's dark dashboard palette, kept identical in the merged page. */
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
    val BtnDisabled = Color(0xFF5A5A5A)
}

private fun colorFor(v: Double): Color = when {
    v > 0 -> GoldColors.Gain
    v < 0 -> GoldColors.Loss
    else -> GoldColors.Muted
}

private fun trendEmoji(t: GoldRepository.Trend): String = when (t) {
    GoldRepository.Trend.UP -> "↗️"
    GoldRepository.Trend.DOWN -> "↘️"
    GoldRepository.Trend.FLAT -> "➡️"
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        val s = snapshot
        if (s == null) {
            Spacer(Modifier.height(60.dp))
            Text(
                when {
                    refreshing -> "Fetching live rate…"
                    failed -> "Price unavailable — source failed. Tap REFRESH NOW to retry."
                    else -> "No reading yet. Tap REFRESH NOW."
                },
                color = GoldColors.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(60.dp))
        } else {
            GoldDashboard(s)
        }

        Spacer(Modifier.height(8.dp))

        // Footer: source · updated time · hourly worker state (as in the original).
        val footer = when {
            refreshing -> "updating…"
            s != null -> {
                val updated = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(s.time))
                (if (failed) "source failed · showing " else "") +
                    "${s.source} · updated $updated · hourly: active"
            }
            else -> ""
        }
        Text(footer, color = GoldColors.Muted, fontSize = 12.sp)

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::refresh,
            enabled = !refreshing,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldColors.Gain,
                contentColor = GoldColors.OnGold,
                disabledContainerColor = GoldColors.BtnDisabled,
                disabledContentColor = GoldColors.OnGold,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (refreshing) "UPDATING…" else "REFRESH NOW",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(8.dp))
        val ctx = LocalContext.current
        val version = remember {
            runCatching {
                "v" + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }.getOrDefault("")
        }
        Text(
            version,
            color = GoldColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GoldDashboard(s: GoldRepository.Snapshot) {
    // ---- LIVE GOLD PRICE · XAU/USD ----
    Text(
        "LIVE GOLD PRICE · XAU/USD",
        color = GoldColors.Muted,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )
    Text(
        "$" + GoldConfig.price2(s.ozUsd),
        color = GoldColors.Gold,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .border(1.dp, GoldColors.CardStroke, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text("USD per troy ounce", color = GoldColors.Muted, fontSize = 12.sp)
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RefChip("1H", s.ref1h?.let { GoldConfig.ozFromSar(it) }, s.ozUsd, usd = true, Modifier.weight(1f))
        RefChip("3H", s.ref3h?.let { GoldConfig.ozFromSar(it) }, s.ozUsd, usd = true, Modifier.weight(1f))
        RefChip("Y.CLOSE", s.yesterday?.let { GoldConfig.ozFromSar(it) }, s.ozUsd, usd = true, Modifier.weight(1f))
    }

    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = GoldColors.CardStroke)
    Spacer(Modifier.height(12.dp))

    // ---- SAR / g hero ----
    Text("SAR / g", color = GoldColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            GoldConfig.price2(s.sar),
            color = GoldColors.Warm,
            fontSize = 50.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(8.dp))
        Text(trendEmoji(s.trend), fontSize = 26.sp)
    }
    Spacer(Modifier.height(2.dp))
    Text(
        "$" + GoldConfig.price2(s.ozUsd) + " ÷ 31.1 × 3.75 = " + GoldConfig.price2(s.sar) + " SAR/g",
        color = GoldColors.Muted,
        fontSize = 12.sp,
    )

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RefChip("1H", s.ref1h, s.sarRaw, usd = false, Modifier.weight(1f))
        RefChip("3H", s.ref3h, s.sarRaw, usd = false, Modifier.weight(1f))
        RefChip("Y.CLOSE", s.yesterday, s.sarRaw, usd = false, Modifier.weight(1f))
    }

    Spacer(Modifier.height(14.dp))

    // ---- Portfolio card ----
    Column(
        Modifier
            .fillMaxWidth()
            .background(GoldColors.Card, RoundedCornerShape(14.dp))
            .border(1.dp, GoldColors.CardStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PortfolioRow("Lot I · 150g", s.val1, s.pl1, bold = false)
        PortfolioRow("Lot II · 50g", s.val2, s.pl2, bold = false)
        HorizontalDivider(color = GoldColors.CardStroke)
        PortfolioRow("TOTAL · 200g", s.total, s.totalPl, bold = true, pct = s.totalPct)

        val chg1hSar = s.ref1h?.let { (s.sarRaw - it) * GoldConfig.TOTAL_G }
        Text(
            if (chg1hSar == null) "1h: —" else "1h: " + GoldConfig.signed0(chg1hSar) + " SAR",
            color = if (chg1hSar == null) GoldColors.Muted else colorFor(chg1hSar),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PortfolioRow(label: String, value: Double, pl: Double, bold: Boolean, pct: Double? = null) {
    val labelColor = if (bold) GoldColors.Gold else GoldColors.Warm
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.2f), color = labelColor, fontSize = 15.sp, fontWeight = weight)
        Text(
            GoldConfig.val0(value),
            Modifier.weight(0.9f),
            color = if (bold) GoldColors.Gold else GoldColors.Warm,
            fontSize = 15.sp,
            fontWeight = weight,
            textAlign = TextAlign.Center,
        )
        Text(
            GoldConfig.signed0(pl) + (pct?.let { " (" + GoldConfig.pct2(it) + ")" } ?: ""),
            Modifier.weight(1f),
            color = colorFor(pl),
            fontSize = 15.sp,
            fontWeight = weight,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RefChip(label: String, ref: Double?, current: Double, usd: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(GoldColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, GoldColors.CardStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = GoldColors.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            ref?.let { (if (usd) "$" else "") + GoldConfig.price2(it) } ?: "—",
            color = GoldColors.Warm,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            ref?.let { GoldConfig.signed2(current - it) } ?: "",
            color = ref?.let { colorFor(current - it) } ?: GoldColors.Muted,
            fontSize = 12.sp,
        )
    }
}
