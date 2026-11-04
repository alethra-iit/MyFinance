package com.myfinance.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val TYPE_INCOME = "INCOME"
const val TYPE_EXPENSE = "EXPENSE"

@Entity(tableName = "transactions", indices = [Index("dateMillis")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val category: String,
    val dateMillis: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConvertedAt: Long? = null
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(
        entity = ShoppingListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: Int = 1,
    val price: Long = 0,
    val checked: Boolean = false,
    /** true bila sudah dikonversi menjadi transaksi (mencegah duplikasi) */
    val converted: Boolean = false
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val userName: String = "Pengguna",
    val theme: String = "LIGHT",      // LIGHT / DARK / SYSTEM
    val currency: String = "IDR",     // IDR / USD / EUR
    val notifications: Boolean = false
)

object DefaultCategories {
    val income = listOf("Gaji", "Bonus", "Hadiah", "Bisnis", "Investasi", "Lainnya")
    val expense = listOf("Makanan", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Pendidikan", "Hiburan", "Lainnya")

    fun all(): List<CategoryEntity> =
        income.mapIndexed { i, n -> CategoryEntity(name = n, type = TYPE_INCOME, sortOrder = i) } +
            expense.mapIndexed { i, n -> CategoryEntity(name = n, type = TYPE_EXPENSE, sortOrder = i) }
}
