package app.pocketta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Wallet::class, Category::class, Txn::class, Bill::class, Installment::class, CircleEntity::class, Loan::class, SettingRow::class],
    version = 2,
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

        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, NAME).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        /** Closes the database so its file can be replaced by a backup. */
        fun closeForRestore() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
