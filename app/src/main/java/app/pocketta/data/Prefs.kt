package app.pocketta.data

import app.pocketta.core.WealthLog
import app.pocketta.core.WealthPoint

/** App settings, stored as rows in the database so one backup file holds everything. */
data class Prefs(
    val onboarded: Boolean = false,
    val cycleStart: Int = 1,
    val baseCurrency: String = "EGP",
    val salary: Double = 0.0,
    val salaryDay: Int = 1,
    val salaryWallet: Long = 0,
    val savingsPerMonth: Double = 0.0,
    val lock: Boolean = false,
    val remindHour: Int = 9,
    val budgetAlert: Boolean = true,
    /** Extra currencies with a manual rate to the base currency, e.g. USD -> 50.5 */
    val rates: Map<String, Double> = emptyMap(),
    val lastCategory: Long = 0,
    val lastWallet: Long = 0,
    /** OFF, DAILY or WEEKLY */
    val autoBackup: String = "OFF",
    val backupUri: String = "",
    val lastBackup: Long = 0,
    val backupReminder: Boolean = true,
    /** Use the daily rates from the internet (your own rates still win). */
    val autoRates: Boolean = true,
    /** Currencies picked from the online list, e.g. USD, EUR. */
    val autoCurrencies: List<String> = listOf("USD", "EUR", "SAR"),
    val onlineRates: Map<String, Double> = emptyMap(),
    /** Gold price per gram in the base currency, by karat ("24", "21", "18"). */
    val gold: Map<String, Double> = emptyMap(),
    val ratesUpdated: String = "",
    val ratesFetchedAt: Long = 0,
    val updateCheckedAt: Long = 0,
    val updateVersion: String = "",
    val updateUrl: String = "",
    /** AUTO (follow the phone), LIGHT or DARK. */
    val theme: String = "AUTO",
    /** EN or AR. */
    val lang: String = "EN",
    /** A morning notification with today's number and the dollar and gold. */
    val morning: Boolean = true,
    val goalName: String = "",
    val goalAmount: Double = 0.0,
    val goalSaved: Double = 0.0,
    /** Extra savings goals besides the main one above. */
    val goals: List<app.pocketta.core.SavingsGoal> = emptyList(),
    /** Everything put into the piggy bank, and taken out, with the day. */
    val piggy: List<app.pocketta.core.PiggyEntry> = emptyList(),
    /** Yesterday's rates and gold, to show an up or down arrow. */
    val prevOnlineRates: Map<String, Double> = emptyMap(),
    val prevGold: Map<String, Double> = emptyMap(),
    /** Gold you bought, to show profit or loss against today's price. */
    val goldBuys: List<app.pocketta.core.GoldBuy> = emptyList(),
    /** At the end of the month, suggest moving what is left into the savings goal. */
    val monthEndReminder: Boolean = true,
    /** Total of everything you own, saved once a day, for the wealth view. */
    val wealthHistory: List<WealthPoint> = emptyList(),
    /** The last price-rise alert sent, "cycleStartDay:category", so each one is sent once. */
    val riseAlerted: String = "",
    /** The newest version a notification was already shown for, so each release notifies once. */
    val updateNotified: String = "",
    /** Switches for each kind of notification (all on by default). */
    val notifyDue: Boolean = true,
    val notifyGameya: Boolean = true,
    val notifyRise: Boolean = true,
    val notifyUpdate: Boolean = true,
    /** Monthly amount to put in the piggy bank (0 = no target). */
    val piggyTarget: Double = 0.0,
    /** Tell me when a category passes its monthly limit. */
    val limitAlert: Boolean = true,
    /** "cycleStartDay|id,id": the categories already announced this cycle. */
    val limitAlerted: String = "",
    /** Suggest an expense from bank text messages (off until you turn it on and allow it). */
    val smsSuggest: Boolean = false,
    /** Recent message fingerprints, so one message is suggested once. */
    val smsSeen: String = "",
    /** Raises: from this month on (year*12+month-1) the salary is this much. */
    val salarySteps: Map<Int, Double> = emptyMap(),
    /** What a normal month of everyday living costs, for the month plan. */
    val livingBase: Double = 0.0,
    /** A different living cost for one month only. */
    val livingOverrides: Map<Int, Double> = emptyMap(),
) {
    fun rateOf(currency: String): Double =
        if (currency == baseCurrency) 1.0 else rates[currency] ?: (if (autoRates) onlineRates[currency] else null) ?: 1.0

    /** Change since yesterday, as a fraction (0.01 = up 1%), or null when yesterday's price isn't known. */
    fun rateChange(currency: String): Double? {
        val now = onlineRates[currency] ?: return null
        val before = prevOnlineRates[currency] ?: return null
        return (now - before) / before
    }

    fun goldChange(karat: String): Double? {
        val now = gold[karat] ?: return null
        val before = prevGold[karat] ?: return null
        return (now - before) / before
    }

    fun isManual(currency: String): Boolean = rates.containsKey(currency)

    val currencies: List<String>
        get() = (listOf(baseCurrency) + rates.keys.sorted() +
            (if (autoRates) autoCurrencies.filter { onlineRates.containsKey(it) } else emptyList())).distinct()

    companion object {
        fun from(rows: List<SettingRow>): Prefs {
            val m = rows.associate { it.key to it.value }
            fun d(k: String, def: Double) = m[k]?.toDoubleOrNull() ?: def
            fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
            fun l(k: String, def: Long) = m[k]?.toLongOrNull() ?: def
            fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
            return Prefs(
                onboarded = b("onboarded", false),
                cycleStart = i("cycleStart", 1),
                baseCurrency = m["baseCurrency"] ?: "EGP",
                salary = d("salary", 0.0),
                salaryDay = i("salaryDay", 1),
                salaryWallet = l("salaryWallet", 0),
                savingsPerMonth = d("savingsPerMonth", 0.0),
                lock = b("lock", false),
                remindHour = i("remindHour", 9),
                budgetAlert = b("budgetAlert", true),
                rates = parseRates(m["rates"].orEmpty()),
                lastCategory = l("lastCategory", 0),
                lastWallet = l("lastWallet", 0),
                autoBackup = m["autoBackup"] ?: "OFF",
                backupUri = m["backupUri"].orEmpty(),
                lastBackup = l("lastBackup", 0),
                backupReminder = b("backupReminder", true),
                autoRates = b("autoRates", true),
                autoCurrencies = m["autoCurrencies"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("USD", "EUR", "SAR"),
                onlineRates = parseRates(m["onlineRates"].orEmpty()),
                gold = parseRates(m["gold"].orEmpty()),
                ratesUpdated = m["ratesUpdated"].orEmpty(),
                ratesFetchedAt = l("ratesFetchedAt", 0),
                updateCheckedAt = l("updateCheckedAt", 0),
                updateVersion = m["updateVersion"].orEmpty(),
                updateUrl = m["updateUrl"].orEmpty(),
                theme = m["theme"] ?: "AUTO",
                lang = m["lang"] ?: "EN",
                morning = b("morning", true),
                goalName = m["goalName"].orEmpty(),
                goalAmount = d("goalAmount", 0.0),
                goalSaved = d("goalSaved", 0.0),
                goals = app.pocketta.core.GoalList.parse(m["goals"].orEmpty()),
                piggy = app.pocketta.core.Piggy.parse(m["piggy"].orEmpty()),
                prevOnlineRates = parseRates(m["prevOnlineRates"].orEmpty()),
                prevGold = parseRates(m["prevGold"].orEmpty()),
                goldBuys = app.pocketta.core.Gold.parse(m["goldBuys"].orEmpty()),
                monthEndReminder = b("monthEndReminder", true),
                wealthHistory = WealthLog.parse(m["wealthHistory"].orEmpty()),
                riseAlerted = m["riseAlerted"].orEmpty(),
                updateNotified = m["updateNotified"].orEmpty(),
                notifyDue = b("notifyDue", true),
                notifyGameya = b("notifyGameya", true),
                notifyRise = b("notifyRise", true),
                notifyUpdate = b("notifyUpdate", true),
                piggyTarget = d("piggyTarget", 0.0),
                limitAlert = b("limitAlert", true),
                limitAlerted = m["limitAlerted"].orEmpty(),
                smsSuggest = b("smsSuggest", false),
                smsSeen = m["smsSeen"].orEmpty(),
                salarySteps = app.pocketta.core.MonthValues.parse(m["salarySteps"].orEmpty()),
                livingBase = d("livingBase", 0.0),
                livingOverrides = app.pocketta.core.MonthValues.parse(m["livingOverrides"].orEmpty()),
            )
        }

        fun parseRates(s: String): Map<String, Double> = s.split(';').mapNotNull {
            val parts = it.split('=')
            val rate = parts.getOrNull(1)?.toDoubleOrNull()
            if (parts.size == 2 && parts[0].isNotBlank() && rate != null && rate > 0) parts[0].trim().uppercase() to rate else null
        }.toMap()

        fun formatRates(m: Map<String, Double>): String = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}
