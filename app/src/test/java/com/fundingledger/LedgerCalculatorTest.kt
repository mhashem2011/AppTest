package com.fundingledger

import com.fundingledger.domain.LedgerCalculator
import com.fundingledger.model.Category
import com.fundingledger.model.Ledger
import com.fundingledger.model.Mode
import com.fundingledger.model.Row
import com.fundingledger.model.SeedData
import com.fundingledger.model.TransferEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalculatorTest {

    private val seed = SeedData.ledger()
    private val derived = LedgerCalculator.derive(seed)

    // Seed carries no manual override, so the price derives from the xauUsd formula.
    private val seedPrice = seed.xauUsd / 31.1 * 3.75

    private fun amountOf(id: String) = derived.rows.first { it.row.id == id }.amount

    @Test
    fun `price per gram derives from the seeded xauUsd`() {
        assertEquals(seedPrice, derived.pricePerGram, 1e-9)
        // Sanity-check against the value shown in the app (≈ 496.95).
        assertEquals(496.95, derived.pricePerGram, 0.01)
    }

    @Test
    fun `manual override wins over the xauUsd formula`() {
        val overridden = seed.copy(pricePerGramOverride = 504.67)
        assertEquals(504.67, LedgerCalculator.pricePerGram(overridden), 1e-9)
    }

    @Test
    fun `gold rows derive amount from grams times price`() {
        assertEquals(100.0 * seedPrice, amountOf("gold-riyadh"), 1e-6)
        assertEquals(93.3 * seedPrice, amountOf("gold-sy"), 1e-6)
    }

    @Test
    fun `plug absorbs the remainder so total equals target`() {
        // Everything but the plug, subtracted from target.
        val nonPlug = 117_750.0 + 5_000.0 + 100.0 * seedPrice + 93.3 * seedPrice + 45_000.0
        assertEquals(seed.target - nonPlug, amountOf("july-slry"), 1e-6)
        // Matches the app's displayed plug (≈ 13,689).
        assertEquals(13_689.0, amountOf("july-slry"), 0.5)
        assertEquals(seed.target, derived.grandTotal, 1e-6)
        assertEquals(0.0, derived.fundingGap, 1e-6)
    }

    @Test
    fun `subtotals split by category`() {
        assertEquals(117_750.0, derived.greenSubtotal, 1e-6)
        assertEquals(seed.target - 117_750.0, derived.redSubtotal, 1e-6)
    }

    @Test
    fun `transfer sums only inTransfer rows`() {
        val expected = derived.rows.filter { it.row.inTransfer }.sumOf { it.amount }
        assertEquals(expected, derived.transferKsaToSy, 1e-6)
        // Matches the app's displayed transfer total (≈ 156,134).
        assertEquals(156_134.0, derived.transferKsaToSy, 0.5)
    }

    @Test
    fun `seed transfer line counts down the remaining transfer`() {
        // Seed ships with a 37,500 "My travel cash" tranche already sent.
        assertEquals(37_500.0, derived.transfersMade, 1e-6)
        assertEquals(derived.transferKsaToSy - 37_500.0, derived.remainingTransfer, 1e-6)
        // Matches the app's displayed remaining (≈ 118,634).
        assertEquals(118_634.0, derived.remainingTransfer, 0.5)
    }

    @Test
    fun `additional sent tranches reduce the remaining transfer`() {
        val edited = seed.copy(
            transfers = seed.transfers + listOf(
                TransferEntry("t1", "Bank 1", 50_000.0),
                TransferEntry("t2", "Courier", 30_000.0),
            ),
        )
        val d = LedgerCalculator.derive(edited)
        assertEquals(37_500.0 + 80_000.0, d.transfersMade, 1e-6)
        assertEquals(d.transferKsaToSy - 117_500.0, d.remainingTransfer, 1e-6)
        // Sending tranches does not change the total that needs to move.
        assertEquals(derived.transferKsaToSy, d.transferKsaToSy, 1e-6)
    }

    @Test
    fun `editing a fixed amount ripples into the plug`() {
        val basePlug = amountOf("july-slry")
        val edited = seed.copy(rows = seed.rows.map {
            if (it.id == "cash-riyadh") it.copy(amount = it.amount + 7_500.0) else it
        })
        val d = LedgerCalculator.derive(edited)
        assertEquals(basePlug - 7_500.0, d.rows.first { it.row.id == "july-slry" }.amount, 1e-6)
        assertEquals(edited.target, d.grandTotal, 1e-6)
    }

    @Test
    fun `editing xauUsd path recomputes gold rows and plug`() {
        val edited = seed.copy(xauUsd = 4000.0, pricePerGramOverride = null)
        val d = LedgerCalculator.derive(edited)
        val price = 4000.0 / 31.1 * 3.75
        assertEquals(100.0 * price, d.rows.first { it.row.id == "gold-riyadh" }.amount, 1e-6)
        assertEquals(edited.target, d.grandTotal, 1e-6)
    }

    @Test
    fun `over-funding drives the plug negative and is flagged`() {
        val edited = seed.copy(rows = seed.rows.map {
            if (it.id == "cash-usd-ksa") it.copy(amount = 200_000.0) else it
        })
        val d = LedgerCalculator.derive(edited)
        val plug = d.rows.first { it.row.mode == Mode.PLUG }.amount
        assertTrue(plug < 0)
        assertEquals(-plug, d.overFundedBy, 1e-6)
        assertEquals(edited.target, d.grandTotal, 1e-6)
    }

    @Test
    fun `percentages are relative to target`() {
        val cashRiyadh = derived.rows.first { it.row.id == "cash-riyadh" }
        assertEquals(21_000.0 / 277_500.0 * 100, cashRiyadh.percentOfTarget, 1e-9)
    }

    @Test
    fun `live gold feed and ledger use the same conversion formula`() {
        // The Gold page pushes USD/oz into the ledger; both sides must derive the
        // identical SAR/g from it (oz / 31.1 * 3.75).
        val oz = 4176.0
        val fromFeed = com.fundingledger.gold.GoldConfig.sarPerGram(oz)
        val fromLedger = LedgerCalculator.pricePerGram(seed.copy(xauUsd = oz, pricePerGramOverride = null))
        assertEquals(fromFeed, fromLedger, 1e-9)
    }

    @Test
    fun `syncing a live rate updates gold rows and plug through the ledger`() {
        // Simulates GoldViewModel delivering a fresh oz price into the ledger state.
        val liveOz = 4250.0
        val synced = seed.copy(xauUsd = liveOz, pricePerGramOverride = null)
        val d = LedgerCalculator.derive(synced)
        val price = com.fundingledger.gold.GoldConfig.sarPerGram(liveOz)
        assertEquals(100.0 * price, d.rows.first { it.row.id == "gold-riyadh" }.amount, 1e-6)
        assertEquals(synced.target, d.grandTotal, 1e-6)
    }

    @Test
    fun `ledger without a plug reports a funding gap`() {
        val noPlug = Ledger(
            target = 100_000.0,
            xauUsd = 4000.0,
            rows = listOf(Row("a", "A", Category.GREEN, Mode.FIXED, amount = 60_000.0)),
        )
        val d = LedgerCalculator.derive(noPlug)
        assertEquals(40_000.0, d.fundingGap, 1e-6)
        assertEquals(0.0, d.overFundedBy, 1e-6)
    }
}
