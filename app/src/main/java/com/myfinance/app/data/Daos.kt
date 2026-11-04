package com.myfinance.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    @Insert
    suspend fun insert(t: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TransactionEntity>)

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName AND type = :type")
    suspend fun reassignCategory(oldName: String, newName: String, type: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE category = :name AND type = :type")
    suspend fun countByCategory(name: String, type: String): Int

    @Query("UPDATE transactions SET walletId = :newId WHERE walletId = :oldId")
    suspend fun moveWallet(oldId: Long, newId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(c: CategoryEntity): Long

    @Update
    suspend fun update(c: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_items")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: Long): ShoppingListEntity?

    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    suspend fun getItems(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_lists")
    suspend fun allLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun allItems(): List<ShoppingItemEntity>

    @Insert
    suspend fun insertList(l: ShoppingListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(l: List<ShoppingListEntity>)

    @Update
    suspend fun updateList(l: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Insert
    suspend fun insertItem(i: ShoppingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(i: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(i: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE shopping_items SET converted = 1 WHERE id IN (:ids)")
    suspend fun markConverted(ids: List<Long>)

    @Query("DELETE FROM shopping_items")
    suspend fun clearItems()

    @Query("DELETE FROM shopping_lists")
    suspend fun clearLists()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: UserSettingsEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(b: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDue ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDue < :before")
    suspend fun due(before: Long): List<RecurringEntity>

    @Insert
    suspend fun insert(r: RecurringEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RecurringEntity>)

    @Update
    suspend fun update(r: RecurringEntity)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring")
    suspend fun clear()

    @Query("UPDATE recurring SET category = :newName WHERE category = :oldName AND type = :type")
    suspend fun reassignCategory(oldName: String, newName: String, type: String)

    @Query("SELECT COUNT(*) FROM recurring WHERE category = :name AND type = :type")
    suspend fun countByCategory(name: String, type: String): Int

    @Query("UPDATE recurring SET walletId = :newId WHERE walletId = :oldId")
    suspend fun moveWallet(oldId: Long, newId: Long)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY (paid >= amount) ASC, dueDate IS NULL ASC, dueDate ASC, id DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun all(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: Long): DebtEntity?

    @Insert
    suspend fun insert(d: DebtEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DebtEntity>)

    @Update
    suspend fun update(d: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM debts")
    suspend fun clear()
}

@Dao
interface ReceiptDao {
    /** Hanya id transaksi yang punya struk (untuk penanda di daftar), tanpa memuat gambarnya. */
    @Query("SELECT transactionId FROM receipts")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT data FROM receipts WHERE transactionId = :id")
    suspend fun get(id: Long): String?

    @Query("SELECT * FROM receipts WHERE transactionId IN (:ids)")
    suspend fun forIds(ids: List<Long>): List<ReceiptEntity>

    @Query("SELECT * FROM receipts")
    suspend fun all(): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ReceiptEntity>)

    @Query("DELETE FROM receipts WHERE transactionId = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM receipts")
    suspend fun clear()
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY sortOrder, id")
    suspend fun all(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: Long): WalletEntity?

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Insert
    suspend fun insert(w: WalletEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WalletEntity>)

    @Update
    suspend fun update(w: WalletEntity)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM wallets")
    suspend fun clear()

    @Query("UPDATE wallets SET initialBalance = 0")
    suspend fun resetBalances()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY dateMillis DESC, id DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers")
    suspend fun all(): List<TransferEntity>

    @Insert
    suspend fun insert(t: TransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TransferEntity>)

    @Update
    suspend fun update(t: TransferEntity)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transfers")
    suspend fun clear()

    @Query("UPDATE transfers SET fromWalletId = :newId WHERE fromWalletId = :oldId")
    suspend fun moveFrom(oldId: Long, newId: Long)

    @Query("UPDATE transfers SET toWalletId = :newId WHERE toWalletId = :oldId")
    suspend fun moveTo(oldId: Long, newId: Long)

    /** Transfer yang asal dan tujuannya jadi sama (setelah dua dompet digabung) tidak berarti apa-apa. */
    @Query("DELETE FROM transfers WHERE fromWalletId = toWalletId")
    suspend fun deleteSelfTransfers()
}
