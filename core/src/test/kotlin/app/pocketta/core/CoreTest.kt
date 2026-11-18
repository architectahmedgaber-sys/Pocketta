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
}
