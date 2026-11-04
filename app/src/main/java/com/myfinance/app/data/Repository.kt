package com.myfinance.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

enum class ConvertResult { OK, NOTHING, ZERO_TOTAL }

data class AllData(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val lists: List<ShoppingListEntity>,
    val items: List<ShoppingItemEntity>,
    val settings: UserSettingsEntity?
)

class Repository(private val db: AppDatabase) {
    private val tx = db.transactionDao()
    private val cat = db.categoryDao()
    private val shop = db.shoppingDao()
    private val set = db.settingsDao()

    val transactions: Flow<List<TransactionEntity>> = tx.observeAll()
    val categories: Flow<List<CategoryEntity>> = cat.observeAll()
    val settings: Flow<UserSettingsEntity> = set.observe().filterNotNull()
    val lists: Flow<List<ShoppingListEntity>> = shop.observeLists()
    val items: Flow<List<ShoppingItemEntity>> = shop.observeItems()

    suspend fun seed() {
        if (cat.count() == 0) cat.insertAll(DefaultCategories.all())
        if (set.get() == null) set.upsert(UserSettingsEntity())
    }

    // ---------- Transaksi ----------
    suspend fun saveTransaction(t: TransactionEntity) {
        if (t.id == 0L) tx.insert(t) else tx.update(t)
    }
    suspend fun deleteTransaction(id: Long) = tx.delete(id)
    suspend fun getTransaction(id: Long) = tx.get(id)

    // ---------- Pengaturan ----------
    suspend fun currentSettings(): UserSettingsEntity = set.get() ?: UserSettingsEntity()
    suspend fun updateSettings(s: UserSettingsEntity) = set.upsert(s)

    // ---------- Belanja ----------
    suspend fun createList(title: String): Long = shop.insertList(ShoppingListEntity(title = title))
    suspend fun renameList(id: Long, title: String) {
        shop.getList(id)?.let { shop.updateList(it.copy(title = title)) }
    }
    suspend fun deleteList(id: Long) = shop.deleteList(id)
    suspend fun saveItem(i: ShoppingItemEntity) {
        if (i.id == 0L) shop.insertItem(i) else shop.updateItem(i)
    }
    suspend fun deleteItem(id: Long) = shop.deleteItem(id)
    suspend fun setChecked(id: Long, checked: Boolean) = shop.setChecked(id, checked)

    /**
     * Konversi barang yang sudah dicentang (dan belum pernah dikonversi) menjadi SATU transaksi pengeluaran.
     * Semua dilakukan dalam satu transaksi database; barang ditandai converted sehingga tidak bisa terduplikasi.
     */
    suspend fun convert(listId: Long, category: String, dateMillis: Long): ConvertResult =
        db.withTransaction {
            val list = shop.getList(listId) ?: return@withTransaction ConvertResult.NOTHING
            val toConvert = shop.getItems(listId).filter { it.checked && !it.converted }
            if (toConvert.isEmpty()) return@withTransaction ConvertResult.NOTHING
            val total = toConvert.sumOf { it.quantity.toLong() * it.price }
            if (total <= 0L) return@withTransaction ConvertResult.ZERO_TOTAL
            tx.insert(
                TransactionEntity(
                    type = TYPE_EXPENSE,
                    amount = total,
                    category = category,
                    dateMillis = dateMillis,
                    note = "Belanja: ${list.title}"
                )
            )
            shop.markConverted(toConvert.map { it.id })
            shop.updateList(list.copy(lastConvertedAt = System.currentTimeMillis()))
            ConvertResult.OK
        }

    // ---------- Backup / restore ----------
    suspend fun allData() = AllData(tx.all(), cat.all(), shop.allLists(), shop.allItems(), set.get())

    suspend fun restore(d: AllData) = db.withTransaction {
        shop.clearItems(); shop.clearLists(); tx.clear(); cat.clear()
        if (d.categories.isNotEmpty()) cat.insertAll(d.categories) else cat.insertAll(DefaultCategories.all())
        tx.insertAll(d.transactions)
        shop.insertLists(d.lists)
        shop.insertItems(d.items)
        d.settings?.let { set.upsert(it) }
    }

    suspend fun clearAll() = db.withTransaction {
        shop.clearItems(); shop.clearLists(); tx.clear()
    }
}
