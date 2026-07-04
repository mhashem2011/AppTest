package com.fundingledger

import com.fundingledger.domain.LedgerCalculator
import com.fundingledger.model.Category
import com.fundingledger.model.Ledger
import com.fundingledger.model.Mode
import com.fundingledger.model.Row
import com.fundingledger.model.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalculatorTest {

    private val seed = SeedData.ledger()
    private val derived = LedgerCalculator.derive(seed)

    private fun amountOf(id: String) = derived.rows.first { it.row.id == id }.amount

    @Test
    fun `price per gram uses the seeded override`() {
        assertEquals(504.67, derived.pricePerGram, 1e-9)
    }

    @Test
    fun `price per gram falls back to xauUsd formula when override cleared`() {
        val noOverride = seed.copy(pricePerGramOverride = null)
        // 4187 / 31.1 * 3.75
        assertEquals(504.8633, LedgerCalculator.pricePerGram(noOverride), 1e-3)
    }

    @Test
    fun `gold rows derive amount from grams times price`() {
        assertEquals(100.0 * 504.67, amountOf("gold-riyadh"), 1e-6)
        assertEquals(93.3 * 504.67, amountOf("gold-sy"), 1e-6)
    }

    @Test
    fun `plug absorbs the remainder so total equals target`() {
        // target 277,500 − (119,250 green + 5,000 + 50,467 + 47,085.711 + 45,000)
        assertEquals(10_697.289, amountOf("july-slry"), 1e-3)
        assertEquals(seed.target, derived.grandTotal, 1e-6)
        assertEquals(0.0, derived.fundingGap, 1e-6)
    }

    @Test
    fun `subtotals split by category`() {
        assertEquals(119_250.0, derived.greenSubtotal, 1e-6)
        assertEquals(seed.target - 119_250.0, derived.redSubtotal, 1e-6)
    }

    @Test
    fun `transfer sums only inTransfer rows`() {
        // 33,000 + 60,000 + 5,000 + 50,467 + 45,000 + 10,697.289
        assertEquals(204_164.289, derived.transferKsaToSy, 1e-3)
    }

    @Test
    fun `remaining transfer defaults to the full transfer total`() {
        assertEquals(204_164.289, derived.transferKsaToSy, 1e-3)
        assertEquals(0.0, derived.transfersMade, 1e-6)
        assertEquals(derived.transferKsaToSy, derived.remainingTransfer, 1e-6)
    }

    @Test
    fun `sent transfer tranches count down the remaining transfer`() {
        val edited = seed.copy(
            transfers = listOf(
                com.fundingledger.model.TransferEntry("t1", "Bank 1", 50_000.0),
                com.fundingledger.model.TransferEntry("t2", "Courier", 30_000.0),
            ),
        )
        val d = LedgerCalculator.derive(edited)
        assertEquals(80_000.0, d.transfersMade, 1e-6)
        assertEquals(204_164.289 - 80_000.0, d.remainingTransfer, 1e-3)
        // Sending tranches does not change the total that needs to move.
        assertEquals(204_164.289, d.transferKsaToSy, 1e-3)
    }

    @Test
    fun `editing a fixed amount ripples into the plug`() {
        val edited = seed.copy(rows = seed.rows.map {
            if (it.id == "cash-riyadh") it.copy(amount = 40_000.0) else it
        })
        val d = LedgerCalculator.derive(edited)
        assertEquals(10_697.289 - 7_000.0, d.rows.first { it.row.id == "july-slry" }.amount, 1e-3)
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
        assertEquals(33_000.0 / 277_500.0 * 100, cashRiyadh.percentOfTarget, 1e-9)
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
