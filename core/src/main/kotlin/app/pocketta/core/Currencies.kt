package app.pocketta.core

/** Countries, their money, and how many decimals that money uses. One list for the first-run picker and Settings. */
object Countries {
    data class Country(val code: String, val ar: String, val en: String, val currency: String, val decimals: Int, val flag: String)

    val all: List<Country> = listOf(
        Country("EG", "مصر", "Egypt", "EGP", 2, "\uD83C\uDDEA\uD83C\uDDEC"),
        Country("JO", "الأردن", "Jordan", "JOD", 3, "\uD83C\uDDEF\uD83C\uDDF4"),
        Country("PS", "فلسطين", "Palestine", "ILS", 2, "\uD83C\uDDF5\uD83C\uDDF8"),
        Country("LY", "ليبيا", "Libya", "LYD", 3, "\uD83C\uDDF1\uD83C\uDDFE"),
        Country("TN", "تونس", "Tunisia", "TND", 3, "\uD83C\uDDF9\uD83C\uDDF3"),
        Country("DZ", "الجزائر", "Algeria", "DZD", 2, "\uD83C\uDDE9\uD83C\uDDFF"),
        Country("MA", "المغرب", "Morocco", "MAD", 2, "\uD83C\uDDF2\uD83C\uDDE6"),
        Country("MR", "موريتانيا", "Mauritania", "MRU", 2, "\uD83C\uDDF2\uD83C\uDDF7"),
        Country("SD", "السودان", "Sudan", "SDG", 2, "\uD83C\uDDF8\uD83C\uDDE9"),
        Country("SA", "السعودية", "Saudi Arabia", "SAR", 2, "\uD83C\uDDF8\uD83C\uDDE6"),
        Country("AE", "الإمارات", "United Arab Emirates", "AED", 2, "\uD83C\uDDE6\uD83C\uDDEA"),
        Country("KW", "الكويت", "Kuwait", "KWD", 3, "\uD83C\uDDF0\uD83C\uDDFC"),
        Country("QA", "قطر", "Qatar", "QAR", 2, "\uD83C\uDDF6\uD83C\uDDE6"),
        Country("BH", "البحرين", "Bahrain", "BHD", 3, "\uD83C\uDDE7\uD83C\uDDED"),
        Country("OM", "عُمان", "Oman", "OMR", 3, "\uD83C\uDDF4\uD83C\uDDF2"),
        Country("IQ", "العراق", "Iraq", "IQD", 0, "\uD83C\uDDEE\uD83C\uDDF6"),
        Country("LB", "لبنان", "Lebanon", "LBP", 0, "\uD83C\uDDF1\uD83C\uDDE7"),
        Country("SY", "سوريا", "Syria", "SYP", 0, "\uD83C\uDDF8\uD83C\uDDFE"),
        Country("YE", "اليمن", "Yemen", "YER", 0, "\uD83C\uDDFE\uD83C\uDDEA"),
    )

    /** Choices that are not a country: the dollar and the euro. Code is "CUR:USD". */
    val others: List<Country> = listOf(
        Country("CUR:USD", "دولار أمريكي", "US dollar", "USD", 2, "\uD83C\uDF0D"),
        Country("CUR:EUR", "يورو", "Euro", "EUR", 2, "\uD83C\uDF0D"),
    )

    val choices: List<Country> get() = all + others

    /** Decimals a currency uses (2 for any code we do not know). */
    fun decimalsOf(currency: String): Int = choices.firstOrNull { it.currency == currency }?.decimals ?: 2

    /** The saved country; old data has none, so it is read from the currency (Egypt for EGP). */
    fun find(code: String, currency: String): Country? =
        all.firstOrNull { it.code == code } ?: all.firstOrNull { it.currency == currency }
}
