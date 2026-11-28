package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LoansTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun threeThousandInThree() {
        val s = LoanSchedule.generate(3000.0, 3, d(2026, 12, 5))
        assertEquals(listOf(d(2026, 12, 5), d(2027, 1, 5), d(2027, 2, 5)), s.map { it.first })
        assertEquals(listOf(1000.0, 1000.0, 1000.0), s.map { it.second })
    }

    @Test fun remainderGoesOnTheLast() {
        val s = LoanSchedule.generate(1000.0, 3, d(2026, 12, 1))
        assertEquals(listOf(333.33, 333.33, 333.34), s.map { it.second })
        assertEquals(1000.0, s.sumOf { it.second }, 0.0001)
    }

    @Test fun monthEndClamping() {
        val s = LoanSchedule.generate(900.0, 3, d(2026, 1, 31))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31)), s.map { it.first })
    }

    @Test fun leapYear() {
        val s = LoanSchedule.generate(200.0, 2, d(2027, 12, 31)) // 31 Dec -> 31 Jan 2028
        assertEquals(d(2028, 1, 31), s[1].first)
        val t = LoanSchedule.generate(300.0, 2, d(2028, 1, 30))
        assertEquals(d(2028, 2, 29), t[1].first)
    }

    @Test fun lumpIsOneInstalment() {
        val s = LoanSchedule.generate(500.0, 1, d(2026, 12, 10))
        assertEquals(1, s.size)
        assertEquals(500.0, s[0].second, 0.0)
    }

    @Test fun previewBorrowed() {
        val sched = LoanSchedule.generate(3000.0, 3, d(2026, 12, 5))
        val p = LoanSchedule.preview(false, 3000.0, d(2026, 11, 20), sched)
        assertEquals(LoanMonthLine(YearMonth.of(2026, 11), 3000.0, 0.0), p[0])
        assertEquals(LoanMonthLine(YearMonth.of(2026, 12), 0.0, 1000.0), p[1])
        assertEquals(4, p.size)
    }

    @Test fun previewNotRecordedHasNoIncome() {
        val p = LoanSchedule.preview(false, 3000.0, d(2026, 11, 20), LoanSchedule.generate(3000.0, 3, d(2026, 12, 5)), recordedIncome = false)
        assertEquals(3, p.size)
        assertTrue(p.all { it.income == 0.0 })
    }

    @Test fun previewLentIsTheMirror() {
        val p = LoanSchedule.preview(true, 600.0, d(2026, 11, 20), LoanSchedule.generate(600.0, 2, d(2026, 12, 5)))
        // Lending is an expense in its month (no commitment); repayments are money coming in.
        assertTrue(p.all { it.commitment == 0.0 })
        assertEquals(300.0, p.first { it.month == YearMonth.of(2026, 12) }.income, 0.0)
    }

    @Test fun paymentGoesToOldestFirstAndCarries() {
        val i = listOf(Instalment(1, d(2026, 12, 5), 1000.0), Instalment(2, d(2027, 1, 5), 1000.0), Instalment(3, d(2027, 2, 5), 1000.0))
        val (parts, left) = LoanSchedule.allocate(i, 1500.0)
        assertEquals(listOf(1L to 1000.0, 2L to 500.0), parts)
        assertEquals(0.0, left, 0.0)
        val (_, over) = LoanSchedule.allocate(i, 3500.0)
        assertEquals(500.0, over, 0.0)
    }

    @Test fun undoTakesFromTheLatestPaid() {
        val i = listOf(Instalment(1, d(2026, 12, 5), 1000.0, paid = 1000.0), Instalment(2, d(2027, 1, 5), 1000.0, paid = 500.0))
        assertEquals(listOf(2L to 500.0, 1L to 200.0), LoanSchedule.unallocate(i, 700.0))
    }

    @Test fun nextSkipsPaid() {
        val i = listOf(Instalment(1, d(2026, 12, 5), 1000.0, paid = 1000.0), Instalment(2, d(2027, 1, 5), 1000.0, paid = 200.0))
        assertEquals(2L, LoanSchedule.next(i)?.id)
        assertEquals(800.0, LoanSchedule.next(i)!!.remaining, 0.0)
    }

    @Test fun regenerateKeepsPaidOnes() {
        val paid = listOf(Instalment(1, d(2026, 12, 5), 1000.0, paid = 1000.0))
        val r = LoanSchedule.regenerate(paid, 4000.0, 3, d(2027, 1, 5))
        assertEquals(listOf(1000.0, 1000.0, 1000.0), r.map { it.second })
        assertTrue(LoanSchedule.regenerate(paid, 1000.0, 3, d(2027, 1, 5)).isEmpty())
    }

    @Test fun legacyLoan() {
        assertEquals(d(2026, 12, 1) to 700.0, LoanSchedule.legacy(700.0, d(2026, 12, 1).toEpochDay()))
        assertNull(LoanSchedule.legacy(700.0, null))
        assertNull(LoanSchedule.legacy(0.0, d(2026, 12, 1).toEpochDay()))
    }

    // Budget: borrowing in November raises November income; each instalment lowers that month's daily allowance.
    private fun allowance(cycle: Cycle, today: LocalDate, income: Double, commitments: Double) = Budget.compute(
        BudgetInput(cycle, today, incomeReceived = income, salaryExpected = 0.0, setAside = 0.0, spentBeforeToday = 0.0, spentToday = 0.0, commitments = commitments, everydaySpent = 0.0)
    )

    @Test fun borrowingRaisesIncomeAndInstalmentLowersAllowance() {
        val nov = CycleMath.cycleFor(d(2026, 11, 20), 1)
        val dec = CycleMath.cycleFor(d(2026, 12, 1), 1)
        val novWithout = allowance(nov, d(2026, 11, 20), 5000.0, 0.0)
        val novWith = allowance(nov, d(2026, 11, 20), 8000.0, 0.0)
        assertEquals(3000.0, novWith.budget - novWithout.budget, 0.0)
        val decWithout = allowance(dec, d(2026, 12, 1), 6000.0, 0.0)
        val decWith = allowance(dec, d(2026, 12, 1), 6000.0, 1000.0)
        assertEquals(1000.0, decWithout.dailyAllowance * 30 - decWith.dailyAllowance * 30, 0.001)
    }

    @Test fun payingEarlyReducesTheCommitment() {
        val i = listOf(Instalment(1, d(2026, 12, 5), 1000.0), Instalment(2, d(2027, 1, 5), 1000.0))
        val before = LoanSchedule.openItems(i).sumOf { it.remaining }
        val (parts, _) = LoanSchedule.allocate(i, 1000.0)
        val after = LoanSchedule.openItems(i.map { x -> x.copy(paid = x.paid + (parts.firstOrNull { it.first == x.id }?.second ?: 0.0)) }).sumOf { it.remaining }
        assertEquals(1000.0, before - after, 0.0)
    }

    // Sync decision: the three branches of the brief.
    private fun facts(cloud: Boolean = true, empty: Boolean = false, before: Boolean = true, same: Boolean = false, lc: Boolean = false, cc: Boolean = false, la: Long = 0, ca: Long = 0, ans: Side? = null) =
        SyncFacts(cloud, empty, before, same, lc, cc, la, ca, ans)

    @Test fun syncSilentCases() {
        assertEquals(SyncAction.Upload, SyncDecision.decide(facts(cloud = false, before = false)))
        assertEquals(SyncAction.Nothing, SyncDecision.decide(facts(cloud = false, empty = true, before = false)))
        assertEquals(SyncAction.Download, SyncDecision.decide(facts(before = false, empty = true)))
        assertEquals(SyncAction.MarkSynced, SyncDecision.decide(facts(before = false, same = true)))
        assertEquals(SyncAction.Upload, SyncDecision.decide(facts(lc = true)))
        assertEquals(SyncAction.Download, SyncDecision.decide(facts(cc = true)))
        assertEquals(SyncAction.Nothing, SyncDecision.decide(facts()))
    }

    @Test fun syncAsksOnlyOnRealConflict() {
        assertEquals(SyncAction.Ask(Side.LOCAL), SyncDecision.decide(facts(before = false, la = 20, ca = 10)))
        assertEquals(SyncAction.Ask(Side.CLOUD), SyncDecision.decide(facts(lc = true, cc = true, la = 10, ca = 20)))
        // Once answered for the same pair, never asked again.
        assertEquals(SyncAction.Download, SyncDecision.decide(facts(lc = true, cc = true, la = 20, ca = 10, ans = Side.CLOUD)))
        assertEquals(SyncAction.Upload, SyncDecision.decide(facts(before = false, ans = Side.LOCAL)))
    }
}
