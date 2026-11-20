package app.pocketta.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.pocketta.core.Budget
import app.pocketta.core.Money
import app.pocketta.core.BudgetInput
import app.pocketta.core.BudgetResult
import app.pocketta.core.Circle
import app.pocketta.core.Cycle
import app.pocketta.core.CycleMath
import app.pocketta.core.Frequency
import app.pocketta.core.LimitAlerts
import app.pocketta.core.InstallmentPlan
import app.pocketta.core.Gold
import app.pocketta.core.GoalList
import app.pocketta.core.Piggy
import app.pocketta.core.SavingsGoal
import app.pocketta.core.Planning
import app.pocketta.core.PlanInput
import app.pocketta.core.PlanBill
import app.pocketta.core.PlanInstallment
import app.pocketta.core.PlanCircle
import app.pocketta.core.PlanLoan
import app.pocketta.core.PlanMonth
import app.pocketta.core.MonthPlanner
import app.pocketta.core.Ym
import app.pocketta.core.WealthLog
import app.pocketta.core.WealthPoint
import app.pocketta.core.Recurrence
import app.pocketta.core.Streak
import app.pocketta.core.StreakResult
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
    val streak: StreakResult? = null,
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
    /** This month's salary when its day hasn't posted it yet; not part of the total. */
    val salaryComing: Double = 0.0,
)

/** A repeated expense (same note and amount) offered as a one-tap button. */
data class Suggestion(val note: String, val amount: Double)

/** The last 7 days against the 7 before, for the weekly card. */
data class WeekData(val spent: Double, val previous: Double, val topCategory: String?, val daysUnder: Int, val topAmount: Double = 0.0)

/** One group of payments that can be marked paid together (a provider, credit cards, gam'eya, bills). */
data class PayItem(val kind: String, val id: Long, val name: String, val n: Int, val amount: Double, val each: Double? = null)
data class PayGroup(val key: String, val title: String, val items: List<PayItem>) { val total: Double get() = items.sumOf { it.amount } }
/** What is needed to undo a bulk payment. */
data class PayUndo(val txnIds: List<Long>, val billDue: List<Pair<Long, Long>>)

/** Everything saved under one tag. */
data class TagStat(val name: String, val spent: Double, val earned: Double, val count: Int, val cats: List<Pair<String, Double>>, val last: Long)

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

    /**
     * Change the main currency. Old amounts are not converted: only the symbol and decimals change.
     * Online rates and gold were converted into the old currency, so they are dropped (the caller fetches them again).
     */
    suspend fun setCurrency(currency: String, country: String) {
        val p = prefs()
        val cur = currency.trim().uppercase().take(4)
        if (cur.isEmpty()) return
        dao.put(SettingRow("country", country))
        if (cur != p.baseCurrency) {
            dao.put(SettingRow("baseCurrency", cur))
            for (k in listOf("onlineRates", "prevOnlineRates", "gold", "prevGold")) dao.put(SettingRow(k, ""))
            dao.put(SettingRow("ratesFetchedAt", "0"))
            dao.put(SettingRow("autoCurrencies", (p.autoCurrencies.filter { it != cur } + (if (cur == "USD") emptyList() else listOf("USD"))).distinct().joinToString(",")))
        }
        Money.setCurrency(cur)
        changed()
    }

    suspend fun set(key: String, value: Any) {
        dao.put(SettingRow(key, value.toString()))
        changed()
    }

    // ---------- Seed ----------

    suspend fun ensureSeeded() {
        if (dao.categories().isNotEmpty()) return
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
        return ws.firstOrNull { it.id == p.lastWallet }?.id ?: ws.firstOrNull()?.id ?: dao.wallets().firstOrNull()?.id ?: dao.insertWallet(Wallet(name = "Money", currency = p.baseCurrency))
    }

    /** "How much money do I have now?": moves the opening balance so the one total matches what the person says. */
    suspend fun setBalance(target: Double) {
        val p = prefs()
        val id = defaultWallet(p)
        val w = dao.wallets().first { it.id == id }
        val current = walletTotal(p)
        dao.updateWallet(w.copy(opening = w.opening + (target - current) / p.rateOf(w.currency)))
        changed()
    }

    /** The wallet new money records go to unless told otherwise. */
    suspend fun newRecordWallet(): Long = defaultWallet(prefs())

    // ---------- Transactions ----------

    suspend fun saveTxn(t0: Txn): Long {
        val t = (if (t0.walletId == 0L) t0.copy(walletId = defaultWallet(prefs())) else t0).copy(tags = Tags.join(t0.tagList))
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
        val topAmount = now.filter { topName(it.categoryId) == top && top != null }.sumOf { it.base }
        return WeekData(now.sumOf { it.base }, before.sumOf { it.base }, top, under, topAmount)
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

    suspend fun payInstallment(i: Installment, dayOrNull: LocalDate? = null) {
        if (i.paid >= i.total) return
        val p = prefs()
        val today = LocalDate.now()
        val due = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue)).dueOf(i.paid)
        val day = dayOrNull ?: if (due.isBefore(today)) due else today
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

    /** Recorded on the day the share was due when that day has passed, so an old gam'eya's shares land in their own months, not today. */
    suspend fun payCircle(c: CircleEntity, dayOrNull: LocalDate? = null) {
        if (c.paid >= c.months) return
        val p = prefs()
        val today = LocalDate.now()
        val due = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue)).plan.dueOf(c.paid)
        val day = dayOrNull ?: if (due.isBefore(today)) due else today
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

    /** Recorded on the payout day when it has passed, so collecting an old payout does not land in this month. */
    suspend fun receivePayout(c: CircleEntity, dayOrNull: LocalDate? = null) {
        if (c.payoutReceived) return
        val p = prefs()
        val today = LocalDate.now()
        val payDay = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue)).payoutDate
        val day = dayOrNull ?: if (payDay.isBefore(today)) payDay else today
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
    /** Deleting a loan also removes the money records it made, so wallets and the budget stay right. */
    suspend fun deleteLoan(l: Loan) { dao.deleteTxnsFor(Source.LOAN, l.id); dao.deleteLoan(l); changed() }

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
            .sumOf { Recurrence.monthlyShare(it.amount * p.rateOf(it.currency), Frequency.parse(it.frequency), it.everyN) } + p.savingsPerMonth + p.goals.sumOf { it.monthly }
        val groceries = if (p.household > 0) categoryNamed("Groceries", TxType.EXPENSE) else null
        val householdSpent = if (groceries != null) spending.filter { it.source == Source.NONE && it.categoryId == groceries }.sumOf { it.base } else 0.0
        val result = Budget.compute(
            BudgetInput(
                cycle = cycle, today = today, incomeReceived = income, salaryExpected = salaryExpected, setAside = setAside,
                spentBeforeToday = spentBefore, spentToday = spentToday, commitments = commitments, everydaySpent = everyday,
                household = p.household, householdSpent = householdSpent,
            )
        )
        val streak = streakNow(p, today, result, cycle)
        val perDay = spending.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
        val days = cycle.days().filter { !it.isAfter(today) }.map { it to (perDay[it.toEpochDay()] ?: 0.0) }
        return HomeState(
            prefs = p, cycle = cycle, today = today, budget = result, days = days,
            dayReference = if (cycle.lengthDays > 0) result.budget / cycle.lengthDays else 0.0,
            upcoming = all.filter { it.due.isBefore(today.plusDays(35)) }, setAside = setAside, income = income, salaryExpected = salaryExpected, streak = streak,
        )
    }

    private suspend fun streakNow(p: Prefs, today: LocalDate, b: BudgetResult, cycle: Cycle): StreakResult? {
        if (b.flexTotal <= 0) return null
        val t0 = today.toEpochDay()
        val all = dao.allTxns().filter { it.type == TxType.EXPENSE && !it.spread && it.source == Source.NONE }
        val first = all.minOfOrNull { it.epochDay } ?: return null
        if (t0 - first < 2) return null
        val groceries = if (p.household > 0) categoryNamed("Groceries", TxType.EXPENSE) else null
        val perDay = all.filter { it.epochDay in (t0 - 130)..t0 && (groceries == null || it.categoryId != groceries) }
            .groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.base } }
        return Streak.compute(perDay, t0, b.flexTotal / cycle.lengthDays * 7, first)
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

    // ---------- Month plan ----------

    /** The month-by-month plan: [past] finished months from what really happened, this month as what happened plus what is still due, then [future] months of what is expected. */
    suspend fun monthPlan(past: Int = 3, future: Int = 12, today: LocalDate = LocalDate.now()): List<PlanMonth> {
        val p = prefs()
        val nowYm = Ym.of(today)
        val input = PlanInput(
            salaryBase = p.salary, salarySteps = p.salarySteps, livingBase = p.livingBase, livingOverrides = p.livingOverrides,
            bills = dao.bills().map {
                PlanBill(
                    it.name, it.amount * p.rateOf(it.currency), Frequency.parse(it.frequency), it.everyN,
                    LocalDate.ofEpochDay(it.nextDue), it.anchorDay,
                )
            },
            installments = dao.installments().map {
                PlanInstallment(
                    it.name, it.amount, it.total, it.paid, LocalDate.ofEpochDay(it.firstDue), it.type.equals("Credit card", ignoreCase = true),
                )
            },
            circles = dao.circles().map {
                PlanCircle(it.name, it.share, it.months, it.myTurn, it.paid, LocalDate.ofEpochDay(it.firstDue), it.payoutReceived)
            },
            loans = dao.loans().filter { !it.closed }.map {
                PlanLoan(it.person, it.remaining, it.dueDay?.let { d -> LocalDate.ofEpochDay(d) }, it.lent)
            },
        )
        val projected = MonthPlanner.project(input, nowYm, future + 1).associateBy { it.ym }
        val rows = ArrayList<PlanMonth>()
        for (ym in (nowYm - past)..(nowYm + future)) {
            val from = Ym.start(ym).toEpochDay()
            val to = Ym.endExclusive(ym).toEpochDay()
            val tx = dao.txns(from, to).filter { it.type != TxType.TRANSFER }
            val a = actualMonth(ym, tx)
            val proj = projected[ym]
            rows += when {
                ym < nowYm -> a
                proj == null -> a
                else -> {
                    // This month: what already happened plus what is still due (unpaid things only), and living cost is the bigger of the plan and what was spent.
                    proj.copy(
                        salary = a.salary + (if (a.salary > 0) 0.0 else proj.salary),
                        payout = a.payout + proj.payout,
                        loansIn = a.loansIn + proj.loansIn,
                        bills = a.bills + proj.bills,
                        installments = a.installments + proj.installments,
                        cards = a.cards + proj.cards,
                        circleShares = a.circleShares + proj.circleShares,
                        loansOut = a.loansOut + proj.loansOut,
                        living = maxOf(proj.living, a.living),
                    )
                }
            }
        }
        return MonthPlanner.withCumulative(rows)
    }

    private fun actualMonth(ym: Int, tx: List<Txn>): PlanMonth {
        val inc = tx.filter { it.type == TxType.INCOME }
        val exp = tx.filter { it.type == TxType.EXPENSE }
        fun sum(l: List<Txn>, src: String) = l.filter { it.source == src }.sumOf { it.base }
        val payout = sum(inc, Source.CIRCLE)
        val loansIn = sum(inc, Source.LOAN)
        val committedSources = setOf(Source.BILL, Source.INSTALLMENT, Source.CIRCLE, Source.LOAN)
        return PlanMonth(
            ym = ym, actual = true,
            salary = inc.sumOf { it.base } - payout - loansIn,
            payout = payout, loansIn = loansIn,
            bills = sum(exp, Source.BILL), installments = sum(exp, Source.INSTALLMENT), cards = 0.0,
            circleShares = sum(exp, Source.CIRCLE), loansOut = sum(exp, Source.LOAN),
            living = exp.filter { it.source !in committedSources }.sumOf { it.base },
        )
    }

    // ---------- Tags (projects) ----------

    suspend fun allTags(): List<String> =
        dao.allTxns().flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    suspend fun tagStats(): List<TagStat> {
        val cats = dao.categories().associateBy { it.id }
        fun top(id: Long?): String = id?.let { cats[it] }?.let { c -> (c.parentId?.let { cats[it] } ?: c).name } ?: "No category"
        val out = LinkedHashMap<String, MutableList<Txn>>()
        for (t in dao.allTxns()) { if (t.type == TxType.TRANSFER) continue; for (g in t.tagList) out.getOrPut(g) { ArrayList() } += t }
        return out.map { (name, l) ->
            val exp = l.filter { it.type == TxType.EXPENSE }
            TagStat(name, exp.sumOf { it.base }, l.filter { it.type == TxType.INCOME }.sumOf { it.base }, l.size,
                exp.groupBy { top(it.categoryId) }.mapValues { e -> e.value.sumOf { it.base } }.entries.sortedByDescending { it.value }.map { it.key to it.value },
                l.maxOf { it.epochDay })
        }.sortedByDescending { it.last }
    }

    suspend fun txnsWithTag(tag: String): List<Txn> = dao.allTxns().filter { tag in it.tagList }.sortedByDescending { it.epochDay }

    suspend fun renameTag(from: String, to: String) {
        val n = Tags.normalize(listOf(to)).firstOrNull() ?: return
        for (t in dao.allTxns()) if (from in t.tagList) dao.updateTxn(t.copy(tags = Tags.join(t.tagList.map { if (it == from) n else it })))
        changed()
    }

    suspend fun removeTag(name: String) {
        for (t in dao.allTxns()) if (name in t.tagList) dao.updateTxn(t.copy(tags = Tags.join(t.tagList - name)))
        changed()
    }

    // ---------- Pay a whole group at once ----------

    /** Payments of each group (provider, credit cards, gam'eya, bills...) due up to the end of the month that contains [monthStart]. */
    suspend fun groupPayments(monthStart: LocalDate): List<PayGroup> {
        val p = prefs()
        val end = CycleMath.cycleFor(monthStart, p.cycleStart).nextStart
        val groups = LinkedHashMap<String, Pair<String, MutableList<PayItem>>>()
        fun add(key: String, title: String, item: PayItem) { groups.getOrPut(key) { title to ArrayList() }.second += item }
        for (b in dao.bills()) {
            val n = Recurrence.occurrences(LocalDate.ofEpochDay(b.nextDue), Frequency.parse(b.frequency), b.everyN, LocalDate.ofEpochDay(b.nextDue), end, b.anchorDay).size
            if (n > 0) add(if (b.kind == "SUB") "subs" else "bills", if (b.kind == "SUB") "Subscriptions" else "Bills", PayItem("BILL", b.id, b.name, n, estimate(b) * p.rateOf(b.currency) * n))
        }
        for (i in dao.installments()) {
            val n = InstallmentPlan(i.amount, i.total, i.paid, LocalDate.ofEpochDay(i.firstDue)).unpaidDueIn(LocalDate.of(1970, 1, 1), end).size
            if (n == 0) continue
            val card = i.type.equals("credit card", true)
            val label = i.provider.trim().ifEmpty { i.type }
            add(if (card) "cards" else "p:" + label.lowercase(), if (card) "Credit cards" else label, PayItem("INSTALLMENT", i.id, i.name, n, i.amount * n))
        }
        for (c in dao.circles()) {
            val n = Circle(c.share, c.months, c.myTurn, c.paid, LocalDate.ofEpochDay(c.firstDue)).plan.unpaidDueIn(LocalDate.of(1970, 1, 1), end).size
            if (n > 0) add("circles", "Gam'eya", PayItem("CIRCLE", c.id, c.name, n, c.share * n))
        }
        return groups.map { (k, v) -> PayGroup(k, v.first, v.second) }.sortedByDescending { it.total }
    }

    /** Marks every item of a group as paid. Returns what is needed to undo it. */
    suspend fun payGroup(g: PayGroup): PayUndo {
        val before = dao.allTxns().map { it.id }.toSet()
        val billDue = ArrayList<Pair<Long, Long>>()
        for (item in g.items) repeat(item.n) {
            when (item.kind) {
                "BILL" -> dao.bill(item.id)?.let { b -> billDue += b.id to b.nextDue; payBill(b, item.each) }
                "INSTALLMENT" -> dao.installment(item.id)?.let { x -> payInstallment(x) }
                "CIRCLE" -> dao.circle(item.id)?.let { x -> payCircle(x) }
            }
        }
        return PayUndo(dao.allTxns().filter { it.id !in before }.map { it.id }, billDue)
    }

    suspend fun undoPayGroup(u: PayUndo) {
        for (id in u.txnIds.reversed()) dao.txn(id)?.let { deleteTxn(it) }
        for ((id, due) in u.billDue.reversed()) dao.bill(id)?.let { dao.updateBill(it.copy(nextDue = due)) }
        changed()
    }

    // ---------- Wealth ----------

    /** Money in all wallets, in the base currency. */
    suspend fun walletTotal(p: Prefs): Double {
        // One balance: old wallets (even hidden ones) all add into it.
        var total = dao.wallets().sumOf { it.opening * p.rateOf(it.currency) }
        for (t in dao.allTxns()) when (t.type) {
            TxType.EXPENSE -> total -= t.base
            TxType.INCOME -> total += t.base
            TxType.TRANSFER -> {}
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
        val cycle = CycleMath.cycleFor(LocalDate.now(), p.cycleStart)
        val posted = dao.countSource(Source.SALARY, cycle.start.toEpochDay(), cycle.nextStart.toEpochDay()) > 0
        val coming = if (p.salary > 0 && !posted && salaryDateIn(cycle, p.salaryDay) != null) p.salary else 0.0
        return WealthView(salaryComing = coming,
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

    // ---------- Piggy bank and goals ----------

    /** Puts [amount] into the piggy bank (negative takes it out), dated today. */
    suspend fun piggyAdd(amount: Double) {
        if (amount == 0.0) return
        val p = prefs()
        val next = Piggy.add(p.piggy, LocalDate.now().toEpochDay(), amount)
        dao.put(SettingRow("piggy", Piggy.format(next)))
        changed()
    }

    suspend fun saveGoals(list: List<SavingsGoal>) {
        dao.put(SettingRow("goals", GoalList.format(list)))
        changed()
    }

    /** Adds to the main savings goal. The money also goes into the piggy bank, since it is saved. */
    suspend fun addToMainGoal(amount: Double) {
        if (amount <= 0) return
        val p = prefs()
        dao.put(SettingRow("goalSaved", (p.goalSaved + amount).toString()))
        piggyAdd(amount)
    }

    /** Adds to one of the extra goals, and to the piggy bank. */
    suspend fun addToGoal(id: Long, amount: Double) {
        if (amount <= 0) return
        val p = prefs()
        saveGoals(p.goals.map { if (it.id == id) it.copy(saved = it.saved + amount) else it })
        piggyAdd(amount)
    }

    /** Top-level expense categories whose spending this cycle is above their monthly limit, as (category, spent). */
    suspend fun categoriesOverLimit(today: LocalDate = LocalDate.now()): List<Pair<Category, Double>> {
        val p = prefs()
        val cycle = CycleMath.cycleFor(today, p.cycleStart)
        val cats = dao.categories().filter { it.kind == TxType.EXPENSE }.associateBy { it.id }
        val limited = cats.values.filter { it.parentId == null && it.monthlyLimit > 0 && !it.archived }
        if (limited.isEmpty()) return emptyList()
        val byTop = HashMap<Long, Double>()
        for (t in dao.txns(cycle.start.toEpochDay(), cycle.nextStart.toEpochDay())) {
            if (t.type != TxType.EXPENSE || t.spread || t.categoryId == null) continue
            val c = cats[t.categoryId] ?: continue
            val top = c.parentId ?: c.id
            byTop[top] = (byTop[top] ?: 0.0) + t.base
        }
        return limited.mapNotNull { c -> byTop[c.id]?.takeIf { LimitAlerts.isOver(it, c.monthlyLimit) }?.let { c to it } }
    }

    /** What you spend in a normal month: the average of the last 3 finished cycles that had spending, else this cycle so far. */
    suspend fun monthlySpend(today: LocalDate = LocalDate.now()): Double {
        val p = prefs()
        var cycle = CycleMath.cycleFor(today, p.cycleStart)
        suspend fun total(c: Cycle) = dao.txns(c.start.toEpochDay(), c.nextStart.toEpochDay())
            .filter { it.type == TxType.EXPENSE && !it.spread }.sumOf { it.base }
        val past = ArrayList<Double>()
        repeat(3) {
            cycle = cycle.previous(p.cycleStart)
            total(cycle).takeIf { it > 0 }?.let { past += it }
        }
        if (past.isNotEmpty()) return past.average()
        return total(CycleMath.cycleFor(today, p.cycleStart))
    }

    // ---------- Backup ----------

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val f = app.getDatabasePath(AppDb.NAME)
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to that file")
        out.use { o -> f.inputStream().use { it.copyTo(o) } }
    }

    /** Saves everything as the portable JSON file that the web app can restore too. */
    suspend fun exportJsonTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = PortableBackup.write(PortableBackup.Data(
            dao.wallets(), dao.categories(), dao.allTxns(), dao.bills(), dao.installments(), dao.circles(), dao.loans(), dao.settings(),
        ))
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to that file")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Replaces all data with a portable JSON file (from this app or the web app). Returns false when the file isn't one. */
    private suspend fun importJsonText(text: String): Boolean {
        val d = PortableBackup.read(text) ?: return false
        val keep = dao.settings().filter { it.key !in PortableBackup.KEYS }
        db.withTransaction {
            val raw = db.openHelper.writableDatabase
            for (t in listOf("txns", "bills", "installments", "circles", "loans", "categories", "wallets", "settings")) raw.execSQL("DELETE FROM $t")
            d.wallets.forEach { dao.insertWallet(it) }
            d.categories.forEach { dao.insertCategory(it) }
            d.txns.forEach { dao.insertTxn(it) }
            d.bills.forEach { dao.insertBill(it) }
            d.installments.forEach { dao.insertInstallment(it) }
            d.circles.forEach { dao.insertCircle(it) }
            d.loans.forEach { dao.insertLoan(it) }
            (keep + d.settings).forEach { dao.put(it) }
        }
        return true
    }

    /** Replaces all data with portable JSON text (used by cloud sync). No restart needed. */
    suspend fun importPortable(text: String): Boolean = withContext(Dispatchers.IO) { importJsonText(text) }

    /** Replaces all data with a backup file. The app must restart after this. */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(app.cacheDir, "restore.db")
        val input = app.contentResolver.openInputStream(uri) ?: return@withContext false
        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            // Not a phone backup: it may be the JSON file from the web app (or "Save for web" on a phone).
            val ok = runCatching { importJsonText(tmp.readText(Charsets.UTF_8).removePrefix("\uFEFF")) }.getOrDefault(false)
            tmp.delete()
            return@withContext ok
        }
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
