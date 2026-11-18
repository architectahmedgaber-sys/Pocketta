package app.pocketta.data

import android.content.Context
import android.net.Uri
import app.pocketta.core.Budget
import app.pocketta.core.BudgetInput
import app.pocketta.core.BudgetResult
import app.pocketta.core.Circle
import app.pocketta.core.Cycle
import app.pocketta.core.CycleMath
import app.pocketta.core.Frequency
import app.pocketta.core.InstallmentPlan
import app.pocketta.core.Gold
import app.pocketta.core.Planning
import app.pocketta.core.WealthLog
import app.pocketta.core.WealthPoint
import app.pocketta.core.Recurrence
import app.pocketta.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Something due soon: a bill, an installment, a circle share or a loan. */
data class Upcoming(
    val kind: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val due: LocalDate,
    /** Money coming back to me (a loan I gave). */
    val incoming: Boolean = false,
    /** A quarterly/yearly bill that is already set aside every month. */
    val spread: Boolean = false,
)

data class HomeState(
    val prefs: Prefs,
    val cycle: Cycle,
    val today: LocalDate,
    val budget: BudgetResult,
    val days: List<Pair<LocalDate, Double>>,
    val dayReference: Double,
    val upcoming: List<Upcoming>,
    val setAside: Double,
    val income: Double,
    val salaryExpected: Double,
)

/** One top-level category: spent so far this cycle and the usual per month (average of the last 3 cycles that had spending, 0 if none). */
data class CatStat(val name: String, val spentSoFar: Double, val usual: Double) {
    /** Best guess of what the category costs in a normal month. */
    fun monthly(dayIndex: Int, cycleLength: Int): Double =
        if (usual > 0) usual else if (dayIndex > 0) spentSoFar / dayIndex * cycleLength else spentSoFar
}

data class PlanData(
    val home: HomeState,
    val categories: List<CatStat>,
    val everydaySpent: Double,
    val dayIndex: Int,
    /** Bills and subscriptions still to come this cycle that could wait a month. */
    val postponable: List<Planning.Postponable>,
) {
    val emergency: Planning.Emergency?
        get() = Planning.emergency(
            home.today, home.cycle, home.budget, everydaySpent, dayIndex, postponable,
            categories.map { Planning.CategorySpend(it.name, it.spentSoFar) },
        )
    val rises: List<Planning.Rise>
        get() = Planning.priceRises(
            categories.map { Planning.CategoryTrend(it.name, it.spentSoFar, it.usual) }, dayIndex, home.cycle.lengthDays,
        )
}

data class WealthView(
    val cash: Double,
    val gold: Double,
    val total: Double,
    val dollars: Double?,
    /** Gold-equivalent grams (21k) of the total, null when the gold price isn't known. */
    val goldGrams: Double?,
    val history: List<WealthPoint>,
)

/** A repeated expense (same note and amount) offered as a one-tap button. */
data class Suggestion(val note: String, val amount: Double)

/** The last 7 days against the 7 before, for the weekly card. */
data class WeekData(val spent: Double, val previous: Double, val topCategory: String?, val daysUnder: Int)

class Repo private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db: AppDb get() = AppDb.get(app)
    val dao: MoneyDao get() = db.dao()

    private val _version = MutableStateFlow(0)
    /** Goes up after every change, so screens that compute totals know to recompute. */
    val version: StateFlow<Int> = _version

    fun prefsFlow(): Flow<Prefs> = dao.settingsFlow().map { Prefs.from(it) }
    suspend fun prefs(): Prefs = Prefs.from(dao.settings())

    fun changed() {
        _version.value = _version.value + 1
        scope.launch { runCatching { Widgets.updateAll(app) } }
    }

    suspend fun set(key: String, value: Any) {
        dao.put(SettingRow(key, value.toString()))
        changed()
    }

    // ---------- Seed ----------

    suspend fun ensureSeeded() {
        if (dao.wallets().isNotEmpty()) return
        dao.insertWallet(Wallet(name = "Cash", type = "CASH", sort = 0))
        dao.insertWallet(Wallet(name = "Bank account", type = "BANK", sort = 1))
        dao.insertWallet(Wallet(name = "Vodafone Cash", type = "MOBILE", sort = 2))
        var sort = 0
        for ((name, subs) in EXPENSE_SEED) {
            val pid = dao.insertCategory(Category(name = name, kind = TxType.EXPENSE, sort = sort++))
            subs.forEachIndexed { i, s -> dao.insertCategory(Category(name = s, parentId = pid, kind = TxType.EXPENSE, sort = i)) }
        }
        for (name in INCOME_SEED) dao.insertCategory(Category(name = name, kind = TxType.INCOME, sort = sort++))
        changed()
    }

    suspend fun categoryNamed(name: String, kind: String): Long? =
        dao.categories().firstOrNull { it.name.equals(name, true) && it.kind == kind && !it.archived }?.id
            ?: dao.categories().firstOrNull { it.name.equals(name, true) && it.kind == kind }?.id

    private suspend fun defaultWallet(p: Prefs): Long {
        val ws = dao.wallets().filter { !it.archived }
        return ws.firstOrNull { it.id == p.lastWallet }?.id ?: ws.firstOrNull()?.id ?: dao.insertWallet(Wallet(name = "Cash"))
    }

    // ---------- Transactions ----------

    suspend fun saveTxn(t: Txn): Long {
        val id = if (t.id == 0L) dao.insertTxn(t) else { dao.updateTxn(t); t.id }
        if (t.type == TxType.EXPENSE && t.categoryId != null && t.source == Source.NONE) dao.put(SettingRow("lastCategory", t.categoryId.toString()))
        dao.put(SettingRow("lastWallet", t.walletId.toString()))
        changed()
        return id
    }

    suspend fun deleteTxn(t: Txn) {
        dao.deleteTxn(t)
        when (t.source) {
            Source.INSTALLMENT -> dao.installment(t.sourceId)?.let { dao.updateInstallment(it.copy(paid = (it.paid - 1).coerceAtLeast(0))) }
            Source.CIRCLE -> dao.circle(t.sourceId)?.let {
                if (t.type == TxType.INCOME) dao.updateCircle(it.copy(payoutReceived = false))
                else dao.updateCircle(it.copy(paid = (it.paid - 1).coerceAtLeast(0)))
            }
            Source.LOAN -> dao.loan(t.sourceId)?.let { l ->
                // A repayment (money moving the other way) is undone; deleting the loan's first record leaves the loan.
                val isRepayment = (l.lent && t.type == TxType.INCOME) || (!l.lent && t.type == TxType.EXPENSE)
                if (isRepayment) {
                    val repaid = (l.repaid - t.amount).coerceAtLeast(0.0)
                    dao.updateLoan(l.copy(repaid = repaid, closed = repaid >= l.amount - 0.001))
                }
            }
        }
        changed()
    }

    /** Guesses the category for a quick note like "koshary": same note as before, or a category with that name. */
    suspend fun guessCategory(note: String, type: String): Long? {
        if (note.isBlank()) return null
        dao.lastWithNote(type, note.trim())?.categoryId?.let { return it }
        val words = note.lowercase().split(' ').filter { it.length >= 3 }
        return dao.categories().filter { it.kind == type && !it.archived }.firstOrNull { c ->
            val n = c.name.lowercase()
            words.any { w -> n == w || n.startsWith(w) || w.startsWith(n) }
        }?.id
    }

    /** Saves an expense typed as a sentence ("koshary 60"). Picks the category from the words, else the last one used. */
    suspend fun quickExpense(note: String, amount: Double, daysAgo: Int = 0): Txn {
        val p = prefs()
        val cat = guessCategory(note, TxType.EXPENSE) ?: p.lastCategory.takeIf { it != 0L }
        val t = Txn(
            type = TxType.EXPENSE, amount = amount, currency = p.baseCurrency, rate = 1.0, walletId = defaultWallet(p),
            categoryId = cat, note = note.trim(), epochDay = LocalDate.now().minusDays(daysAgo.toLong()).toEpochDay(),
        )
        return t.copy(id = saveTxn(t))
    }

    /** Expenses you type again and again, so they can be added with one tap. */
    suspend fun suggestions(today: LocalDate = LocalDate.now()): List<Suggestion> {
        val from = today.minusDays(60).toEpochDay()
        val cur = prefs().baseCurrency
        return dao.txns(from, today.toEpochDay() + 1)
            .filter { it.type == TxType.EXPENSE && it.source == Source.NONE && it.note.isNotBlank() && it.currency == cur }
            .groupBy { it.note.trim().lowercase() to it.amount }
            .filter { it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(4)
            .map { Suggestion(it.value.first().note.trim(), it.key.second) }
    }

    suspend fun week(today: LocalDate, dayReference: Double): WeekData {
        val from = today.minusDays(6).toEpochDay()
        val to = today.toEpochDay() + 1
        val now = dao.txns(from, to).filter { it.type == TxType.EXPENSE && !it.spread }
        val before = dao.txns(from - 7, from).filter { it.type == TxType.EXPENSE && !it.spread }
        val cats = dao.categories().associateBy { it.id }
        fun topName(id: Long?): String? = id?.let { cats[it] }?.let { c -> (c.parentId?.let { cats[it] } ?: c).name }
        val top = now.groupBy { topName(it.categoryId) }.filterKeys { it != null }
            .mapValues { e -> e.value.sumOf { it.base } }.maxByOrNull { it.value }?.key
        val perDay = now.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
        var under = 0
        if (dayReference > 0) for (d in from until to) if ((perDay[d] ?: 0.0) <= dayReference) under++
        return WeekData(now.sumOf { it.base }, before.sumOf { it.base }, top, under)
    }

    // ---------- Bills ----------

    suspend fun saveBill(b: Bill) { if (b.id == 0L) dao.insertBill(b) else dao.updateBill(b); changed() }
    suspend fun deleteBill(b: Bill) { dao.deleteBill(b); changed() }

    suspend fun estimate(b: Bill): Double =
        if (!b.variable) b.amount
        else dao.txnsFor(Source.BILL, b.id, 3).map { it.amount }.ifEmpty { listOf(b.amount) }.average()

    suspend fun payBill(b: Bill, amount: Double? = null, day: LocalDate = LocalDate.now()) {
        val p = prefs()
        val f = Frequency.parse(b.frequency)
        dao.insertTxn(
            Txn(
                type = TxType.EXPENSE, amount = amount ?: estimate(b), currency = b.currency, rate = p.rateOf(b.currency),
                walletId = b.walletId ?: defaultWallet(p),
                categoryId = b.categoryId ?: categoryNamed(if (b.kind == "SUB") "Subscriptions" else "Bills", TxType.EXPENSE),
                note = b.name, epochDay = day.toEpochDay(), source = Source.BILL, sourceId = b.id,
                spread = Recurrence.isSpread(f, b.everyN),
            )
        )
        skipBill(b)
    }

    suspend fun skipBill(b: Bill) {
        val f = Frequency.parse(b.frequency)
        dao.updateBill(b.copy(nextDue = Recurrence.next(LocalDate.ofEpochDay(b.nextDue), f, b.everyN, b.anchorDay).toEpochDay()))
        changed()
    }

    // ---------- Installments ----------

    suspend fun saveInstallment(i: Installment) { if (i.id == 0L) dao.insertInstallment(i) else dao.updateInstallment(i); changed() }
    suspend fun deleteInstallment(i: Installment) { dao.deleteInstallment(i); changed() }

    suspend fun payInstallment(i: Installment, day: LocalDate = LocalDate.now()) {
        if (i.paid >= i.total) return
        val p = prefs()
        dao.insertTxn(
            Txn(
                type = TxType.EXPENSE, amount = i.amount, walletId = i.walletId ?: defaultWallet(p),
                categoryId = categoryNamed("Installments", TxType.EXPENSE), note = i.name,
                epochDay = day.toEpochDay(), source = Source.INSTALLMENT, sourceId = i.id,
            )
        )
        dao.updateInstallment(i.copy(paid = i.paid + 1))
        changed()
    }

    // ---------- Circles ----------

    suspend fun saveCircle(c: CircleEntity) { if (c.id == 0L) dao.insertCircle(c) else dao.updateCircle(c); changed() }
    suspend fun deleteCircle(c: CircleEntity) { dao.deleteCircle(c); changed() }

    suspend fun payCircle(c: CircleEntity, day: LocalDate = LocalDate.now()) {
        if (c.paid >= c.months) return
        val p = prefs()
        dao.insertTxn(
            Txn(
                type = TxType.EXPENSE, amount = c.share, walletId = c.walletId ?: defaultWallet(p),
                categoryId = categoryNamed("Gam'eya", TxType.EXPENSE), note = "${c.name} share",
                epochDay = day.toEpochDay(), source = Source.CIRCLE, sourceId = c.id,
            )
        )
        dao.updateCircle(c.copy(paid = c.paid + 1))
        changed()
    }

    suspend fun receivePayout(c: CircleEntity, day: LocalDate = LocalDate.now()) {
        if (c.payoutReceived) return
        val p = prefs()
        dao.insertTxn(
            Txn(
                type = TxType.INCOME, amount = c.share * c.months, walletId = c.walletId ?: defaultWallet(p),
                categoryId = categoryNamed("Gam'eya payout", TxType.INCOME), note = "${c.name} payout",
                epochDay = day.toEpochDay(), source = Source.CIRCLE, sourceId = c.id,
            )
        )
        dao.updateCircle(c.copy(payoutReceived = true))
        changed()
    }

    // ---------- Loans ----------

    suspend fun addLoan(
        person: String, lent: Boolean, amount: Double, walletId: Long?, day: LocalDate,
        due: LocalDate?, note: String, recordMoney: Boolean = true,
    ): Long {
        val p = prefs()
        val id = dao.insertLoan(
            Loan(person = person, direction = if (lent) "LENT" else "BORROWED", amount = amount, epochDay = day.toEpochDay(), dueDay = due?.toEpochDay(), note = note)
        )
        if (recordMoney) {
            dao.insertTxn(
                Txn(
                    type = if (lent) TxType.EXPENSE else TxType.INCOME, amount = amount, walletId = walletId ?: defaultWallet(p),
                    categoryId = if (lent) categoryNamed("Loans", TxType.EXPENSE) else categoryNamed("Borrowed", TxType.INCOME),
                    note = if (lent) "Lent to $person" else "Borrowed from $person",
                    epochDay = day.toEpochDay(), source = Source.LOAN, sourceId = id,
                )
            )
        }
        changed()
        return id
    }

    suspend fun updateLoan(l: Loan) { dao.updateLoan(l); changed() }
    suspend fun deleteLoan(l: Loan) { dao.deleteLoan(l); changed() }

    suspend fun loanPayment(l: Loan, amount: Double, walletId: Long? = null, day: LocalDate = LocalDate.now()) {
        val p = prefs()
        dao.insertTxn(
            Txn(
                type = if (l.lent) TxType.INCOME else TxType.EXPENSE, amount = amount, walletId = walletId ?: defaultWallet(p),
                categoryId = if (l.lent) categoryNamed("Loan repaid", TxType.INCOME) else categoryNamed("Loans", TxType.EXPENSE),
                note = if (l.lent) "${l.person} paid back" else "Paid back ${l.person}",
                epochDay = day.toEpochDay(), source = Source.LOAN, sourceId = l.id,
            )
        )
        val repaid = l.repaid + amount
        dao.updateLoan(l.copy(repaid = repaid, closed = repaid >= l.amount - 0.001))
        changed()
    }

    // ---------- Salary ----------

    fun salaryDateIn(cycle: Cycle, day: Int): LocalDate? {
        val m = YearMonth.from(cycle.start)
        return listOf(m, m.plusMonths(1)).map { CycleMath.startIn(it, day) }.firstOrNull { cycle.contains(it) }
    }

    /** Records this month's salary on its day, once per cycle. */
    suspend fun ensureSalary(today: LocalDate = LocalDate.now()) {
        val p = prefs()
        if (p.salary <= 0) return
        val cycle = CycleMath.cycleFor(today, p.cycleStart)
        val d = salaryDateIn(cycle, p.salaryDay) ?: return
        if (today.isBefore(d)) return
        if (dao.countSource(Source.SALARY, cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) > 0) return
        val wallet = dao.wallets().firstOrNull { it.id == p.salaryWallet && !it.archived }?.id ?: defaultWallet(p)
        dao.insertTxn(
            Txn(
                type = TxType.INCOME, amount = p.salary, walletId = wallet, categoryId = categoryNamed("Salary", TxType.INCOME),
                note = "Salary", epochDay = d.toEpochDay(), source = Source.SALARY,
            )
        )
        changed()
    }

    // ---------- Totals ----------

    /** Everything unpaid that falls due before [to], including anything overdue. */
    suspend fun upcoming(p: Prefs, to: LocalDate): List<Upcoming> {
        val out = ArrayList<Upcoming>()
        val epoch = LocalDate.ofEpochDay(0)
        for (b in dao.bills()) {
            val f = Frequency.parse(b.frequency)
            val est = estimate(b) * p.rateOf(b.currency)
            val first = LocalDate.ofEpochDay(b.nextDue)
            for (d in Recurrence.occurrences(first, f, b.everyN, first, to, b.anchorDay)) {
                out += Upcoming("BILL", b.id, b.name, Recurrence.describe(f, b.everyN), est, d, spread = Recurrence.isSpread(f, b.everyN))
            }
        }
        for (i in dao.installments()) {
            val plan = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue))
            plan.unpaidDueIn(epoch, to).forEachIndexed { k, d ->
                out += Upcoming("INSTALLMENT", i.id, i.name, "${i.paid + k + 1} of ${i.total} · ${i.type}", i.amount, d)
            }
        }
        for (c in dao.circles()) {
            val circle = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue))
            circle.plan.unpaidDueIn(epoch, to).forEachIndexed { k, d ->
                out += Upcoming("CIRCLE", c.id, "${c.name} share", "Month ${c.paid + k + 1} of ${c.months}", c.share, d)
            }
        }
        for (l in dao.loans()) {
            val due = l.dueDay ?: continue
            if (l.closed || due >= to.toEpochDay()) continue
            out += Upcoming(
                "LOAN", l.id, if (l.lent) "${l.person} pays you back" else "Pay back ${l.person}",
                if (l.lent) "They owe you" else "You owe", l.remaining, LocalDate.ofEpochDay(due), incoming = l.lent,
            )
        }
        return out.sortedBy { it.due }
    }

    suspend fun home(today: LocalDate = LocalDate.now()): HomeState {
        val p = prefs()
        val cycle = CycleMath.cycleFor(today, p.cycleStart)
        val t0 = today.toEpochDay()
        val txns = dao.txns(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay())
        val income = txns.filter { it.type == TxType.INCOME }.sumOf { it.base }
        val spending = txns.filter { it.type == TxType.EXPENSE && !it.spread && it.epochDay <= t0 }
        val spentBefore = spending.filter { it.epochDay < t0 }.sumOf { it.base }
        val spentToday = spending.filter { it.epochDay == t0 }.sumOf { it.base }
        val everyday = spending.filter { it.source == Source.NONE }.sumOf { it.base }
        val salaryPosted = txns.any { it.source == Source.SALARY }
        val salaryExpected = if (p.salary > 0 && !salaryPosted && salaryDateIn(cycle, p.salaryDay) != null) p.salary else 0.0
        val all = upcoming(p, cycle.nextStart.plusDays(35))
        val commitments = all.filter { !it.incoming && !it.spread && it.due.isBefore(cycle.nextStart) }.sumOf { it.amount }
        val setAside = dao.bills().filter { Recurrence.isSpread(Frequency.parse(it.frequency), it.everyN) }
            .sumOf { Recurrence.monthlyShare(it.amount * p.rateOf(it.currency), Frequency.parse(it.frequency), it.everyN) } + p.savingsPerMonth
        val result = Budget.compute(
            BudgetInput(
                cycle = cycle, today = today, incomeReceived = income, salaryExpected = salaryExpected, setAside = setAside,
                spentBeforeToday = spentBefore, spentToday = spentToday, commitments = commitments, everydaySpent = everyday,
            )
        )
        val perDay = spending.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
        val days = cycle.days().filter { !it.isAfter(today) }.map { it to (perDay[it.toEpochDay()] ?: 0.0) }
        return HomeState(
            prefs = p, cycle = cycle, today = today, budget = result, days = days,
            dayReference = if (cycle.lengthDays > 0) result.budget / cycle.lengthDays else 0.0,
            upcoming = all.filter { it.due.isBefore(today.plusDays(35)) }, setAside = setAside, income = income, salaryExpected = salaryExpected,
        )
    }

    /** Current balance of every wallet, in the base currency. */
    fun balancesFlow(): Flow<Map<Long, Double>> = combine(dao.walletsFlow(), dao.allTxnsFlow(), prefsFlow()) { ws, txns, p ->
        val m = HashMap<Long, Double>()
        for (w in ws) m[w.id] = w.opening * p.rateOf(w.currency)
        for (t in txns) when (t.type) {
            TxType.EXPENSE -> m[t.walletId] = (m[t.walletId] ?: 0.0) - t.base
            TxType.INCOME -> m[t.walletId] = (m[t.walletId] ?: 0.0) + t.base
            TxType.TRANSFER -> {
                m[t.walletId] = (m[t.walletId] ?: 0.0) - t.base
                t.toWalletId?.let { m[it] = (m[it] ?: 0.0) + t.base }
            }
        }
        m
    }

    // ---------- Planning ----------

    suspend fun planData(today: LocalDate = LocalDate.now()): PlanData {
        val h = home(today)
        val p = h.prefs
        val cats = dao.categories().associateBy { it.id }
        fun top(id: Long?): String? = id?.let { cats[it] }?.let { c -> (c.parentId?.let { cats[it] } ?: c).name }
        suspend fun totals(from: LocalDate, toExclusive: LocalDate, upTo: LocalDate? = null): Map<String, Double> =
            dao.txns(from.toEpochDay(), toExclusive.toEpochDay())
                .filter { it.type == TxType.EXPENSE && !it.spread && (upTo == null || it.epochDay <= upTo.toEpochDay()) }
                .groupBy { top(it.categoryId) ?: "Other" }.mapValues { e -> e.value.sumOf { it.base } }
        val now = totals(h.cycle.start, h.cycle.nextStart, today)
        val past = ArrayList<Map<String, Double>>()
        var c = h.cycle
        repeat(3) { c = c.previous(p.cycleStart); past += totals(c.start, c.nextStart) }
        val withData = past.filter { it.isNotEmpty() }
        val stats = (now.keys + withData.flatMap { it.keys }).distinct().map { name ->
            CatStat(name, now[name] ?: 0.0, if (withData.isEmpty()) 0.0 else withData.sumOf { it[name] ?: 0.0 } / withData.size)
        }.sortedByDescending { maxOf(it.usual, it.spentSoFar) }
        val everyday = dao.txns(h.cycle.start.toEpochDay(), today.toEpochDay() + 1)
            .filter { it.type == TxType.EXPENSE && !it.spread && it.source == Source.NONE }.sumOf { it.base }
        val postponable = h.upcoming.filter { it.kind == "BILL" && !it.incoming && !it.spread && it.due.isAfter(today) && it.due.isBefore(h.cycle.nextStart) }
            .map { Planning.Postponable(it.title, it.amount, it.due) }
        return PlanData(h, stats, everyday, h.cycle.dayIndex(today), postponable)
    }

    // ---------- Wealth ----------

    /** Money in all wallets, in the base currency. */
    suspend fun walletTotal(p: Prefs): Double {
        var total = dao.wallets().filter { !it.archived }.sumOf { it.opening * p.rateOf(it.currency) }
        val active = dao.wallets().filter { !it.archived }.map { it.id }.toSet()
        for (t in dao.allTxns()) when (t.type) {
            TxType.EXPENSE -> if (t.walletId in active) total -= t.base
            TxType.INCOME -> if (t.walletId in active) total += t.base
            TxType.TRANSFER -> {
                if (t.walletId in active) total -= t.base
                if (t.toWalletId in active) total += t.base
            }
        }
        return total
    }

    /** What you own: wallets, gold you bought at today's price, and how it looks in dollars and gold. */
    suspend fun wealth(): WealthView {
        val p = prefs()
        val cash = walletTotal(p)
        val gold = Gold.summary(p.goldBuys, p.gold)?.value ?: 0.0
        val total = cash + gold
        val usdRate = p.rates["USD"] ?: p.onlineRates["USD"]
        return WealthView(
            cash = cash, gold = gold, total = total,
            dollars = Planning.inDollars(total, usdRate),
            goldGrams = Planning.inGoldGrams(total, p.gold["21"]),
            history = p.wealthHistory,
        )
    }

    /** Saves today's total once a day (a later call the same day replaces it). */
    suspend fun recordWealth(today: LocalDate = LocalDate.now()) {
        val p = prefs()
        if (!p.onboarded) return
        val w = wealth()
        val next = WealthLog.record(p.wealthHistory, today.toEpochDay(), w.total)
        if (next == p.wealthHistory) return
        dao.put(SettingRow("wealthHistory", WealthLog.format(next)))
        _version.value = _version.value + 1
    }

    // ---------- Backup ----------

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val f = app.getDatabasePath(AppDb.NAME)
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to that file")
        out.use { o -> f.inputStream().use { it.copyTo(o) } }
    }

    /** Replaces all data with a backup file. The app must restart after this. */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(app.cacheDir, "restore.db")
        val input = app.contentResolver.openInputStream(uri) ?: return@withContext false
        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) { tmp.delete(); return@withContext false }
        AppDb.closeForRestore()
        val target = app.getDatabasePath(AppDb.NAME)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
        true
    }

    /** Saves a backup to the file the user picked, when it's due (or always when [force]). */
    suspend fun autoBackupIfDue(force: Boolean = false): Boolean {
        val p = prefs()
        if (p.backupUri.isEmpty() || (!force && p.autoBackup == "OFF")) return false
        val every = if (p.autoBackup == "WEEKLY") 7 * 86_400_000L else 86_400_000L - 3_600_000L
        if (!force && System.currentTimeMillis() - p.lastBackup < every) return false
        return runCatching {
            exportTo(Uri.parse(p.backupUri))
            dao.put(SettingRow("lastBackup", System.currentTimeMillis().toString()))
            changed()
        }.isSuccess
    }

    suspend fun markBackedUp() {
        dao.put(SettingRow("lastBackup", System.currentTimeMillis().toString()))
        changed()
    }

    companion object {
        @Volatile private var inst: Repo? = null
        fun get(context: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(context.applicationContext).also { inst = it }
        }

        val EXPENSE_SEED: List<Pair<String, List<String>>> = listOf(
            "Food" to listOf("Groceries", "Eating out", "Delivery", "Coffee"),
            "Transport" to listOf("Uber / Careem", "Microbus / Metro", "Fuel", "Parking", "Car service"),
            "Home" to listOf("Rent", "Maintenance", "Cleaning", "Furniture"),
            "Bills" to listOf("Electricity", "Water", "Gas", "Internet", "Mobile"),
            "Subscriptions" to emptyList(),
            "Health" to listOf("Doctor", "Pharmacy", "Lab tests"),
            "Shopping" to listOf("Clothes", "Electronics", "Home stuff"),
            "Family" to emptyList(),
            "Education" to emptyList(),
            "Fun" to listOf("Outings", "Trips", "Games"),
            "Personal care" to emptyList(),
            "Gifts & charity" to emptyList(),
            "Installments" to emptyList(),
            "Gam'eya" to emptyList(),
            "Loans" to emptyList(),
            "Other" to emptyList(),
        )
        val INCOME_SEED = listOf("Salary", "Gam'eya payout", "Borrowed", "Loan repaid", "Side work", "Gift", "Other income")
    }
}
