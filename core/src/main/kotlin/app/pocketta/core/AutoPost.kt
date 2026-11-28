package app.pocketta.core

import java.time.LocalDate

object AutoPost {
    data class Item(val billId: Long, val day: LocalDate)
    data class BillIn(val id: Long, val active: Boolean, val autoPost: Boolean, val variable: Boolean, val nextDue: LocalDate, val freq: Frequency, val everyN: Int, val anchorDay: Int)

    /** Every occurrence due on or before [today], dated on its due date, at most 12 per bill. Bill order is kept. */
    fun plan(bills: List<BillIn>, today: LocalDate): List<Item> {
        val out = ArrayList<Item>()
        for (b in bills) {
            if (!b.active || !b.autoPost || b.variable) continue
            var due = b.nextDue
            var n = 0
            while (!due.isAfter(today) && n < 12) {
                out += Item(b.id, due)
                due = Recurrence.next(due, b.freq, b.everyN, b.anchorDay)
                n++
            }
        }
        return out
    }
}
