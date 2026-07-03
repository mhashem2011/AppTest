package com.fundingledger.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val sarFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private val percentFormat = DecimalFormat("0.0", DecimalFormatSymbols(Locale.US))
private val priceFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
private val editableFormat = DecimalFormat("#0.##", DecimalFormatSymbols(Locale.US))

fun formatSar(value: Double): String = sarFormat.format(value)
fun formatPercent(value: Double): String = percentFormat.format(value)
fun formatPrice(value: Double): String = priceFormat.format(value)

/** Plain (no thousands separator) representation used to seed an edit TextField. */
fun formatEditable(value: Double): String = editableFormat.format(value)

fun parseEditable(text: String): Double? = text.trim().replace(",", "").toDoubleOrNull()
