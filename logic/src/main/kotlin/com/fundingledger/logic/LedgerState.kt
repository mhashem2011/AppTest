package com.fundingledger.logic

import kotlinx.serialization.Serializable

@Serializable
data class LedgerState(
    val target: Double,
    val xauUsd: Double,
    val pricePerGramOverride: Double? = null,
    val rows: List<Row>
) {
    companion object {
        /** Current state of the ledger this app replaces, as of seeding. */
        fun seed(): LedgerState = LedgerState(
            target = 277_500.0,
            xauUsd = 4187.0,
            pricePerGramOverride = 504.67,
            rows = listOf(
                Row(
                    id = "cash_riyadh",
                    label = "Cash Riyadh",
                    category = Category.GREEN,
                    mode = Mode.FIXED,
                    amount = 33_000.0,
                    inTransfer = true
                ),
                Row(
                    id = "cash_sy",
                    label = "Cash SY (USD-Val)",
                    category = Category.GREEN,
                    mode = Mode.FIXED,
                    amount = 26_250.0,
                    inTransfer = false
                ),
                Row(
                    id = "inma_bank_001",
                    label = "Inma Bank 001",
                    category = Category.GREEN,
                    mode = Mode.FIXED,
                    amount = 0.0,
                    inTransfer = false
                ),
                Row(
                    id = "cash_usd_ksa",
                    label = "Cash USD KSA (16K USD)",
                    category = Category.GREEN,
                    mode = Mode.FIXED,
                    amount = 60_000.0,
                    inTransfer = true
                ),
                Row(
                    id = "nbd_credit_card",
                    label = "NBD Credit Card",
                    category = Category.RED,
                    mode = Mode.FIXED,
                    amount = 5_000.0,
                    inTransfer = true
                ),
                Row(
                    id = "new_gold_riyadh",
                    label = "New Gold Riyadh",
                    category = Category.RED,
                    mode = Mode.GOLD,
                    grams = 100.0,
                    inTransfer = true
                ),
                Row(
                    id = "new_gold_sy",
                    label = "New Gold SY",
                    category = Category.RED,
                    mode = Mode.GOLD,
                    grams = 93.3,
                    inTransfer = false
                ),
                Row(
                    id = "bns_allocation",
                    label = "Bns Allcation",
                    category = Category.RED,
                    mode = Mode.FIXED,
                    amount = 45_000.0,
                    inTransfer = true
                ),
                Row(
                    id = "july_slry_csh",
                    label = "July Slry CSH",
                    category = Category.RED,
                    mode = Mode.PLUG,
                    inTransfer = true
                )
            )
        )
    }
}
