package app.pocketta.core

import java.time.LocalDate

/** One line on the Commitments overview: a payment (or income) with a date. */
enum class CKind { BILL, SUB, INSTALLMENT, CARD, CIRCLE, LOAN, INCOME }

enum class CFilter { ALL, BILLS, INSTALLMENTS, CIRCLE, CARDS, INCOME }

data class CItem(
    val kind: CKind,
    val refId: Long,
    val name: String,
    val sub: String,
    val amount: Double,
    val date: LocalDate,
    val paid: Boolean,
    /** Money coming to you (salary, a loan you gave) instead of money you pay. */
    val incoming: Boolean = false,
    /** The "Paid" button can be shown (it is the next unpaid payment of that item). */
    val payable: Boolean = false,
)

object Commitments {
    fun matches(f: CFilter, i: CItem): Boolean = when (f) {
        CFilter.ALL -> true
        CFilter.BILLS -> i.kind == CKind.BILL || i.kind == CKind.SUB
        CFilter.INSTALLMENTS -> i.kind == CKind.INSTALLMENT
        CFilter.CIRCLE -> i.kind == CKind.CIRCLE
        CFilter.CARDS -> i.kind == CKind.CARD
        CFilter.INCOME -> i.incoming
    }

    /** What is still to pay: unpaid items that are not income. */
    fun leftToPay(items: List<CItem>): Double = items.filter { !it.paid && !it.incoming }.sumOf { it.amount }

    fun leftCount(items: List<CItem>): Int = items.count { !it.paid && !it.incoming }

    fun byDay(items: List<CItem>): Map<LocalDate, List<CItem>> = items.groupBy { it.date }

    /** Late ones first, then the rest in date order. */
    fun late(items: List<CItem>, today: LocalDate): List<CItem> =
        items.filter { !it.paid && it.date.isBefore(today) }.sortedBy { it.date }

    fun later(items: List<CItem>, today: LocalDate): List<CItem> =
        items.filter { !it.paid && !it.date.isBefore(today) }.sortedBy { it.date }

    fun done(items: List<CItem>): List<CItem> = items.filter { it.paid }.sortedByDescending { it.date }
}

/**
 * The little picture next to a payment. For a known service it is a coloured square with a letter
 * (our own marks, not the official logos); other names get an emoji by topic, then by kind.
 */
data class Badge(val letter: String?, val color: Long, val emoji: String)

object Badges {
    private class Brand(val letter: String, val color: Long, val keys: List<String>)

    private val brands = listOf(
        Brand("N", 0xFFE50914, listOf("netflix", "نتفليكس", "نتفلكس")),
        Brand("S", 0xFF00B386, listOf("shahid", "شاهد")),
        Brand("Y", 0xFFFF0000, listOf("youtube", "يوتيوب")),
        Brand("S", 0xFF1DB954, listOf("spotify", "سبوتيفاي", "سبوتفاي")),
        Brand("A", 0xFF6C2BD9, listOf("anghami", "انغامي", "انجامي")),
        Brand("D+", 0xFF113CCF, listOf("disney", "ديزني")),
        Brand("O+", 0xFF222B45, listOf("osn", "اوسن")),
        Brand("C", 0xFFD97757, listOf("claude", "كلود")),
        Brand("G", 0xFF10A37F, listOf("chatgpt", "openai", "شات جي بي تي", "شات جيبيتي")),
        Brand("G", 0xFF4285F4, listOf("gemini", "جيميني", "جمناي")),
        Brand("N", 0xFF111111, listOf("notion", "نوشن")),
        Brand("C", 0xFF00B8C4, listOf("canva", "كانفا")),
        Brand("A", 0xFFFA0F00, listOf("adobe", "ادوبي", "creative cloud")),
        Brand("i", 0xFF3693F3, listOf("icloud", "ايكلاود", "ايكلود")),
        Brand("G1", 0xFF34A853, listOf("google one", "جوجل ون", "google drive", "جوجل درايف")),
        Brand("M", 0xFFD83B01, listOf("microsoft", "office 365", "m365", "مايكروسوفت")),
        Brand("P+", 0xFF003791, listOf("playstation", "ps plus", "psn", "بلايستيشن", "بلاي ستيشن")),
        Brand("X", 0xFF107C10, listOf("xbox", "اكس بوكس")),
        Brand("S", 0xFF1B2838, listOf("steam", "ستيم")),
        Brand("V", 0xFFE60000, listOf("vodafone", "فودافون")),
        Brand("O", 0xFFFF7900, listOf("orange", "اورنج")),
        Brand("e", 0xFF6CBE45, listOf("etisalat", "اتصالات")),
        Brand("WE", 0xFF5B2D90, listOf("we", "وي")),
    )

    private val topics: List<Pair<List<String>, String>> = listOf(
        listOf("electric", "كهربا", "كهرباء") to "⚡",
        listOf("water", "مياه", "ميه", "مية") to "💧",
        listOf("gas", "غاز") to "🔥",
        listOf("internet", "wifi", "انترنت", "نت") to "🌐",
        listOf("rent", "ايجار", "إيجار") to "🔑",
        listOf("school", "tuition", "مدرسه", "مصاريف", "حضانه") to "🎓",
        listOf("club", "gym", "نادي", "جيم") to "🏋️",
        listOf("insurance", "تامين") to "🛡️",
        listOf("mobile", "phone", "موبايل", "كارت شحن") to "📱",
        listOf("doctor", "clinic", "دكتور", "عياده") to "🩺",
        listOf("garage", "parking", "جراج") to "🅿️",
        listOf("zakat", "charity", "زكاه", "صدقه") to "🤲",
        listOf("salary", "payroll", "مرتب", "مرتبي", "راتب") to "💰",
    )

    private fun kindEmoji(kind: CKind): String = when (kind) {
        CKind.BILL -> "🧾"
        CKind.SUB -> "🔁"
        CKind.INSTALLMENT -> "📆"
        CKind.CARD -> "💳"
        CKind.CIRCLE -> "🤝"
        CKind.LOAN -> "💸"
        CKind.INCOME -> "💰"
    }

    /** Lower case, no diacritics, one spelling for the Arabic letters people mix up. */
    fun normalize(s: String): String = buildString {
        for (ch in s.lowercase()) {
            when (ch) {
                'أ', 'إ', 'آ' -> append('ا')
                'ى' -> append('ي')
                'ة' -> append('ه')
                in 'ً'..'ٟ', 'ـ' -> {}
                else -> append(ch)
            }
        }
    }

    private fun hit(text: String, tokens: List<String>, rawKey: String): Boolean {
        val key = normalize(rawKey)
        return if (key.length <= 3) key in tokens else text.contains(key)
    }

    fun forName(name: String, kind: CKind): Badge {
        val text = normalize(name)
        val tokens = text.split(Regex("[^\\p{L}\\p{N}+]+")).filter { it.isNotEmpty() }
        if (kind != CKind.INCOME) {
            brands.firstOrNull { b -> b.keys.any { hit(text, tokens, it) } }?.let { return Badge(it.letter, it.color, kindEmoji(kind)) }
        }
        topics.firstOrNull { (keys, _) -> keys.any { hit(text, tokens, it) } }?.let { return Badge(null, 0, it.second) }
        return Badge(null, 0, kindEmoji(kind))
    }
}
