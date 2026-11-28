package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The numeric vectors of the v2 spec (sections 9 and 10), run against the code that is really in the app. */
class V2VectorsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun amounts(total: Double, n: Int, first: LocalDate) = LoanSchedule.generate(total, n, first).map { it.second }
    private fun dates(n: Int, first: LocalDate) = LoanSchedule.generate(n * 100.0, n, first).map { it.first }

    @Test fun splitPutsTheRemainderOnTheLast() {
        assertEquals(listOf(1000.0, 1000.0, 1000.0), amounts(3000.0, 3, d(2026, 12, 5)))
        assertEquals(listOf(333.33, 333.33, 333.34), amounts(1000.0, 3, d(2026, 12, 5)))
        assertEquals(listOf(0.0, 0.0, 0.01), amounts(0.01, 3, d(2026, 12, 5)))
        assertEquals(listOf(0.0, 0.0), amounts(0.0, 2, d(2026, 12, 5)))
    }

    @Test fun sumsAreExact() {
        for ((t, n) in listOf(1000.0 to 3, 99.99 to 7, 0.01 to 3, 12345.67 to 12)) {
            assertEquals(t, amounts(t, n, d(2026, 1, 31)).sum(), 0.000001)
        }
    }

    @Test fun dueDates() {
        assertEquals(listOf(d(2026, 12, 5), d(2027, 1, 5), d(2027, 2, 5)), dates(3, d(2026, 12, 5)))
        assertEquals(listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31)), dates(3, d(2027, 1, 31)))
        assertEquals(listOf(d(2028, 1, 31), d(2028, 2, 29)), dates(2, d(2028, 1, 31)))
        assertEquals(listOf(d(2026, 11, 30), d(2026, 12, 30), d(2027, 1, 30)), dates(3, d(2026, 11, 30)))
        assertEquals(d(2027, 12, 20), dates(14, d(2026, 11, 20)).last())
    }

    private fun loan(s: String, kind: QuickKind, person: String, amount: Double) {
        val q = QuickIntentParser.parse(s)
        assertEquals(s, kind, q.kind); assertEquals(s, person, q.person); assertEquals(s, amount, q.amount)
    }

    @Test fun parseIntents() {
        loan("استلفت ٣٠٠٠ من محمد", QuickKind.BORROWED, "محمد", 3000.0)
        loan("استلفت من محمد 3000", QuickKind.BORROWED, "محمد", 3000.0)
        loan("borrowed 3000 from Sam", QuickKind.BORROWED, "Sam", 3000.0)
        loan("سلفت محمد ٥٠٠", QuickKind.LENT, "محمد", 500.0)
        loan("lent Sam 500", QuickKind.LENT, "Sam", 500.0)
        loan("lent 500 to Sam", QuickKind.LENT, "Sam", 500.0)
        loan("استلفت ٣٠٠٠", QuickKind.BORROWED, "", 3000.0)
        QuickIntentParser.parse("مرتب ٩٢٠٠").let { assertEquals(QuickKind.INCOME, it.kind); assertEquals(9200.0, it.amount!!, 0.0); assertEquals("مرتب", it.note) }
        QuickIntentParser.parse("كشري ٦٠").let { assertEquals(QuickKind.EXPENSE, it.kind); assertEquals(60.0, it.amount!!, 0.0); assertEquals("كشري", it.note) }
        QuickIntentParser.parse("امبارح تاكسي 85").let { assertEquals(1, it.daysAgo); assertEquals(85.0, it.amount!!, 0.0); assertEquals("تاكسي", it.note) }
        QuickIntentParser.parse("1.5k rent").let { assertEquals(1500.0, it.amount!!, 0.0); assertEquals("rent", it.note) }
        QuickIntentParser.parse("قهوة").let { assertEquals(QuickKind.EXPENSE, it.kind); assertEquals(null, it.amount); assertEquals("قهوة", it.note) }
        QuickIntentParser.parse("60 uber").let { assertEquals(60.0, it.amount!!, 0.0); assertEquals("uber", it.note) }
    }

    // ---- sync decision: the nine branches of the spec ----
    private fun f(cloud: Boolean = true, empty: Boolean = false, before: Boolean = true, same: Boolean = false, lc: Boolean = false, cc: Boolean = false) =
        SyncFacts(cloudExists = cloud, localEmpty = empty, syncedBefore = before, sameContent = same, localChanged = lc, cloudChanged = cc)

    @Test fun syncDecisionVectors() {
        assertEquals(SyncAction.Upload, SyncDecision.decide(f(cloud = false)))
        assertEquals(SyncAction.Nothing, SyncDecision.decide(f(cloud = false, empty = true)))
        assertEquals(SyncAction.Download, SyncDecision.decide(f(before = false, empty = true)))
        assertEquals(SyncAction.MarkSynced, SyncDecision.decide(f(before = false, same = true)))
        assertTrue(SyncDecision.decide(f(before = false)) is SyncAction.Ask)
        assertTrue(SyncDecision.decide(f(lc = true, cc = true)) is SyncAction.Ask)
        assertEquals(SyncAction.MarkSynced, SyncDecision.decide(f(lc = true, cc = true, same = true)))
        assertEquals(SyncAction.Upload, SyncDecision.decide(f(lc = true)))
        assertEquals(SyncAction.Download, SyncDecision.decide(f(cc = true)))
        assertEquals(SyncAction.Nothing, SyncDecision.decide(f()))
    }

    // ---- loans: whatever is paid, edited, deleted or undone, the instalments' paid sum equals what was repaid ----
    private fun rows() = listOf(Instalment(1, d(2026, 12, 10), 1000.0), Instalment(2, d(2026, 12, 20), 1000.0), Instalment(3, d(2026, 12, 30), 1000.0))

    /** Mirrors Repo: the amount that fits is what is counted as repaid. Returns the new (rows, repaid). */
    private fun pay(rs: List<Instalment>, repaid: Double, total: Double, delta: Double): Pair<List<Instalment>, Double> {
        val fit = if (delta > 0) minOf(delta, total - repaid) else -minOf(-delta, repaid)
        return LoanSchedule.applyDelta(rs, fit) to (repaid + fit)
    }

    @Test fun paidSumAlwaysEqualsRepaid() {
        var rs = rows(); var repaid = 0.0
        fun step(delta: Double) {
            val (a, b) = pay(rs, repaid, 3000.0, delta); rs = a; repaid = b
            assertEquals(repaid, rs.sumOf { it.paid }, 0.0001)
            assertTrue(rs.all { it.paid in -0.0001..it.amount + 0.0001 })
        }
        step(1500.0)      // pay
        step(700.0)       // pay more
        step(-300.0)      // edit a repayment down
        step(400.0)       // edit it up again
        step(-1000.0)     // delete a repayment
        step(5000.0)      // pay too much: only what is left counts
        step(-3000.0)     // undo everything
        step(-5.0)        // undo with nothing left
        assertTrue(rs.all { it.paid == 0.0 })
    }

    @Test fun payOldestFirstAndUndoLatestFirst() {
        val (r1, _) = pay(rows(), 0.0, 3000.0, 1500.0)
        assertEquals(listOf(1000.0, 500.0, 0.0), r1.map { it.paid })
        val (r2, _) = pay(r1, 1500.0, 3000.0, -700.0)
        assertEquals(listOf(800.0, 0.0, 0.0), r2.map { it.paid })
    }

    @Test fun editAfterPartPaymentKeepsPaidAndResplitsTheRest() {
        // 3 x 1000, 1000 paid; the loan becomes 4000: the two unpaid ones become 1500 each.
        val paid = listOf(Instalment(1, d(2026, 12, 10), 1000.0, paid = 1000.0))
        assertEquals(listOf(1500.0, 1500.0), LoanSchedule.regenerate(paid, 4000.0, 2, d(2026, 12, 20)).map { it.second })
        // Lowered to exactly what is paid: nothing left to schedule.
        assertTrue(LoanSchedule.regenerate(paid, 1000.0, 2, d(2026, 12, 20)).isEmpty())
    }
}
