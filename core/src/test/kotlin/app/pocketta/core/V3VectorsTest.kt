package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The numeric vectors of the v3 spec (section 10.2 of the brief), ported 1:1 from tests/web/extras.test.mjs. */
class V3VectorsTest {
    private fun e(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun ld(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test fun alertLevels() {
        assertEquals(listOf(0, 0, 80, 80, 100), listOf(0.0, 79.99, 80.0, 100.0, 100.01).map { LimitLevels.level(it, 100.0) })
        assertEquals(0, LimitLevels.level(500.0, 0.0))
        assertEquals(listOf(80, 0, 80), listOf(LimitLevels.level(2400.0, 3000.0), LimitLevels.level(2399.99, 3000.0), LimitLevels.level(0.8 * 3, 3.0)))
        assertEquals(listOf("60", "60.5", "60.25", "1200"), listOf(60.0, 60.5, 60.25, 1200.0).map { TxnFilter.amountText(it) })
    }

    @Test fun alertState() {
        assertEquals(LimitLevels.State(19700, mapOf(1L to 100, 2L to 100)), LimitLevels.parse("19700|1,2"))
        assertEquals(LimitLevels.State(0, emptyMap()), LimitLevels.parse(""))
        assertEquals(LimitLevels.State(19700, mapOf(3L to 80, 4L to 100)), LimitLevels.parse("19700|3:80,4:100"))
        var r = LimitLevels.fresh(19700, "", listOf(LimitLevels.Item(3, 85.0, 100.0), LimitLevels.Item(4, 10.0, 100.0)))
        assertEquals(listOf(LimitLevels.Fresh(3, 80)), r.fresh); assertEquals("19700|3:80", r.state)
        r = LimitLevels.fresh(19700, r.state, listOf(LimitLevels.Item(3, 90.0, 100.0)))
        assertEquals(emptyList<LimitLevels.Fresh>(), r.fresh)
        r = LimitLevels.fresh(19700, r.state, listOf(LimitLevels.Item(3, 101.0, 100.0)))
        assertEquals(listOf(LimitLevels.Fresh(3, 100)), r.fresh); assertEquals("19700|3:100", r.state)
        r = LimitLevels.fresh(19731, r.state, listOf(LimitLevels.Item(3, 85.0, 100.0)))
        assertEquals(listOf(LimitLevels.Fresh(3, 80)), r.fresh)
        r = LimitLevels.fresh(19700, "19700|7", listOf(LimitLevels.Item(7, 120.0, 100.0)))
        assertEquals(emptyList<LimitLevels.Fresh>(), r.fresh)
        assertEquals(
            LimitLevels.Summary(3, 1, 1, 1),
            LimitLevels.summary(listOf(LimitLevels.Item(1, 50.0, 100.0), LimitLevels.Item(2, 85.0, 100.0), LimitLevels.Item(3, 130.0, 100.0), LimitLevels.Item(4, 999.0, 0.0))),
        )
    }

    @Test fun dailyCap() {
        assertEquals(400.0, Forecast.dailyCap(4805.0, 13))
        assertEquals(500.0, Forecast.dailyCap(500.0, 1))
        assertEquals(500.0, Forecast.dailyCap(500.0, 2))
        assertEquals(0.0, Forecast.dailyCap(-5.0, 10))
    }

    @Test fun forecast() {
        assertEquals(9, Forecast.runOutIn(900.0, 100.0, 20))
        assertNull(Forecast.runOutIn(900.0, 100.0, 9))
        assertEquals(8, Forecast.runOutIn(899.0, 100.0, 20))
        assertEquals(0, Forecast.runOutIn(-5.0, 100.0, 20))
        assertNull(Forecast.runOutIn(800.0, 100.0, 9))
        assertEquals(7, Forecast.runOutIn(700.0, 100.0, 9))
        assertNull(Forecast.runOutIn(700.0, 100.0, 8))
        assertEquals(6, Forecast.runOutIn(600.0, 100.0, 8))
        assertNull(Forecast.runOutIn(500.0, 0.0, 20))
        assertEquals(
            listOf(Forecast.Status.OVER, Forecast.Status.TIGHT, Forecast.Status.TIGHT, Forecast.Status.OK),
            listOf(Forecast.status(-1.0, 1000.0), Forecast.status(0.0, 1000.0), Forecast.status(99.99, 1000.0), Forecast.status(100.0, 1000.0)),
        )
    }

    private fun bill(id: Long, active: Boolean, auto: Boolean, variable: Boolean, due: LocalDate, anchor: Int) =
        AutoPost.BillIn(id, active, auto, variable, due, Frequency.MONTHLY, 30, anchor)

    @Test fun autoPost() {
        val bills = listOf(
            bill(1, true, true, false, ld(2026, 11, 28), 28),
            bill(2, true, true, true, ld(2026, 11, 1), 1),
            bill(3, true, false, false, ld(2026, 11, 1), 1),
            bill(4, true, true, false, ld(2026, 12, 1), 1),
            bill(5, false, true, false, ld(2026, 11, 1), 1),
            bill(6, true, true, false, ld(2026, 9, 30), 30),
        )
        val plan = AutoPost.plan(bills, ld(2026, 11, 28))
        assertEquals(listOf("1@2026-11-28", "6@2026-09-30", "6@2026-10-30"), plan.map { "${it.billId}@${it.day}" })
        assertEquals(emptyList<AutoPost.Item>(), AutoPost.plan(bills, ld(2026, 11, 27)).filter { it.billId == 1L })
        assertEquals(12, AutoPost.plan(listOf(bill(9, true, true, false, ld(2020, 1, 1), 1)), ld(2026, 11, 28)).size)
    }

    @Test fun goals() {
        assertEquals(4, GoalNeed.monthsUntil(ld(2026, 11, 28), ld(2027, 3, 1)))
        assertEquals(1, GoalNeed.monthsUntil(ld(2026, 11, 28), ld(2026, 11, 30)))
        assertEquals(1, GoalNeed.monthsUntil(ld(2026, 11, 28), ld(2026, 1, 1)))
        val today = ld(2026, 11, 28)
        assertEquals(GoalNeed.Need(GoalNeed.Status.ONTRACK, 1500, 6), GoalNeed.need(12000.0, 3000.0, 2000.0, ld(2027, 5, 15), today))
        assertEquals(GoalNeed.Need(GoalNeed.Status.BEHIND, 1500, 6), GoalNeed.need(12000.0, 3000.0, 1000.0, ld(2027, 5, 15), today))
        assertEquals(GoalNeed.Need(GoalNeed.Status.BEHIND, 2223, 3), GoalNeed.need(10000.0, 3333.0, 0.0, ld(2027, 2, 1), today))
        assertEquals(GoalNeed.Need(GoalNeed.Status.REACHED, 0, 0), GoalNeed.need(5000.0, 5000.0, 0.0, null, today))
        assertEquals(GoalNeed.Need(GoalNeed.Status.NODATE, null, null), GoalNeed.need(5000.0, 100.0, 0.0, null, today))
        val gl = GoalList.parse("1\tPhone\t20000\t5000\t1000\t${e(2027, 6, 1)}\n2\tCar\t90000\t0\t0\nbad line\n3\tX\tnope\t0\t0")
        assertEquals(listOf(Triple(1L, "Phone", e(2027, 6, 1) as Long?), Triple(2L, "Car", null)), gl.map { Triple(it.id, it.name, it.deadline) })
        assertEquals(gl, GoalList.parse(GoalList.format(gl)))
        assertEquals("2\tCar\t90000.0\t0.0\t0.0\t", GoalList.format(listOf(SavingsGoal(2, "Car", 90000.0, 0.0, 0.0, null))))
    }

    @Test fun report() {
        fun d(x: Int) = e(2026, 11, x)
        val tx = listOf(
            MonthReport.Tx("EXPENSE", 100.0, "Food", "koshary", d(2), ""),
            MonthReport.Tx("EXPENSE", 300.0, "Food", "market", d(2), ""),
            MonthReport.Tx("EXPENSE", 1200.0, "Bills", "rent", d(1), "BILL"),
            MonthReport.Tx("EXPENSE", 50.0, "Fun", "cinema", d(9), ""),
            MonthReport.Tx("EXPENSE", 5000.0, "Loans", "lend", d(3), "LOAN"),
            MonthReport.Tx("EXPENSE", 700.0, "", "piggy", d(4), "PIGGY"),
            MonthReport.Tx("INCOME", 9000.0, "Salary", "salary", d(1), "SALARY"),
            MonthReport.Tx("INCOME", 400.0, "", "back", d(5), "PIGGYOUT"),
            MonthReport.Tx("TRANSFER", 77.0, "", "t", d(6), ""),
        )
        val rep = MonthReport.build(tx, listOf(MonthReport.Tx("EXPENSE", 1000.0, "Food", "x", 1, "")), 30)
        assertEquals(1650.0, rep.spent, 0.0); assertEquals(9000.0, rep.income, 0.0); assertEquals(7350.0, rep.net, 0.0)
        assertEquals(1000.0, rep.prevSpent, 0.0); assertEquals(65, rep.changePct); assertEquals(55.0, rep.perDay, 0.0)
        assertEquals(listOf(MonthReport.Cat("Bills", 1200.0, 73), MonthReport.Cat("Food", 400.0, 24), MonthReport.Cat("Fun", 50.0, 3)), rep.topCategories)
        assertEquals(listOf("rent", "market", "koshary"), rep.topExpenses.map { it.note })
        assertEquals(MonthReport.Day(d(1), 1200.0), rep.biggestDay)
        val empty = MonthReport.build(emptyList(), emptyList(), 30)
        assertEquals(MonthReport.Report(0.0, 0.0, 0.0, 0.0, null, 0.0, emptyList(), emptyList(), null), empty)
    }

    @Test fun csv() {
        val csv = CsvExport.build(
            listOf(
                CsvExport.Row(e(2026, 11, 2), "EXPENSE", 60.0, "EGP", 1.0, "Food · Lunch", "Cash", "koshary, large", listOf("work", "trip")),
                CsvExport.Row(e(2026, 11, 3), "EXPENSE", 10.0, "USD", 49.5, "", "Bank", "=1+1 \"x\"", emptyList()),
                CsvExport.Row(e(2026, 11, 4), "INCOME", 100.5, "EGP", 1.0, "Salary", "Bank", "قهوة", emptyList()),
            ),
            "EGP",
        )
        assertEquals(
            "﻿Date,Type,Amount,Currency,Rate,Amount (EGP),Category,Wallet,Note,Tags\r\n" +
                "2026-11-02,EXPENSE,60.00,EGP,1,60.00,Food · Lunch,Cash,\"koshary, large\",work|trip\r\n" +
                "2026-11-03,EXPENSE,10.00,USD,49.5,495.00,,Bank,\"'=1+1 \"\"x\"\"\",\r\n" +
                "2026-11-04,INCOME,100.50,EGP,1,100.50,Salary,Bank,قهوة,\r\n",
            csv,
        )
    }

    @Test fun lock() {
        assertEquals(listOf(true, true, false, true), listOf(LockGrace.shouldRelock(0, 5000, 60), LockGrace.shouldRelock(1000, 1000, 0), LockGrace.shouldRelock(1000, 30999, 30), LockGrace.shouldRelock(1000, 31000, 30)))
    }

    @Test fun filters() {
        fun d(x: Int) = e(2026, 11, x)
        val names = mapOf(1L to "Food", 2L to "Bills", 3L to "Fun")
        val cn = { id: Long? -> names[id] ?: "No category" }
        val t = listOf(
            TxnFilter.T(1, "EXPENSE", 60.0, "koshary", 1, 1, d(2), "NEED", listOf("work")),
            TxnFilter.T(2, "EXPENSE", 1200.0, "rent", 2, 2, d(1), "", emptyList()),
            TxnFilter.T(3, "EXPENSE", 50.0, "cinema", 3, 1, d(9), "WANT", listOf("trip")),
            TxnFilter.T(4, "INCOME", 9000.0, "salary", null, 2, d(1), "", emptyList()),
            TxnFilter.T(5, "EXPENSE", 60.0, "lunch", 1, 1, d(2), "WANT", emptyList()),
        )
        fun ids(f: TxnFilter.F) = TxnFilter.apply(t, f, cn).list.map { it.id }
        assertEquals(listOf(3L, 5L, 1L, 4L, 2L), ids(TxnFilter.F()))
        assertEquals(listOf(5L, 1L), ids(TxnFilter.F(q = "FOOD")))
        assertEquals(listOf(5L, 1L), ids(TxnFilter.F(q = "60")))
        assertEquals(listOf(3L), ids(TxnFilter.F(q = "trip")))
        assertEquals(listOf(4L), ids(TxnFilter.F(types = setOf("INCOME"))))
        assertEquals(listOf(3L, 5L, 1L), ids(TxnFilter.F(catIds = setOf(1L, 3L))))
        assertEquals(listOf(5L, 1L, 2L), ids(TxnFilter.F(min = 55.0, max = 1200.0)))
        assertEquals(listOf(3L, 5L, 1L), ids(TxnFilter.F(from = d(2), to = d(9))))
        assertEquals(listOf(3L, 5L), ids(TxnFilter.F(walletId = 1, need = "WANT")))
        assertEquals(listOf(1L), ids(TxnFilter.F(tag = "work", q = "kosh")))
        assertEquals(emptyList<Long>(), ids(TxnFilter.F(q = "nothing")))
        assertEquals(170.0, TxnFilter.apply(t, TxnFilter.F(types = setOf("EXPENSE"), walletId = 1), cn).total, 0.0)
    }
}
