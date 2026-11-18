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
) {
    fun rateOf(currency: String): Double = if (currency == baseCurrency) 1.0 else rates[currency] ?: 1.0
    val currencies: List<String> get() = listOf(baseCurrency) + rates.keys.sorted()

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
