package com.fundingledger.logic

import kotlin.math.round

/** A row with its derived amount and percent-of-target for the current recalculation. */
data class RowComputed(
    val row: Row,
    val amount: Double,
    val percent: Double
)

data class LedgerSnapshot(
    val pricePerGram: Double,
    val rows: List<RowComputed>,
    val greenSubtotal: Double,
    val redSubtotal: Double,
    val grandTotal: Double,
    val fundingGap: Double,
    val transferTotal: Double,
    val percentSourced: Double
) {
    val isBalanced: Boolean get() = kotlin.math.abs(fundingGap) < 0.005

    // Grand total is forced to target by the plug row itself, so a shortfall never shows up
    // in fundingGap - it shows up as the plug going negative instead.
    val isOverfunded: Boolean get() = rows.any { it.row.mode == Mode.PLUG && it.amount < -0.005 }
}

/**
 * The full recalculation chain. Every derived value in the ledger flows from these
 * pure functions of (rows, pricePerGram, target) - there is no other source of truth.
 */
object LedgerCalculator {

    fun pricePerGram(xauUsd: Double, override: Double?): Double =
        override ?: (xauUsd / 31.1 * 3.75)

    /** Resolves GOLD row amounts from grams, then solves the single PLUG row against target. */
    fun computeAmounts(rows: List<Row>, pricePerGram: Double, target: Double): List<Row> {
        val withGold = rows.map { row ->
            if (row.mode == Mode.GOLD) row.copy(amount = (row.grams ?: 0.0) * pricePerGram) else row
        }
        val nonPlugSum = withGold.filter { it.mode != Mode.PLUG }.sumOf { it.amount }
        val plugAmount = target - nonPlugSum
        return withGold.map { row ->
            if (row.mode == Mode.PLUG) row.copy(amount = plugAmount) else row
        }
    }

    fun snapshot(state: LedgerState): LedgerSnapshot {
        val ppg = pricePerGram(state.xauUsd, state.pricePerGramOverride)
        val computedRows = computeAmounts(state.rows, ppg, state.target)

        val rowsUi = computedRows.map { row -> RowComputed(row, row.amount, percentOf(row.amount, state.target)) }

        val greenSubtotal = computedRows.filter { it.category == Category.GREEN }.sumOf { it.amount }
        val redSubtotal = computedRows.filter { it.category == Category.RED }.sumOf { it.amount }
        val grandTotal = computedRows.sumOf { it.amount }
        val fundingGap = state.target - grandTotal
        val transferTotal = computedRows.filter { it.inTransfer }.sumOf { it.amount }
        val percentSourced = percentOf(grandTotal, state.target)

        return LedgerSnapshot(
            pricePerGram = ppg,
            rows = rowsUi,
            greenSubtotal = greenSubtotal,
            redSubtotal = redSubtotal,
            grandTotal = grandTotal,
            fundingGap = fundingGap,
            transferTotal = transferTotal,
            percentSourced = percentSourced
        )
    }

    private fun percentOf(amount: Double, target: Double): Double =
        if (target == 0.0) 0.0 else round1(amount / target * 100.0)

    private fun round1(value: Double): Double = round(value * 10.0) / 10.0
}
