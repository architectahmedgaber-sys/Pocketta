package app.pocketta.widget

import android.content.Context
import app.pocketta.core.Circle
import app.pocketta.core.CycleMath
import app.pocketta.data.HomeState
import app.pocketta.data.Repo
import app.pocketta.data.Source
import app.pocketta.data.Suggestion
import app.pocketta.data.TxType
import app.pocketta.data.WeekData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the extra widgets show, read from the database when the widget draws. */
data class CatSpend(val id: Long, val name: String, val spent: Double, val limit: Double)
data class RecentTxn(val note: String, val category: String, val amount: Double, val income: Boolean, val daysAgo: Int)
data class DebtSummary(val lent: Double, val borrowed: Double, val lentCount: Int, val borrowedCount: Int, val topPerson: String?)
data class CircleInfo(val name: String, val daysToPayout: Int, val payout: Double, val received: Boolean, val month: Int, val months: Int)
data class SalaryInfo(val date: LocalDate, val days: Int, val amount: Double)
data class QuickCat(val id: Long, val name: String)

object WidgetData {
    private fun repo(context: Context) = Repo.get(context)

    suspend fun week(context: Context, s: HomeState): WeekData = repo(context).week(s.today, s.dayReference)

    /** Spending for each of the last 7 days (oldest first). */
    suspend fun weekDays(context: Context, s: HomeState): List<Pair<LocalDate, Double>> {
        val from = s.today.minusDays(6)
        val per = repo(context).dao.txns(from.toEpochDay(), s.today.toEpochDay() + 1)
            .filter { it.type == TxType.EXPENSE && !it.spread }
            .groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
        return (0..6).map { i -> from.plusDays(i.toLong()).let { d -> d to (per[d.toEpochDay()] ?: 0.0) } }
    }

    suspend fun suggestions(context: Context, s: HomeState): List<Suggestion> = repo(context).suggestions(s.today)

    /** Spending this cycle per category (sub-categories counted in their parent), biggest first. */
    suspend fun categories(context: Context, s: HomeState): List<CatSpend> {
        val dao = repo(context).dao
        val cats = dao.categories().filter { it.kind == TxType.EXPENSE }.associateBy { it.id }
        val txns = dao.txns(s.cycle.start.toEpochDay(), s.cycle.nextStart.toEpochDay())
            .filter { it.type == TxType.EXPENSE && !it.spread && it.categoryId != null }
        val byTop = HashMap<Long, Double>()
        for (t in txns) {
            val c = cats[t.categoryId] ?: continue
            val top = c.parentId ?: c.id
            byTop[top] = (byTop[top] ?: 0.0) + t.base
        }
        return byTop.entries.mapNotNull { e -> cats[e.key]?.let { CatSpend(it.id, it.name, e.value, it.monthlyLimit) } }
            .sortedByDescending { it.spent }
    }

    /** The category that is closest to (or over) its monthly limit. */
    suspend fun closestLimit(context: Context, s: HomeState): CatSpend? {
        val dao = repo(context).dao
        val all = dao.categories().filter { it.kind == TxType.EXPENSE && !it.archived && it.monthlyLimit > 0 }
        if (all.isEmpty()) return null
        val cats = dao.categories().associateBy { it.id }
        val txns = dao.txns(s.cycle.start.toEpochDay(), s.cycle.nextStart.toEpochDay())
            .filter { it.type == TxType.EXPENSE && !it.spread && it.categoryId != null }
        return all.map { c ->
            val spent = txns.filter { t ->
                val tc = cats[t.categoryId]
                t.categoryId == c.id || (tc != null && tc.parentId == c.id)
            }.sumOf { it.base }
            CatSpend(c.id, c.name, spent, c.monthlyLimit)
        }.maxByOrNull { it.spent / it.limit }
    }

    suspend fun recent(context: Context, s: HomeState, n: Int): List<RecentTxn> {
        val dao = repo(context).dao
        val cats = dao.categories().associateBy { it.id }
        val from = s.today.minusDays(60).toEpochDay()
        return dao.txns(from, s.today.toEpochDay() + 1)
            .filter { it.type != TxType.TRANSFER }
            .sortedWith(compareByDescending<app.pocketta.data.Txn> { it.epochDay }.thenByDescending { it.createdAt })
            .take(n)
            .map { t ->
                val cat = cats[t.categoryId]?.name.orEmpty()
                RecentTxn(
                    note = t.note.ifBlank { cat }.ifBlank { if (t.type == TxType.INCOME) "Income" else "Expense" },
                    category = cat, amount = t.base, income = t.type == TxType.INCOME,
                    daysAgo = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(t.epochDay), s.today).toInt(),
                )
            }
    }

    suspend fun debts(context: Context): DebtSummary {
        val open = repo(context).dao.loans().filter { !it.closed && it.remaining > 0 }
        val lent = open.filter { it.lent }
        val borrowed = open.filter { !it.lent }
        val top = open.maxByOrNull { it.remaining }?.person
        return DebtSummary(lent.sumOf { it.remaining }, borrowed.sumOf { it.remaining }, lent.size, borrowed.size, top)
    }

    suspend fun circle(context: Context, s: HomeState): CircleInfo? {
        val list = repo(context).dao.circles().map { c ->
            c to Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))
        }
        val pick = list.filter { !it.first.payoutReceived }.minByOrNull { it.second.payoutDate } ?: list.firstOrNull() ?: return null
        val (e, c) = pick
        return CircleInfo(
            name = e.name, daysToPayout = ChronoUnit.DAYS.between(s.today, c.payoutDate).toInt(),
            payout = c.payout, received = e.payoutReceived, month = c.currentMonth, months = e.months,
        )
    }

    fun salary(s: HomeState, repo: Repo): SalaryInfo? {
        val p = s.prefs
        if (p.salary <= 0) return null
        var d = repo.salaryDateIn(s.cycle, p.salaryDay)
        if (d == null || d.isBefore(s.today)) {
            val next = CycleMath.cycleFor(s.cycle.nextStart, p.cycleStart)
            d = repo.salaryDateIn(next, p.salaryDay)
        }
        d ?: return null
        return SalaryInfo(d, ChronoUnit.DAYS.between(s.today, d).toInt(), p.salary)
    }

    fun salary(context: Context, s: HomeState): SalaryInfo? = salary(s, repo(context))

    /** Most used expense categories, for the one-tap buttons. */
    suspend fun quickCats(context: Context, s: HomeState, n: Int): List<QuickCat> {
        val dao = repo(context).dao
        val cats = dao.categories().filter { it.kind == TxType.EXPENSE && !it.archived }.associateBy { it.id }
        val used = dao.txns(s.today.minusDays(90).toEpochDay(), s.today.toEpochDay() + 1)
            .filter { it.type == TxType.EXPENSE && it.source == Source.NONE && it.categoryId != null }
            .groupBy { it.categoryId!! }.entries.sortedByDescending { it.value.size }
            .mapNotNull { cats[it.key] }
        val out = ArrayList<QuickCat>()
        for (c in used + cats.values) {
            if (out.size >= n) break
            if (out.none { it.id == c.id }) out += QuickCat(c.id, c.name)
        }
        return out
    }

    /** How many days in a row (counting back from today) you stayed within the daily budget. */
    fun streak(s: HomeState): Int {
        if (s.dayReference <= 0) return 0
        var n = 0
        for ((_, spent) in s.days.reversed()) {
            if (spent <= s.dayReference) n++ else break
        }
        return n
    }
}
