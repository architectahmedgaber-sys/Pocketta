package app.pocketta.data

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
) {
    fun rateOf(currency: String): Double =
        if (currency == baseCurrency) 1.0 else rates[currency] ?: (if (autoRates) onlineRates[currency] else null) ?: 1.0

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
