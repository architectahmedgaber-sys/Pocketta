package app.pocketta.core

import kotlin.math.roundToLong

/** Alert levels for category limits: 0 none, 80 near, 100 over. Compared in whole cents. */
object LimitLevels {
    fun level(spent: Double, limit: Double): Int {
        if (limit <= 0) return 0
        val sc = (spent * 100).roundToLong()
        val lc = (limit * 100).roundToLong()
        return when {
            sc > lc -> 100
            sc * 5 >= lc * 4 -> 80
            else -> 0
        }
    }

    data class State(val day: Long, val levels: Map<Long, Int>)
    data class Fresh(val id: Long, val level: Int)
    data class Item(val id: Long, val spent: Double, val limit: Double)
    data class Result(val fresh: List<Fresh>, val state: String)

    /** "<day>|<id>:<level>,...". Old "<day>|1,2" (no colon) means level 100. Bad text gives day 0 and nothing. */
    fun parse(s: String): State {
        val parts = s.split('|', limit = 2)
        val day = parts.getOrNull(0)?.toLongOrNull() ?: return State(0, emptyMap())
        val levels = HashMap<Long, Int>()
        for (p in (parts.getOrNull(1) ?: "").split(',')) {
            if (p.isEmpty()) continue
            val kv = p.split(':')
            val id = kv[0].toLongOrNull() ?: continue
            levels[id] = if (kv.size < 2) 100 else (kv[1].toIntOrNull() ?: 100)
        }
        return State(day, levels)
    }

    fun format(day: Long, levels: Map<Long, Int>): String =
        "$day|" + levels.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }

    /** Categories whose level went up since the last alert in this cycle. A new cycle starts from nothing. */
    fun fresh(cycleDay: Long, state: String, items: List<Item>): Result {
        val cur = parse(state)
        val known = HashMap<Long, Int>(if (cur.day == cycleDay) cur.levels else emptyMap())
        val out = ArrayList<Fresh>()
        for (it in items) {
            val lv = level(it.spent, it.limit)
            if (lv > (known[it.id] ?: 0)) { out += Fresh(it.id, lv); known[it.id] = lv }
        }
        return Result(out, format(cycleDay, known))
    }

    /** For the Budgets card on Month: only categories with a limit. */
    data class Summary(val total: Int, val over: Int, val near: Int, val ok: Int)
    fun summary(items: List<Item>): Summary {
        val l = items.filter { it.limit > 0 }
        val over = l.count { level(it.spent, it.limit) == 100 }
        val near = l.count { level(it.spent, it.limit) == 80 }
        return Summary(l.size, over, near, l.size - over - near)
    }
}
