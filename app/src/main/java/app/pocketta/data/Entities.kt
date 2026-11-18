package app.pocketta.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object TxType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
}

/** Where a transaction came from, so paying a bill or an installment can be traced back. */
object Source {
    const val NONE = ""
    const val SALARY = "SALARY"
    const val BILL = "BILL"
    const val INSTALLMENT = "INSTALLMENT"
    const val CIRCLE = "CIRCLE"
    const val LOAN = "LOAN"
}

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CASH, BANK, MOBILE, CARD, OTHER */
    val type: String = "CASH",
    val currency: String = "EGP",
    val opening: Double = 0.0,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    /** EXPENSE or INCOME */
    val kind: String = TxType.EXPENSE,
    /** Optional monthly limit, 0 = none. */
    val monthlyLimit: Double = 0.0,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Entity(tableName = "txns", indices = [Index("epochDay"), Index("categoryId"), Index("walletId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    /** Amount in [currency]. Always positive. */
    val amount: Double,
    val currency: String = "EGP",
    /** Base currency (EGP) per 1 unit of [currency] at the time. */
    val rate: Double = 1.0,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = Source.NONE,
    val sourceId: Long = 0,
    /** Paid from money set aside every month (quarterly/yearly bills), so it does not hit this month again. */
    val spread: Boolean = false,
) {
    val base: Double get() = amount * rate
}

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val currency: String = "EGP",
    /** Frequency enum name from core. */
    val frequency: String = "MONTHLY",
    val everyN: Int = 30,
    val anchorDay: Int = 1,
    /** Next unpaid due date (epoch day). */
    val nextDue: Long,
    val categoryId: Long? = null,
    val walletId: Long? = null,
    val remindDays: Int = 1,
    /** Amount changes every time (electricity): use the average of the last 3 payments. */
    val variable: Boolean = false,
    /** BILL or SUB */
    val kind: String = "BILL",
    val active: Boolean = true,
)

@Entity(tableName = "installments")
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Consumer finance, Credit card, or a custom type. */
    val type: String,
    val provider: String = "",
    val amount: Double,
    val total: Int,
    val paid: Int = 0,
    val firstDue: Long,
    val walletId: Long? = null,
    val remindDays: Int = 1,
)

@Entity(tableName = "circles")
data class CircleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val share: Double,
    val months: Int,
    val myTurn: Int,
    val paid: Int = 0,
    val firstDue: Long,
    val payoutReceived: Boolean = false,
    val walletId: Long? = null,
    val remindDays: Int = 1,
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    /** LENT (they owe me) or BORROWED (I owe them) */
    val direction: String,
    val amount: Double,
    val repaid: Double = 0.0,
    val epochDay: Long,
    val dueDay: Long? = null,
    val note: String = "",
    val closed: Boolean = false,
) {
    val remaining: Double get() = (amount - repaid).coerceAtLeast(0.0)
    val lent: Boolean get() = direction == "LENT"
}

@Entity(tableName = "settings")
data class SettingRow(
    @PrimaryKey val key: String,
    val value: String,
)
