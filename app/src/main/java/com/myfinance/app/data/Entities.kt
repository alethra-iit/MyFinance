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

/**
 * Lampiran foto struk untuk satu transaksi. Dipisah dari tabel transactions supaya daftar transaksi
 * tidak ikut memuat string besar. [data] = JPEG yang sudah diperkecil, dalam Base64 (tanpa baris baru).
 * Ikut terhapus otomatis (CASCADE) saat transaksinya dihapus.
 */
@Entity(
    tableName = "receipts",
    foreignKeys = [ForeignKey(
        entity = TransactionEntity::class,
        parentColumns = ["id"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReceiptEntity(
    @PrimaryKey val transactionId: Long,
    val data: String
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val sortOrder: Int = 0,
    /** Kunci ikon (lihat CategoryIcons). Kosong = ikon bawaan berdasarkan nama. */
    val icon: String = "",
    /** Warna ARGB (non-negatif). 0 = warna bawaan berdasarkan nama. */
    val color: Long = 0L
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

/** Batas pengeluaran bulanan untuk satu kategori. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val amount: Long
)

const val FREQ_WEEKLY = "WEEKLY"
const val FREQ_MONTHLY = "MONTHLY"

/** Aturan transaksi berulang. Transaksi nyata dibuat otomatis saat nextDue tiba. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val category: String,
    val note: String = "",
    val frequency: String = FREQ_MONTHLY,
    /** Tanggal (1-31) patokan untuk pengulangan bulanan; bulan pendek memakai hari terakhir. */
    val anchorDay: Int = 1,
    /** Awal hari (millis) kemunculan berikutnya yang belum dicatat. */
    val nextDue: Long,
    val active: Boolean = true
)

const val DEBT_LENT = "LENT"          // piutang: saya meminjamkan, orang lain berutang ke saya
const val DEBT_BORROWED = "BORROWED"  // utang: saya meminjam, saya berutang ke orang lain

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val type: String,
    val amount: Long,
    /** Total yang sudah dibayar/diterima (cicilan boleh). Lunas bila paid >= amount. */
    val paid: Long = 0,
    /** Awal hari jatuh tempo (millis), null = tanpa jatuh tempo. */
    val dueDate: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object DefaultCategories {
    val income = listOf("Gaji", "Bonus", "Hadiah", "Bisnis", "Investasi", "Lainnya")
    val expense = listOf("Makanan", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Pendidikan", "Hiburan", "Lainnya")

    fun all(): List<CategoryEntity> =
        income.mapIndexed { i, n -> CategoryEntity(name = n, type = TYPE_INCOME, sortOrder = i) } +
            expense.mapIndexed { i, n -> CategoryEntity(name = n, type = TYPE_EXPENSE, sortOrder = i) }
}
