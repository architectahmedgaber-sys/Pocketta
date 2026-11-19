package app.pocketta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyDao {
    // Wallets
    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY sort, id") fun walletsFlow(): Flow<List<Wallet>>
    @Query("SELECT * FROM wallets ORDER BY sort, id") suspend fun wallets(): List<Wallet>
    @Insert suspend fun insertWallet(w: Wallet): Long
    @Update suspend fun updateWallet(w: Wallet)

    // Categories
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sort, id") fun categoriesFlow(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY sort, id") suspend fun categories(): List<Category>
    @Insert suspend fun insertCategory(c: Category): Long
    @Update suspend fun updateCategory(c: Category)

    // Transactions
    @Query("SELECT * FROM txns WHERE epochDay >= :from AND epochDay < :to ORDER BY epochDay DESC, createdAt DESC")
    fun txnsFlow(from: Long, to: Long): Flow<List<Txn>>
    @Query("SELECT * FROM txns WHERE epochDay >= :from AND epochDay < :to")
    suspend fun txns(from: Long, to: Long): List<Txn>
    @Query("SELECT * FROM txns") suspend fun allTxns(): List<Txn>
    @Query("SELECT * FROM txns") fun allTxnsFlow(): Flow<List<Txn>>
    @Query("SELECT * FROM txns WHERE id = :id") suspend fun txn(id: Long): Txn?
    @Query("SELECT * FROM txns WHERE source = :source AND sourceId = :sourceId ORDER BY epochDay DESC LIMIT :limit")
    suspend fun txnsFor(source: String, sourceId: Long, limit: Int): List<Txn>
    @Query("SELECT * FROM txns WHERE type = :type AND note = :note COLLATE NOCASE AND categoryId IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastWithNote(type: String, note: String): Txn?
    @Query("SELECT COUNT(*) FROM txns WHERE source = :source AND epochDay >= :from AND epochDay < :to")
    suspend fun countSource(source: String, from: Long, to: Long): Int
    @Query("DELETE FROM txns WHERE source = :source AND sourceId = :sourceId")
    suspend fun deleteTxnsFor(source: String, sourceId: Long)
    @Insert suspend fun insertTxn(t: Txn): Long
    @Update suspend fun updateTxn(t: Txn)
    @Delete suspend fun deleteTxn(t: Txn)

    // Bills
    @Query("SELECT * FROM bills WHERE active = 1 ORDER BY nextDue") fun billsFlow(): Flow<List<Bill>>
    @Query("SELECT * FROM bills WHERE active = 1") suspend fun bills(): List<Bill>
    @Query("SELECT * FROM bills WHERE id = :id") suspend fun bill(id: Long): Bill?
    @Insert suspend fun insertBill(b: Bill): Long
    @Update suspend fun updateBill(b: Bill)
    @Delete suspend fun deleteBill(b: Bill)

    // Installments
    @Query("SELECT * FROM installments ORDER BY firstDue") fun installmentsFlow(): Flow<List<Installment>>
    @Query("SELECT * FROM installments") suspend fun installments(): List<Installment>
    @Query("SELECT * FROM installments WHERE id = :id") suspend fun installment(id: Long): Installment?
    @Insert suspend fun insertInstallment(i: Installment): Long
    @Update suspend fun updateInstallment(i: Installment)
    @Delete suspend fun deleteInstallment(i: Installment)

    // Circles
    @Query("SELECT * FROM circles ORDER BY firstDue") fun circlesFlow(): Flow<List<CircleEntity>>
    @Query("SELECT * FROM circles") suspend fun circles(): List<CircleEntity>
    @Query("SELECT * FROM circles WHERE id = :id") suspend fun circle(id: Long): CircleEntity?
    @Insert suspend fun insertCircle(c: CircleEntity): Long
    @Update suspend fun updateCircle(c: CircleEntity)
    @Delete suspend fun deleteCircle(c: CircleEntity)

    // Loans
    @Query("SELECT * FROM loans ORDER BY closed, epochDay DESC") fun loansFlow(): Flow<List<Loan>>
    @Query("SELECT * FROM loans") suspend fun loans(): List<Loan>
    @Query("SELECT * FROM loans WHERE id = :id") suspend fun loan(id: Long): Loan?
    @Insert suspend fun insertLoan(l: Loan): Long
    @Update suspend fun updateLoan(l: Loan)
    @Delete suspend fun deleteLoan(l: Loan)

    // Settings
    @Query("SELECT * FROM settings") fun settingsFlow(): Flow<List<SettingRow>>
    @Query("SELECT * FROM settings") suspend fun settings(): List<SettingRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: SettingRow)
}
