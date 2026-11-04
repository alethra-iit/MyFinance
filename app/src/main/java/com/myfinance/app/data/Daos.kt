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
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

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
