package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreTest {
    @Test fun quickText() {
        assertEquals(Quick(60.0, "koshary"), QuickText.parse("koshary 60"))
        assertEquals(Quick(85.0, "uber"), QuickText.parse("85 uber"))
        assertEquals(Quick(60.0, "كشري"), QuickText.parse("كشري ٦٠"))
        assertEquals(Quick(1500.0, "rent"), QuickText.parse("1.5k rent"))
        assertEquals(Quick(60.0, "kebab"), QuickText.parse("60 kebab"))
        assertEquals(Quick(1500.0, "groceries"), QuickText.parse("groceries 1,500 EGP"))
        assertEquals(Quick(85.0, "taxi", 1), QuickText.parse("yesterday taxi 85"))
        assertEquals(Quick(85.0, "تاكسي", 1), QuickText.parse("امبارح تاكسي ٨٥"))
        assertEquals(Quick(85.0, "غدا"), QuickText.parse("غدا 85"))
        assertEquals(Quick(200.0, "دجاج"), QuickText.parse("دجاج 200"))
        assertEquals(Quick(200.0, "فراخ"), QuickText.parse("فراخ 200 ج"))
        assertNull(QuickText.parse("coffee").amount)
    }

    @Test fun recurrenceMonthlyKeepsAnchor() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = Recurrence.next(jan31, Frequency.MONTHLY, 0)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(feb, Frequency.MONTHLY, 0, 31))
        assertEquals(LocalDate.of(2027, 1, 31), Recurrence.next(jan31, Frequency.ANNUAL, 0))
        assertEquals(LocalDate.of(2026, 2, 10), Recurrence.next(jan31, Frequency.EVERY_N_DAYS, 10))
    }

    @Test fun occurrencesAndShares() {
        val occ = Recurrence.occurrences(LocalDate.of(2026, 11, 2), Frequency.WEEKLY, 0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
        assertEquals(5, occ.size)
        assertEquals(100.0, Recurrence.monthlyShare(1200.0, Frequency.ANNUAL, 0), 0.001)
        assertTrue(Recurrence.isSpread(Frequency.ANNUAL, 0))
        assertTrue(!Recurrence.isSpread(Frequency.MONTHLY, 0))
    }

    @Test fun installments() {
        val p = InstallmentPlan(1250.0, 12, 4, LocalDate.of(2026, 8, 5))
        assertEquals(LocalDate.of(2026, 12, 5), p.nextDue)
        assertEquals(LocalDate.of(2027, 7, 5), p.endDate)
        assertEquals(10000.0, p.remainingAmount, 0.001)
        assertEquals(listOf(LocalDate.of(2026, 12, 5)), p.unpaidDueIn(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)))
        val c = Circle(2000.0, 10, 7, 4, LocalDate.of(2026, 7, 25))
        assertEquals(20000.0, c.payout, 0.001)
        assertEquals(LocalDate.of(2027, 1, 25), c.payoutDate)
    }

    @Test fun dailyAllowance() {
        val cycle = CycleMath.cycleFor(LocalDate.of(2026, 11, 18), 1)
        val r = Budget.compute(
            BudgetInput(
                cycle = cycle, today = LocalDate.of(2026, 11, 18),
                incomeReceived = 12000.0, salaryExpected = 0.0, setAside = 0.0,
                spentBeforeToday = 6000.0, spentToday = 100.0, commitments = 1300.0, everydaySpent = 5000.0,
            )
        )
        // 13 days left incl. today: (12000 - 6000 - 1300) / 13
        assertEquals(13, r.daysLeft)
        assertEquals(4700.0 / 13, r.dailyAllowance, 0.001)
        assertEquals(4700.0 / 13 - 100, r.leftToday, 0.001)
        assertEquals(12000.0 - 6100 - 1300, r.left, 0.001)
    }

    @Test fun money() {
        assertEquals("1,234", Money.fmt(1234.0))
        assertEquals("12.5", Money.fmt(12.5))
        assertEquals("−300", Money.signed(-300.0))
    }

    @Test fun goldProfit() {
        val buys = listOf(GoldBuy("21", 10.0, 3000.0), GoldBuy("24", 5.0, 3500.0))
        val round = Gold.parse(Gold.format(buys))
        assertEquals(buys, round)
        val s = Gold.summary(buys, mapOf("21" to 3200.0, "24" to 3400.0))!!
        assertEquals(15.0, s.grams, 0.0001)
        assertEquals(47500.0, s.cost, 0.0001)
        assertEquals(49000.0, s.value, 0.0001)
        assertEquals(1500.0, s.profit, 0.0001)
        assertEquals(-500.0, Gold.profit(buys[1], mapOf("24" to 3400.0))!!, 0.0001)
        assertNull(Gold.profit(buys[0], emptyMap()))
        assertEquals(0, Gold.parse("junk;21,x,3").size)
    }

    @Test fun savingsSuggestion() {
        assertEquals(800.0, Savings.suggestMove(800.7, 10000.0, 2000.0), 0.0)
        assertEquals(300.0, Savings.suggestMove(800.0, 10000.0, 9700.0), 0.0)
        assertEquals(0.0, Savings.suggestMove(30.0, 10000.0, 0.0), 0.0)
        assertEquals(0.0, Savings.suggestMove(-5.0, 10000.0, 0.0), 0.0)
        assertEquals(0.0, Savings.suggestMove(500.0, 0.0, 0.0), 0.0)
        assertEquals(0.0, Savings.suggestMove(500.0, 1000.0, 1000.0), 0.0)
    }

    @Test fun goalMonths() {
        assertEquals(10, Planning.monthsToGoal(10000.0, 0.0, 1000.0))
        assertEquals(4, Planning.monthsToGoal(10000.0, 6999.0, 1000.0))
        assertEquals(0, Planning.monthsToGoal(10000.0, 10000.0, 0.0))
        assertNull(Planning.monthsToGoal(10000.0, 0.0, 0.0))
        assertEquals(LocalDate.of(2027, 2, 18), Planning.goalDate(LocalDate.of(2026, 11, 18), 3))
    }

    @Test fun canAfford() {
        val ok = Planning.canAfford(100.0, 300.0, 5000.0, 10)
        assertEquals(Planning.Verdict.YES, ok.verdict)
        assertEquals(290.0, ok.dailyAfter, 0.001)
        val tight = Planning.canAfford(2000.0, 300.0, 3000.0, 10)
        assertEquals(Planning.Verdict.TIGHT, tight.verdict)
        val no = Planning.canAfford(3500.0, 300.0, 3000.0, 10, goalAmount = 20000.0, goalSaved = 0.0, savingsPerMonth = 1000.0)
        assertEquals(Planning.Verdict.NO, no.verdict)
        assertEquals(500.0, no.shortfall, 0.001)
        assertEquals(1, no.goalDelayMonths)
        assertEquals(0, ok.goalDelayMonths)
    }

    @Test fun simulateCut() {
        val s = Planning.simulateCut(2000.0, 20, 12000.0, 0.0, 1000.0)
        assertEquals(400.0, s.extraPerMonth, 0.001)
        assertEquals(12, s.monthsBefore)
        assertEquals(9, s.monthsAfter)
        assertEquals(3, s.monthsSooner)
        assertEquals(0, Planning.simulateCut(2000.0, 20, 0.0, 0.0, 1000.0).monthsSooner)
    }

    private fun budgetOf(left: Double, forecastDiff: Double, daysLeft: Int) =
        BudgetResult(budget = 10000.0, spent = 0.0, committed = 0.0, left = left, daysLeft = daysLeft, dailyAllowance = 0.0, leftToday = 0.0, forecastSpend = 0.0, forecastDiff = forecastDiff)

    @Test fun emergency() {
        val cycle = Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
        val today = LocalDate.of(2026, 11, 10)
        assertNull(Planning.emergency(today, cycle, budgetOf(5000.0, 100.0, 21), 3000.0, 10, emptyList(), emptyList()))
        val e = Planning.emergency(
            today, cycle, budgetOf(1500.0, -2000.0, 21), 3000.0, 10,
            listOf(Planning.Postponable("Netflix", 200.0, LocalDate.of(2026, 11, 20)), Planning.Postponable("Old", 50.0, LocalDate.of(2026, 11, 5))),
            listOf(Planning.CategorySpend("Food", 1500.0), Planning.CategorySpend("Bills", 900.0), Planning.CategorySpend("Fun", 600.0)),
        )!!
        assertEquals(2000.0, e.shortfall, 0.001)
        assertEquals(LocalDate.of(2026, 11, 15), e.runOutDate)
        assertEquals(listOf("Netflix"), e.postpone.map { it.name })
        assertTrue(e.cuts.none { it.name == "Bills" })
        assertTrue(e.cuts.first().name == "Food")
    }

    @Test fun priceRises() {
        val r = Planning.priceRises(
            listOf(Planning.CategoryTrend("Food", 1500.0, 3000.0), Planning.CategoryTrend("Fun", 100.0, 500.0), Planning.CategoryTrend("Tiny", 90.0, 50.0)),
            dayIndex = 10, cycleLength = 30,
        )
        assertEquals(listOf("Food"), r.map { it.name })
        assertEquals(50, r.first().percent)
    }

    @Test fun weekSentences() {
        val (a, b) = Planning.weekSentences(1200.0, -10, "Food", 5, "EGP")
        assertEquals("You spent 1,200 EGP this week, 10% less than last week.", a)
        assertTrue(b.contains("Food"))
    }

    @Test fun circleStatus() {
        val c = Circle(share = 500.0, months = 10, myTurn = 4, paid = 2, firstDue = LocalDate.of(2026, 10, 5))
        val st = Circles.status(c, false, LocalDate.of(2026, 11, 18))
        assertEquals(8, st.installmentsLeft)
        assertEquals(5000.0, st.payout, 0.001)
        assertEquals(LocalDate.of(2027, 1, 5), st.payoutDate)
        assertEquals(48, st.daysToTurn)
        assertTrue(!st.collectNow && !st.finished)
        assertTrue(Circles.status(c, false, LocalDate.of(2027, 1, 5)).collectNow)
        val done = Circle(500.0, 10, 4, 10, LocalDate.of(2026, 10, 5))
        assertTrue(Circles.status(done, true, LocalDate.of(2027, 9, 1)).finished)
    }
}
