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

    @Test fun wealthLog() {
        val a = WealthLog.record(emptyList(), 100, 5000.0)
        val b = WealthLog.record(a, 130, 6500.0)
        val c = WealthLog.record(b, 130, 6600.0)
        assertEquals(2, c.size)
        assertEquals(6600.0, c.last().total, 0.001)
        assertEquals(c, WealthLog.parse(WealthLog.format(c)))
        assertEquals(1600.0, WealthLog.changeSince(c, 130, 30)!!, 0.001)
        assertNull(WealthLog.changeSince(c, 130, 90))
        assertEquals(2000.0, Planning.inDollars(100000.0, 50.0)!!, 0.001)
        assertNull(Planning.inDollars(1000.0, null))
    }

    @Test fun piggyBankMonthByMonth() {
        val d = { y: Int, m: Int, day: Int -> java.time.LocalDate.of(y, m, day).toEpochDay() }
        var list = emptyList<PiggyEntry>()
        list = Piggy.add(list, d(2026, 9, 10), 500.0)
        list = Piggy.add(list, d(2026, 9, 25), 250.0)
        list = Piggy.add(list, d(2026, 11, 3), 1000.0)
        list = Piggy.add(list, d(2026, 11, 5), -200.0)
        val months = Piggy.months(list, java.time.LocalDate.of(2026, 11, 18))
        assertEquals(3, months.size)
        assertEquals(750.0, months[0].saved, 0.001)
        assertEquals(0.0, months[1].saved, 0.001)
        assertEquals(750.0, months[1].total, 0.001)
        assertEquals(800.0, months[2].saved, 0.001)
        assertEquals(1550.0, months[2].total, 0.001)
        assertEquals(1550.0, Piggy.total(list), 0.001)
        assertEquals(list, Piggy.parse(Piggy.format(list)))
        assertEquals(emptyList<PiggyEntry>(), Piggy.parse("junk;5=;=3;7=0"))
        assertEquals(1, Piggy.months(Piggy.add(emptyList(), d(2026, 11, 1), 10.0), java.time.LocalDate.of(2026, 11, 18)).size)
    }

    @Test fun piggyTargetStreakAndCover() {
        val d = { y: Int, m: Int, day: Int -> java.time.LocalDate.of(y, m, day).toEpochDay() }
        var list = emptyList<PiggyEntry>()
        list = Piggy.add(list, d(2026, 8, 5), 1000.0)
        list = Piggy.add(list, d(2026, 9, 5), 1500.0)
        list = Piggy.add(list, d(2026, 10, 5), 1200.0)
        list = Piggy.add(list, d(2026, 11, 2), 300.0)
        val today = java.time.LocalDate.of(2026, 11, 18)
        val months = Piggy.months(list, today)
        assertEquals(2, Piggy.streak(months, 1200.0, today))
        assertEquals(3, Piggy.streak(months, 900.0, today))
        assertEquals(0, Piggy.streak(months, 1300.0, today))
        assertEquals(0, Piggy.streak(months, 0.0, today))
        assertEquals(300.0, Piggy.thisMonth(months, today), 0.001)
        assertEquals(3.0, Piggy.coverMonths(9000.0, 3000.0)!!, 0.001)
        assertEquals(1.7, Piggy.coverMonths(5000.0, 3000.0)!!, 0.001)
        assertNull(Piggy.coverMonths(5000.0, 0.0))
    }

    @Test fun categoryLimitAlertsOncePerCycle() {
        assertEquals(false, LimitAlerts.isOver(3000.0, 3000.0))
        assertEquals(true, LimitAlerts.isOver(3000.5, 3000.0))
        assertEquals(false, LimitAlerts.isOver(5000.0, 0.0))
        assertEquals(listOf(1L, 2L), LimitAlerts.fresh(100, "", listOf(1, 2)))
        val saved = LimitAlerts.format(100, setOf(1L))
        assertEquals(listOf(2L), LimitAlerts.fresh(100, saved, listOf(1, 2)))
        assertEquals(listOf(1L), LimitAlerts.fresh(130, saved, listOf(1)))
    }

    @Test fun bankSmsAndReceipts() {
        val a = SmsParser.parse("Your card ending 1234 was used for purchase of EGP 1,250.50 at CARREFOUR MAADI on 18/11. Balance EGP 9,000")!!
        assertEquals(1250.5, a.amount, 0.001)
        assertEquals("EGP", a.currency)
        assertEquals("CARREFOUR MAADI", a.merchant)
        val b = SmsParser.parse("تم خصم ٣٥٠ جنيه من بطاقتك عند صيدلية العزبي")!!
        assertEquals(350.0, b.amount, 0.001)
        assertEquals("EGP", b.currency)
        assertEquals(true, b.merchant.startsWith("صيدلية"))
        assertNull(SmsParser.parse("Your OTP is 123456. Do not share. Amount EGP 500 purchase"))
        assertNull(SmsParser.parse("EGP 5,000 credited to your account"))
        assertNull(SmsParser.parse("Hello, welcome to the bank"))
        assertEquals(236.5, ReceiptParser.total("Cafe\nSubtotal 210.00\nVAT 14% 26.50\nTOTAL 236.50\nCash 300.00")!!, 0.001)
        assertEquals(412.0, ReceiptParser.total("سوبر ماركت\nالاجمالي: ٤١٢٫٠٠\nشكرا")!!, 0.001)
        assertEquals(99.9, ReceiptParser.total("Shop\nItem 20.50\nItem 99.90")!!, 0.001)
        assertNull(ReceiptParser.total("hello"))
    }

    @Test fun extraGoals() {
        val list = listOf(
            SavingsGoal(1, "Car", 250000.0, 22000.0, 3000.0),
            SavingsGoal(2, "Laptop", 30000.0, 24000.0, 2000.0),
            SavingsGoal(3, "Umra", 60000.0, 18500.0, 2500.0),
            SavingsGoal(4, "Done", 100.0, 100.0, 0.0),
            SavingsGoal(5, "No date", 100.0, 0.0, 0.0),
        )
        assertEquals(listOf(2L, 3L, 1L, 5L, 4L), GoalList.byFinish(list).map { it.id })
        assertEquals(3, list[1].monthsLeft)
        assertEquals(null, list[4].monthsLeft)
        assertEquals(0.8, list[1].fraction, 0.001)
        assertEquals(list, GoalList.parse(GoalList.format(list)))
        assertEquals(emptyList<SavingsGoal>(), GoalList.parse("junk\n1\ta\tx\t1\t1"))
        assertEquals(6L, GoalList.nextId(list))
        // 228000/3000 = 76 months, 228000/4000 = 57 months
        assertEquals(19, GoalList.monthsSoonerWith(250000.0, 22000.0, 3000.0, 1000.0))
        assertEquals(null, GoalList.monthsSoonerWith(100.0, 0.0, 0.0, 50.0))
    }

    @Test fun badges() {
        assertEquals("N", Badges.forName("Netflix Premium", CKind.SUB).letter)
        assertEquals("N", Badges.forName("نتفليكس", CKind.SUB).letter)
        assertEquals("S", Badges.forName("شاهد VIP", CKind.SUB).letter)
        assertEquals(0xFFD97757, Badges.forName("Claude Pro", CKind.SUB).color)
        assertEquals("WE", Badges.forName("WE internet", CKind.BILL).letter)
        assertEquals("WE", Badges.forName("باقة وي", CKind.BILL).letter)
        assertNull(Badges.forName("Water bill", CKind.BILL).letter)
        assertEquals("💧", Badges.forName("Water bill", CKind.BILL).emoji)
        assertEquals("⚡", Badges.forName("فاتورة الكهرباء", CKind.BILL).emoji)
        assertEquals("🔑", Badges.forName("Rent", CKind.BILL).emoji)
        assertEquals("💰", Badges.forName("Salary", CKind.INCOME).emoji)
        assertEquals("📆", Badges.forName("Fridge", CKind.INSTALLMENT).emoji)
        assertEquals("🔁", Badges.forName("Mystery app", CKind.SUB).emoji)
    }

    @Test fun commitmentsTotals() {
        val d = LocalDate.of(2026, 11, 20)
        val items = listOf(
            CItem(CKind.BILL, 1, "Rent", "", 5000.0, d, paid = false),
            CItem(CKind.SUB, 2, "Netflix", "", 200.0, d.plusDays(3), paid = false),
            CItem(CKind.CARD, 3, "CIB card", "", 1000.0, d.minusDays(2), paid = false),
            CItem(CKind.BILL, 4, "Internet", "", 300.0, d.minusDays(5), paid = true),
            CItem(CKind.INCOME, 0, "Salary", "", 20000.0, d.plusDays(5), paid = false, incoming = true),
        )
        assertEquals(6200.0, Commitments.leftToPay(items), 0.001)
        assertEquals(3, Commitments.leftCount(items))
        assertEquals(listOf("CIB card"), Commitments.late(items, d).map { it.name })
        assertEquals(listOf("Rent", "Netflix", "Salary"), Commitments.later(items, d).map { it.name })
        assertEquals(listOf("Internet"), Commitments.done(items).map { it.name })
        assertEquals(listOf("Salary"), items.filter { Commitments.matches(CFilter.INCOME, it) }.map { it.name })
        assertEquals(listOf("Rent", "Netflix", "Internet"), items.filter { Commitments.matches(CFilter.BILLS, it) }.map { it.name })
        assertEquals(listOf("CIB card"), items.filter { Commitments.matches(CFilter.CARDS, it) }.map { it.name })
    }
}
