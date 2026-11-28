package app.pocketta.core

import kotlin.math.roundToLong

object TxnFilter {
    data class T(val id: Long, val type: String, val base: Double, val note: String, val categoryId: Long?, val walletId: Long, val epochDay: Long, val need: String, val tags: List<String>)
    data class F(
        val q: String = "", val types: Set<String> = emptySet(), val catIds: Set<Long> = emptySet(), val walletId: Long = 0,
        val min: Double? = null, val max: Double? = null, val from: Long? = null, val to: Long? = null, val need: String = "", val tag: String = "",
    )
    data class Result(val list: List<T>, val total: Double)

    /** 60 -> "60", 60.5 -> "60.5", 60.25 -> "60.25". */
    fun amountText(v: Double): String {
        val c = (v * 100).roundToLong()
        return if (c % 100 == 0L) (c / 100).toString() else {
            val s = "${c / 100}.${Math.abs(c % 100).toString().padStart(2, '0')}"
            if (s.endsWith("0")) s.dropLast(1) else s
        }
    }

    fun apply(txns: List<T>, f: F, catName: (Long?) -> String): Result {
        val q = f.q.trim().lowercase()
        val list = txns.filter { t ->
            if (f.types.isNotEmpty() && t.type !in f.types) return@filter false
            if (f.catIds.isNotEmpty() && t.categoryId !in f.catIds) return@filter false
            if (f.walletId != 0L && t.walletId != f.walletId) return@filter false
            if (f.min != null && t.base < f.min) return@filter false
            if (f.max != null && t.base > f.max) return@filter false
            if (f.from != null && t.epochDay < f.from) return@filter false
            if (f.to != null && t.epochDay > f.to) return@filter false
            if (f.need.isNotEmpty() && t.need != f.need) return@filter false
            if (f.tag.isNotEmpty() && f.tag !in t.tags) return@filter false
            if (q.isNotEmpty()) {
                val hay = (listOf(t.note, catName(t.categoryId), t.tags.joinToString(" "), amountText(t.base)).joinToString(" ")).lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }.sortedWith(compareByDescending<T> { it.epochDay }.thenByDescending { it.id })
        val total = (list.filter { it.type == "EXPENSE" }.sumOf { it.base } * 100).roundToLong() / 100.0
        return Result(list, total)
    }
}
