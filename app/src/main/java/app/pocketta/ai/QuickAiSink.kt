package app.pocketta.ai

import app.pocketta.core.QuickAi
import app.pocketta.data.Loan
import app.pocketta.data.Repo
import app.pocketta.data.Txn
import app.pocketta.data.TxType

/**
 * Writes confirmed Quick AI proposals into the database. Only [QuickAi.Pending.confirm] calls [save]; the screen never calls it directly.
 * Loan repayments need an open loan with that person: [problem] tells the screen in advance when there is none, so it is not offered.
 */
class QuickAiSink(private val repo: Repo) : QuickAi.Sink {
    private fun nameMatches(a: String, b: String) = a.isNotBlank() && b.isNotBlank() && (a.equals(b, true) || a.contains(b, true) || b.contains(a, true))

    /** The open loan a repayment belongs to: the right direction, same person, oldest first. */
    suspend fun loanFor(p: QuickAi.Proposal): Loan? {
        val lent = p.type == "loan_repaid_received"
        val person = p.person ?: return null
        return repo.dao.loans().filter { !it.closed && it.remaining > 0.004 && it.lent == lent && nameMatches(it.person, person) }.minByOrNull { it.epochDay }
    }

    /** Null when the proposal can be saved, otherwise a short reason why not. */
    suspend fun problem(p: QuickAi.Proposal): String? = when (p.type) {
        "loan_received", "loan_given" -> if (p.person.isNullOrBlank()) "Who is the other person? Add it by hand." else null
        "loan_repaid_given", "loan_repaid_received" ->
            if (p.person.isNullOrBlank()) "Who paid? Add it by hand."
            else if (loanFor(p) == null) "No open loan with ${p.person} to settle. Add it by hand." else null
        else -> null
    }

    private suspend fun category(name: String, note: String, type: String): Long? {
        val all = repo.dao.categories().filter { it.kind == type && !it.archived }
        val byName = if (name.isBlank()) null else (all.firstOrNull { it.name.equals(name, true) } ?: all.firstOrNull { it.name.contains(name, true) || name.contains(it.name, true) })?.id
        return byName ?: repo.guessCategory(note, type) ?: repo.guessCategory(name, type)
    }

    override suspend fun save(p: QuickAi.Proposal) {
        val pr = repo.prefs()
        when (p.type) {
            "expense" -> repo.saveTxn(
                Txn(type = TxType.EXPENSE, amount = p.amountEgp, currency = pr.baseCurrency, rate = 1.0, walletId = repo.newRecordWallet(),
                    categoryId = category(p.category, p.note, TxType.EXPENSE) ?: pr.lastCategory.takeIf { it != 0L },
                    note = p.note.ifBlank { p.category }, epochDay = p.date.toEpochDay())
            )
            "income" -> repo.saveTxn(
                Txn(type = TxType.INCOME, amount = p.amountEgp, currency = pr.baseCurrency, rate = 1.0, walletId = repo.newRecordWallet(),
                    categoryId = category(p.category, p.note, TxType.INCOME) ?: repo.categoryNamed("Other income", TxType.INCOME),
                    note = p.note.ifBlank { p.category }, epochDay = p.date.toEpochDay())
            )
            "loan_received", "loan_given" -> repo.addLoan(
                p.person?.takeIf { it.isNotBlank() } ?: return, p.type == "loan_given", p.amountEgp, null, p.date, p.dueDate, p.note,
            )
            "loan_repaid_given", "loan_repaid_received" -> {
                val loan = loanFor(p) ?: return
                repo.loanPayment(loan, p.amountEgp, null, p.date)
            }
        }
    }
}
