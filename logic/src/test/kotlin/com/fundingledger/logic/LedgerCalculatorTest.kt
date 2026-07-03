package com.fundingledger.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerCalculatorTest {

    private fun assertNear(expected: Double, actual: Double, eps: Double = 0.01) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= eps,
            "expected $expected but was $actual (diff ${kotlin.math.abs(expected - actual)})"
        )
    }

    @Test
    fun `price per gram uses manual override when present`() {
        val snapshot = LedgerCalculator.snapshot(LedgerState.seed())
        assertNear(504.67, snapshot.pricePerGram)
    }

    @Test
    fun `price per gram derives from xauUsd when no override is set`() {
        val state = LedgerState.seed().copy(pricePerGramOverride = null, xauUsd = 4187.0)
        val snapshot = LedgerCalculator.snapshot(state)
        val expected = 4187.0 / 31.1 * 3.75
        assertNear(expected, snapshot.pricePerGram, eps = 0.001)
    }

    @Test
    fun `gold row amounts derive from grams times price per gram`() {
        val snapshot = LedgerCalculator.snapshot(LedgerState.seed())
        val goldRiyadh = snapshot.rows.first { it.row.id == "new_gold_riyadh" }
        val goldSy = snapshot.rows.first { it.row.id == "new_gold_sy" }

        assertNear(50_467.0, goldRiyadh.amount)
        assertNear(47_085.71, goldSy.amount)
    }

    @Test
    fun `plug absorbs the difference so grand total always equals target`() {
        val state = LedgerState.seed()
        val snapshot = LedgerCalculator.snapshot(state)

        assertNear(state.target, snapshot.grandTotal)
        assertNear(0.0, snapshot.fundingGap)
        assertTrue(snapshot.isBalanced)

        val plugRow = snapshot.rows.first { it.row.mode == Mode.PLUG }
        assertNear(10_697.29, plugRow.amount, eps = 0.5)
    }

    @Test
    fun `only one plug row exists in the seed ledger`() {
        val plugRows = LedgerState.seed().rows.filter { it.mode == Mode.PLUG }
        assertEquals(1, plugRows.size)
    }

    @Test
    fun `subtotals split rows by category`() {
        val snapshot = LedgerCalculator.snapshot(LedgerState.seed())
        assertNear(119_250.0, snapshot.greenSubtotal)
        assertNear(158_250.0, snapshot.redSubtotal, eps = 0.5)
        assertNear(snapshot.greenSubtotal + snapshot.redSubtotal, snapshot.grandTotal)
    }

    @Test
    fun `transfer total sums only rows flagged inTransfer, including the plug`() {
        val snapshot = LedgerCalculator.snapshot(LedgerState.seed())
        assertNear(204_164.29, snapshot.transferTotal, eps = 0.5)
    }

    @Test
    fun `editing xauUsd recomputes gold rows and the plug together`() {
        val state = LedgerState.seed().copy(pricePerGramOverride = null, xauUsd = 5000.0)
        val snapshot = LedgerCalculator.snapshot(state)
        val expectedPpg = 5000.0 / 31.1 * 3.75

        val goldRiyadh = snapshot.rows.first { it.row.id == "new_gold_riyadh" }
        assertNear(100.0 * expectedPpg, goldRiyadh.amount)
        assertNear(state.target, snapshot.grandTotal)
    }

    @Test
    fun `editing grams on a gold row recomputes its amount and the plug`() {
        val state = LedgerState.seed().let { s ->
            s.copy(rows = s.rows.map { if (it.id == "new_gold_riyadh") it.copy(grams = 50.0) else it })
        }
        val snapshot = LedgerCalculator.snapshot(state)
        val goldRiyadh = snapshot.rows.first { it.row.id == "new_gold_riyadh" }

        assertNear(50.0 * 504.67, goldRiyadh.amount)
        assertNear(state.target, snapshot.grandTotal)
    }

    @Test
    fun `over-funded ledger yields a negative plug instead of crashing`() {
        val state = LedgerState.seed().copy(target = 100_000.0)
        val snapshot = LedgerCalculator.snapshot(state)
        val plugRow = snapshot.rows.first { it.row.mode == Mode.PLUG }

        assertTrue(plugRow.amount < 0)
        assertTrue(snapshot.isOverfunded)
        assertNear(state.target, snapshot.grandTotal)
    }

    @Test
    fun `percent contribution is amount over target times 100`() {
        val snapshot = LedgerCalculator.snapshot(LedgerState.seed())
        val cashRiyadh = snapshot.rows.first { it.row.id == "cash_riyadh" }
        assertNear(33_000.0 / 277_500.0 * 100.0, cashRiyadh.percent, eps = 0.05)
    }
}
