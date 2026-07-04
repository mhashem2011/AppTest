package com.fundingledger.domain

import com.fundingledger.model.Category
import com.fundingledger.model.Ledger
import com.fundingledger.model.Mode
import com.fundingledger.model.Row

/** A row plus its fully derived values. */
data class ComputedRow(
    val row: Row,
    val amount: Double,
    val percentOfTarget: Double,
)

/** Everything the UI shows, derived from a [Ledger] in one pass. */
data class DerivedLedger(
    val pricePerGram: Double,
    val rows: List<ComputedRow>,
    val greenSubtotal: Double,
    val redSubtotal: Double,
    val grandTotal: Double,
    val fundingGap: Double,
    /** Total that needs to move KSA→SY (sum of inTransfer rows). */
    val transferKsaToSy: Double,
    /** Sum of transfer tranches already sent. */
    val transfersMade: Double,
    /** transferKsaToSy − transfersMade; counts down to 0 as tranches are sent. */
    val remainingTransfer: Double,
    /** > 0 when non-plug rows alone exceed the target (plug went negative). */
    val overFundedBy: Double,
)

object LedgerCalculator {

    const val TROY_OUNCE_GRAMS = 31.1
    const val USD_TO_SAR = 3.75

    fun pricePerGram(ledger: Ledger): Double =
        ledger.pricePerGramOverride ?: (ledger.xauUsd / TROY_OUNCE_GRAMS * USD_TO_SAR)

    /** The full recalculation chain; re-run on every edit. */
    fun derive(ledger: Ledger): DerivedLedger {
        val gramPrice = pricePerGram(ledger)

        // Amounts for every non-plug row first, so the plug can absorb the remainder.
        fun baseAmount(row: Row): Double = when (row.mode) {
            Mode.FIXED -> row.amount
            Mode.GOLD -> (row.grams ?: 0.0) * gramPrice
            Mode.PLUG -> 0.0
        }

        val nonPlugSum = ledger.rows.filter { it.mode != Mode.PLUG }.sumOf(::baseAmount)
        val hasPlug = ledger.rows.any { it.mode == Mode.PLUG }
        val plugAmount = ledger.target - nonPlugSum

        val computed = ledger.rows.map { row ->
            val amount = if (row.mode == Mode.PLUG) plugAmount else baseAmount(row)
            val percent = if (ledger.target != 0.0) amount / ledger.target * 100 else 0.0
            ComputedRow(row, amount, percent)
        }

        val grandTotal = computed.sumOf { it.amount }
        val transferTotal = computed.filter { it.row.inTransfer }.sumOf { it.amount }
        val transfersMade = ledger.transfers.sumOf { it.amount }
        return DerivedLedger(
            pricePerGram = gramPrice,
            rows = computed,
            greenSubtotal = computed.filter { it.row.category == Category.GREEN }.sumOf { it.amount },
            redSubtotal = computed.filter { it.row.category == Category.RED }.sumOf { it.amount },
            grandTotal = grandTotal,
            fundingGap = ledger.target - grandTotal,
            transferKsaToSy = transferTotal,
            transfersMade = transfersMade,
            remainingTransfer = transferTotal - transfersMade,
            overFundedBy = if (hasPlug && plugAmount < 0) -plugAmount else 0.0,
        )
    }
}
