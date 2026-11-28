package app.pocketta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Wallet::class, Category::class, Txn::class, Bill::class, Installment::class, CircleEntity::class, Loan::class, LoanInstallment::class, SettingRow::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        const val NAME = "pocketta.db"

        /** 1.14: tags on transactions. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txns ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 1.20: need or want, how it was paid, and where it was bought. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txns ADD COLUMN need TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE txns ADD COLUMN how TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE txns ADD COLUMN place TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 1.23: loans get a repayment schedule. New table of instalments; every open loan that had a due date gets one
         * instalment for what is left, so nothing changes in the numbers. Loans without a date get none (the app asks for a date).
         * Existing loans keep how they were recorded: [recordedIncome] is true when their first money record exists.
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN recordedIncome INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE loans ADD COLUMN scheduleType TEXT NOT NULL DEFAULT 'LUMP'")
                db.execSQL("ALTER TABLE loans ADD COLUMN countInBudget INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "UPDATE loans SET recordedIncome = CASE WHEN EXISTS (SELECT 1 FROM txns WHERE txns.source = 'LOAN' AND txns.sourceId = loans.id " +
                        "AND txns.type = CASE loans.direction WHEN 'LENT' THEN 'EXPENSE' ELSE 'INCOME' END) THEN 1 ELSE 0 END"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS loan_installments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanId INTEGER NOT NULL, " +
                        "dueDay INTEGER NOT NULL, amount REAL NOT NULL, paidAmount REAL NOT NULL, paidDay INTEGER, walletId INTEGER, " +
                        "FOREIGN KEY(loanId) REFERENCES loans(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_installments_loanId ON loan_installments(loanId)")
                db.execSQL(
                    "INSERT INTO loan_installments (loanId, dueDay, amount, paidAmount) " +
                        "SELECT id, dueDay, amount - repaid, 0 FROM loans WHERE closed = 0 AND dueDay IS NOT NULL AND amount - repaid > 0.004"
                )
            }
        }

        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        /** Closes the database so its file can be replaced by a backup. */
        fun closeForRestore() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
