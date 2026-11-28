package app.pocketta.core

/** An extra savings goal (the main one lives in the old goalName/goalAmount/goalSaved settings). [monthly] is what you put towards it each month. */
data class SavingsGoal(val id: Long, val name: String, val target: Double, val saved: Double, val monthly: Double, /** Optional target date (epoch day). */ val deadline: Long? = null) {
    val fraction: Double get() = if (target <= 0) 0.0 else (saved / target).coerceIn(0.0, 1.0)
    val reached: Boolean get() = target > 0 && saved >= target
    /** Whole months left at the monthly amount: 0 = reached, null = no monthly amount so no date. */
    val monthsLeft: Int? get() = Planning.monthsToGoal(target, saved, monthly)
}

object GoalList {
    /** One goal per line, fields split by a tab: id, name, target, saved, monthly, deadline (epoch day, empty = none; 5-field lines still read). Bad lines are skipped. */
    fun parse(s: String): List<SavingsGoal> = s.split('\n').mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 5 && p.size != 6) return@mapNotNull null
        val id = p[0].toLongOrNull() ?: return@mapNotNull null
        val t = p[2].toDoubleOrNull() ?: return@mapNotNull null
        val sv = p[3].toDoubleOrNull() ?: return@mapNotNull null
        val m = p[4].toDoubleOrNull() ?: return@mapNotNull null
        val dl = if (p.size == 6) p[5].toLongOrNull() else null
        SavingsGoal(id, p[1], t, sv, m, dl)
    }

    fun format(list: List<SavingsGoal>): String = list.joinToString("\n") {
        listOf(it.id, it.name.replace('\t', ' ').replace('\n', ' ').trim(), it.target, it.saved, it.monthly, it.deadline ?: "").joinToString("\t")
    }

    fun nextId(list: List<SavingsGoal>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

    /** Goals ordered by how soon they finish: reached ones last, then soonest first, goals with no date at the end. */
    fun byFinish(list: List<SavingsGoal>): List<SavingsGoal> =
        list.sortedWith(compareBy({ if (it.reached) 1 else 0 }, { it.monthsLeft ?: Int.MAX_VALUE }))

    /** How many months sooner [goal] finishes if [extra] more goes into it each month, or null when it has no date now. */
    fun monthsSoonerWith(target: Double, saved: Double, monthly: Double, extra: Double): Int? {
        val before = Planning.monthsToGoal(target, saved, monthly) ?: return null
        val after = Planning.monthsToGoal(target, saved, monthly + extra) ?: return null
        return (before - after).coerceAtLeast(0)
    }
}
