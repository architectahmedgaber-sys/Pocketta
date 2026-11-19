package app.pocketta.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file shared with the web app: plain JSON, {"app":"pocketta","v":1,"settings":{...},"wallets":[...],...}.
 * The phone's own backup (.db) stays as it is; this is the file to use to move data between the phone and a browser.
 * It never holds the assistant key or anything that belongs to one device only (lock, update checks, backup file choice).
 */
object PortableBackup {
    private val BOOLS = listOf("onboarded", "budgetAlert", "autoRates", "monthEndReminder", "limitAlert")
    private val INTS = listOf("cycleStart", "salaryDay")
    private val LONGS = listOf("salaryWallet", "lastCategory", "lastWallet", "ratesFetchedAt")
    private val DOUBLES = listOf("salary", "savingsPerMonth", "goalAmount", "goalSaved", "piggyTarget", "livingBase")
    private val STRINGS = listOf("baseCurrency", "theme", "lang", "goalName", "ratesUpdated")
    private val MAPS = listOf("rates", "onlineRates", "prevOnlineRates", "gold", "prevGold")
    private val SPECIAL = listOf("autoCurrencies", "goals", "piggy", "goldBuys", "wealthHistory", "salarySteps", "livingOverrides")

    /** Setting keys a portable file carries. Other keys on the phone are left alone when restoring. */
    val KEYS: Set<String> = (BOOLS + INTS + LONGS + DOUBLES + STRINGS + MAPS + SPECIAL).toSet()

    class Data(
        val wallets: List<Wallet>, val categories: List<Category>, val txns: List<Txn>, val bills: List<Bill>,
        val installments: List<Installment>, val circles: List<CircleEntity>, val loans: List<Loan>,
        val settings: List<SettingRow>,
    )

    fun write(d: Data): String {
        val p = Prefs.from(d.settings)
        val o = JSONObject()
        o.put("app", "pocketta"); o.put("v", 1)
        o.put("exportedAt", java.time.Instant.now().toString())
        val s = JSONObject()
        s.put("onboarded", p.onboarded); s.put("cycleStart", p.cycleStart); s.put("baseCurrency", p.baseCurrency)
        s.put("salary", p.salary); s.put("salaryDay", p.salaryDay); s.put("salaryWallet", p.salaryWallet)
        s.put("savingsPerMonth", p.savingsPerMonth); s.put("budgetAlert", p.budgetAlert)
        s.put("rates", map(p.rates)); s.put("lastCategory", p.lastCategory); s.put("lastWallet", p.lastWallet)
        s.put("autoRates", p.autoRates); s.put("autoCurrencies", JSONArray(p.autoCurrencies))
        s.put("onlineRates", map(p.onlineRates)); s.put("prevOnlineRates", map(p.prevOnlineRates))
        s.put("gold", map(p.gold)); s.put("prevGold", map(p.prevGold))
        s.put("ratesUpdated", p.ratesUpdated); s.put("ratesFetchedAt", p.ratesFetchedAt)
        s.put("theme", p.theme); s.put("lang", p.lang)
        s.put("goalName", p.goalName); s.put("goalAmount", p.goalAmount); s.put("goalSaved", p.goalSaved)
        s.put("goals", JSONArray().also { a -> p.goals.forEach { g ->
            a.put(JSONObject().put("id", g.id).put("name", g.name).put("target", g.target).put("saved", g.saved).put("monthly", g.monthly))
        } })
        s.put("piggy", JSONArray().also { a -> p.piggy.forEach { a.put(JSONArray().put(it.epochDay).put(it.amount)) } })
        s.put("goldBuys", JSONArray().also { a -> p.goldBuys.forEach { b ->
            a.put(JSONObject().put("karat", b.karat).put("grams", b.grams).put("price", b.pricePerGram).put("day", b.epochDay))
        } })
        s.put("monthEndReminder", p.monthEndReminder)
        s.put("wealthHistory", JSONArray().also { a -> p.wealthHistory.forEach { a.put(JSONArray().put(it.epochDay).put(it.total)) } })
        s.put("piggyTarget", p.piggyTarget); s.put("limitAlert", p.limitAlert)
        s.put("salarySteps", JSONObject().also { m -> p.salarySteps.forEach { (k, v) -> m.put(k.toString(), v) } })
        s.put("livingBase", p.livingBase)
        s.put("livingOverrides", JSONObject().also { m -> p.livingOverrides.forEach { (k, v) -> m.put(k.toString(), v) } })
        o.put("settings", s)
        o.put("wallets", arr(d.wallets) { JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("currency", it.currency).put("opening", it.opening).put("sort", it.sort).put("archived", it.archived) })
        o.put("categories", arr(d.categories) { JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: JSONObject.NULL).put("kind", it.kind).put("monthlyLimit", it.monthlyLimit).put("sort", it.sort).put("archived", it.archived) })
        o.put("txns", arr(d.txns) { JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount).put("currency", it.currency).put("rate", it.rate).put("walletId", it.walletId).put("toWalletId", it.toWalletId ?: JSONObject.NULL).put("categoryId", it.categoryId ?: JSONObject.NULL).put("note", it.note).put("epochDay", it.epochDay).put("createdAt", it.createdAt).put("source", it.source).put("sourceId", it.sourceId).put("spread", it.spread) })
        o.put("bills", arr(d.bills) { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("currency", it.currency).put("frequency", it.frequency).put("everyN", it.everyN).put("anchorDay", it.anchorDay).put("nextDue", it.nextDue).put("categoryId", it.categoryId ?: JSONObject.NULL).put("walletId", it.walletId ?: JSONObject.NULL).put("remindDays", it.remindDays).put("variable", it.variable).put("kind", it.kind).put("active", it.active) })
        o.put("installments", arr(d.installments) { JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("provider", it.provider).put("amount", it.amount).put("total", it.total).put("paid", it.paid).put("firstDue", it.firstDue).put("walletId", it.walletId ?: JSONObject.NULL).put("remindDays", it.remindDays) })
        o.put("circles", arr(d.circles) { JSONObject().put("id", it.id).put("name", it.name).put("share", it.share).put("months", it.months).put("myTurn", it.myTurn).put("paid", it.paid).put("firstDue", it.firstDue).put("payoutReceived", it.payoutReceived).put("walletId", it.walletId ?: JSONObject.NULL).put("remindDays", it.remindDays) })
        o.put("loans", arr(d.loans) { JSONObject().put("id", it.id).put("person", it.person).put("direction", it.direction).put("amount", it.amount).put("repaid", it.repaid).put("epochDay", it.epochDay).put("dueDay", it.dueDay ?: JSONObject.NULL).put("note", it.note).put("closed", it.closed) })
        return o.toString()
    }

    /** Reads a portable file. Returns null when the text isn't one (not JSON, not a Pocketta file, or no wallets). */
    fun read(text: String): Data? = runCatching {
        val o = JSONObject(text)
        if (o.optString("app") != "pocketta" || !o.has("txns")) return null
        val wallets = list(o, "wallets") { Wallet(it.getLong("id"), it.getString("name"), it.optString("type", "CASH"), it.optString("currency", "EGP"), it.optDouble("opening", 0.0), it.optInt("sort", 0), it.optBoolean("archived", false)) }
        if (wallets.isEmpty()) return null
        Data(
            wallets,
            list(o, "categories") { Category(it.getLong("id"), it.getString("name"), lng(it, "parentId"), it.optString("kind", "EXPENSE"), it.optDouble("monthlyLimit", 0.0), it.optInt("sort", 0), it.optBoolean("archived", false)) },
            list(o, "txns") { Txn(it.getLong("id"), it.getString("type"), it.getDouble("amount"), it.optString("currency", "EGP"), it.optDouble("rate", 1.0).let { r -> if (r > 0) r else 1.0 }, it.getLong("walletId"), lng(it, "toWalletId"), lng(it, "categoryId"), it.optString("note", ""), it.getLong("epochDay"), it.optLong("createdAt", 0L), it.optString("source", ""), it.optLong("sourceId", 0L), it.optBoolean("spread", false)) },
            list(o, "bills") { Bill(it.getLong("id"), it.getString("name"), it.getDouble("amount"), it.optString("currency", "EGP"), it.optString("frequency", "MONTHLY"), it.optInt("everyN", 30), it.optInt("anchorDay", 1), it.getLong("nextDue"), lng(it, "categoryId"), lng(it, "walletId"), it.optInt("remindDays", 1), it.optBoolean("variable", false), it.optString("kind", "BILL"), it.optBoolean("active", true)) },
            list(o, "installments") { Installment(it.getLong("id"), it.getString("name"), it.optString("type", ""), it.optString("provider", ""), it.getDouble("amount"), it.getInt("total"), it.optInt("paid", 0), it.getLong("firstDue"), lng(it, "walletId"), it.optInt("remindDays", 1)) },
            list(o, "circles") { CircleEntity(it.getLong("id"), it.getString("name"), it.getDouble("share"), it.getInt("months"), it.getInt("myTurn"), it.optInt("paid", 0), it.getLong("firstDue"), it.optBoolean("payoutReceived", false), lng(it, "walletId"), it.optInt("remindDays", 1)) },
            list(o, "loans") { Loan(it.getLong("id"), it.getString("person"), it.getString("direction"), it.getDouble("amount"), it.optDouble("repaid", 0.0), it.getLong("epochDay"), lng(it, "dueDay"), it.optString("note", ""), it.optBoolean("closed", false)) },
            settings(o.optJSONObject("settings") ?: JSONObject()),
        )
    }.getOrNull()

    private fun settings(s: JSONObject): List<SettingRow> {
        val rows = ArrayList<SettingRow>()
        fun put(k: String, v: String) { rows += SettingRow(k, v) }
        for (k in BOOLS) if (s.has(k)) put(k, s.optBoolean(k).toString())
        for (k in INTS) if (s.has(k)) put(k, s.optInt(k).toString())
        for (k in LONGS) if (s.has(k)) put(k, s.optLong(k).toString())
        for (k in DOUBLES) if (s.has(k)) put(k, s.optDouble(k).toString())
        for (k in STRINGS) if (s.has(k)) put(k, s.optString(k))
        for (k in MAPS) s.optJSONObject(k)?.let { m ->
            put(k, Prefs.formatRates(m.keys().asSequence().mapNotNull { c -> m.optDouble(c).takeIf { v -> v > 0 }?.let { v -> c to v } }.toMap()))
        }
        s.optJSONArray("autoCurrencies")?.let { a -> put("autoCurrencies", (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.joinToString(",")) }
        s.optJSONArray("goals")?.let { a ->
            put("goals", app.pocketta.core.GoalList.format((0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { g ->
                app.pocketta.core.SavingsGoal(g.optLong("id"), g.optString("name"), g.optDouble("target", 0.0), g.optDouble("saved", 0.0), g.optDouble("monthly", 0.0))
            }))
        }
        s.optJSONArray("piggy")?.let { a ->
            put("piggy", app.pocketta.core.Piggy.format((0 until a.length()).mapNotNull { a.optJSONArray(it) }.map { app.pocketta.core.PiggyEntry(it.optLong(0), it.optDouble(1)) }))
        }
        s.optJSONArray("goldBuys")?.let { a ->
            put("goldBuys", app.pocketta.core.Gold.format((0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { g ->
                app.pocketta.core.GoldBuy(g.optString("karat"), g.optDouble("grams", 0.0), g.optDouble("price", 0.0), g.optLong("day", 0L))
            }))
        }
        s.optJSONArray("wealthHistory")?.let { a ->
            put("wealthHistory", app.pocketta.core.WealthLog.format((0 until a.length()).mapNotNull { a.optJSONArray(it) }.map { app.pocketta.core.WealthPoint(it.optLong(0), it.optDouble(1)) }))
        }
        for (k in listOf("salarySteps", "livingOverrides")) s.optJSONObject(k)?.let { m ->
            put(k, app.pocketta.core.MonthValues.format(m.keys().asSequence().mapNotNull { ym -> ym.toIntOrNull()?.let { it to m.optDouble(ym) } }.toMap()))
        }
        return rows
    }

    private fun map(m: Map<String, Double>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
    private fun <T> arr(l: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> l.forEach { a.put(f(it)) } }
    private fun <T> list(o: JSONObject, k: String, f: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }
    private fun lng(o: JSONObject, k: String): Long? = if (o.isNull(k) || !o.has(k)) null else o.getLong(k)
}
