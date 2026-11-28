package app.pocketta.core

object LockGrace {
    val OPTIONS = listOf(0, 30, 60, 300)
    /** [leftAtMs] = 0 means "never left": relock. */
    fun shouldRelock(leftAtMs: Long, nowMs: Long, graceSec: Int): Boolean =
        if (leftAtMs == 0L) true else nowMs - leftAtMs >= graceSec * 1000L
}
