package com.fundingledger.model

import kotlinx.serialization.Serializable

@Serializable
enum class Category { GREEN, RED, NONE }

@Serializable
enum class Mode { FIXED, GOLD, PLUG }

/**
 * A single funding-stream row. [amount] is only meaningful for FIXED rows
 * (it is the user-typed SAR value); GOLD amounts derive from [grams] and the
 * gold price, and the PLUG amount derives from the target minus everything else.
 */
@Serializable
data class Row(
    val id: String,
    val label: String,
    val category: Category = Category.NONE,
    val mode: Mode = Mode.FIXED,
    val amount: Double = 0.0,
    val grams: Double? = null,
    val inTransfer: Boolean = false,
)

/**
 * The persisted ledger state: everything the user types, nothing derived.
 * pricePerGramOverride, when set, wins over the xauUsd-based formula;
 * editing xauUsd clears it so the formula takes over again.
 */
/**
 * A single KSA→SY transfer that has already been sent. These count down the
 * remaining transfer: remaining = (sum of inTransfer rows) − (sum of sent entries).
 */
@Serializable
data class TransferEntry(
    val id: String,
    val label: String,
    val amount: Double = 0.0,
)

@Serializable
data class Ledger(
    val target: Double,
    val xauUsd: Double,
    val pricePerGramOverride: Double? = null,
    val rows: List<Row>,
    val transfers: List<TransferEntry> = emptyList(),
)

object SeedData {
    fun ledger(): Ledger = Ledger(
        target = 277_500.0,
        // Latest hand-entered gold price; SAR/g derives from this (≈ 503.54).
        xauUsd = 4176.0,
        pricePerGramOverride = null,
        rows = listOf(
            Row("cash-riyadh", "Cash Riyadh", Category.GREEN, Mode.FIXED, amount = 32_500.0, inTransfer = true),
            Row("cash-sy-usd", "Cash SY (USD-Val)", Category.GREEN, Mode.FIXED, amount = 26_250.0),
            Row("inma-bank", "Inma Bank 001", Category.GREEN, Mode.FIXED, amount = 0.0),
            Row("cash-usd-ksa", "Cash USD KSA (16K USD)", Category.GREEN, Mode.FIXED, amount = 60_000.0, inTransfer = true),
            Row("nbd-cc", "NBD Credit Card", Category.RED, Mode.FIXED, amount = 5_000.0, inTransfer = true),
            Row("gold-riyadh", "New Gold Riyadh", Category.RED, Mode.GOLD, grams = 100.0, inTransfer = true),
            Row("gold-sy", "New Gold SY", Category.RED, Mode.GOLD, grams = 93.3),
            Row("bns-alloc", "Bns Allcation", Category.RED, Mode.FIXED, amount = 45_000.0, inTransfer = true),
            Row("july-slry", "July Slry CSH", Category.RED, Mode.PLUG, inTransfer = true),
        ),
        transfers = listOf(
            TransferEntry("travel-cash", "My travel cash", 37_500.0),
        ),
    )
}
