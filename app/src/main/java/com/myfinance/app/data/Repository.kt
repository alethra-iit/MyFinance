package com.myfinance.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Period

enum class ConvertResult { OK, NOTHING, ZERO_TOTAL }

data class AllData(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val lists: List<ShoppingListEntity>,
    val items: List<ShoppingItemEntity>,
    val settings: UserSettingsEntity?,
    val budgets: List<BudgetEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val receipts: List<ReceiptEntity> = emptyList(),
    val wallets: List<WalletEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList()
)

class Repository(private val db: AppDatabase) {
    private val tx = db.transactionDao()
    private val cat = db.categoryDao()
    private val shop = db.shoppingDao()
    private val set = db.settingsDao()
    private val bud = db.budgetDao()
    private val rec = db.recurringDao()
    private val debt = db.debtDao()
    private val rcp = db.receiptDao()
    private val wal = db.walletDao()
    private val trf = db.transferDao()

    val transactions: Flow<List<TransactionEntity>> = tx.observeAll()
    val categories: Flow<List<CategoryEntity>> = cat.observeAll()
    val settings: Flow<UserSettingsEntity> = set.observe().filterNotNull()
    val lists: Flow<List<ShoppingListEntity>> = shop.observeLists()
    val items: Flow<List<ShoppingItemEntity>> = shop.observeItems()
    val budgets: Flow<List<BudgetEntity>> = bud.observeAll()
    val recurring: Flow<List<RecurringEntity>> = rec.observeAll()
    val debts: Flow<List<DebtEntity>> = debt.observeAll()
    val receiptIds: Flow<List<Long>> = rcp.observeIds()
    val wallets: Flow<List<WalletEntity>> = wal.observeAll()
    val transfers: Flow<List<TransferEntity>> = trf.observeAll()

    suspend fun seed() {
        if (cat.count() == 0) cat.insertAll(DefaultCategories.all())
        if (set.get() == null) set.upsert(UserSettingsEntity())
        if (wal.count() == 0) wal.insertAll(DefaultWallets.all())
    }

    // ---------- Transaksi ----------
    /**
     * Menyimpan transaksi. Lampiran struk hanya disentuh bila [updateReceipt] = true:
     * [receipt] berisi Base64 untuk dipasang/diganti, atau null/kosong untuk menghapus.
     * Transaksi dan lampirannya disimpan dalam satu transaksi database.
     */
    suspend fun saveTransaction(t: TransactionEntity, receipt: String? = null, updateReceipt: Boolean = false) {
        db.withTransaction {
            val fixed = t.copy(walletId = validWalletId(t.walletId))
            val id = if (fixed.id == 0L) tx.insert(fixed) else { tx.update(fixed); fixed.id }
            if (updateReceipt) {
                if (receipt.isNullOrEmpty()) rcp.delete(id) else rcp.upsert(ReceiptEntity(id, receipt))
            }
        }
    }
    suspend fun deleteTransaction(id: Long) = db.withTransaction { rcp.delete(id); tx.delete(id) }
    suspend fun getReceipt(transactionId: Long): String? = rcp.get(transactionId)

    /** Struk untuk daftar id tertentu (dipecah agar tidak melewati batas variabel SQLite). */
    suspend fun receiptsFor(ids: List<Long>): Map<Long, String> =
        ids.chunked(500).flatMap { rcp.forIds(it) }.associate { it.transactionId to it.data }
    suspend fun getTransaction(id: Long) = tx.get(id)

    // ---------- Dompet & transfer ----------
    /** Dompet yang diminta bila ada; kalau tidak (mis. sudah dihapus) jatuh ke dompet pertama. */
    private suspend fun validWalletId(id: Long): Long =
        if (wal.get(id) != null) id else (wal.all().firstOrNull()?.id ?: id)

    /** Tambah (id = 0) atau ubah dompet. */
    suspend fun saveWallet(w: WalletEntity) {
        db.withTransaction {
            if (w.id == 0L) {
                val order = (wal.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
                wal.insert(w.copy(sortOrder = order))
            } else {
                val old = wal.get(w.id)
                if (old != null) wal.update(w.copy(sortOrder = old.sortOrder))
            }
            Unit
        }
    }

    /**
     * Menghapus dompet. Seluruh transaksi, transaksi berulang, dan transfer miliknya dipindah ke [moveTo];
     * saldo awalnya digabung ke dompet tujuan sehingga total uang tidak berubah. Transfer antara kedua dompet
     * itu dihapus (setelah digabung tidak ada lagi yang berpindah). Dompet terakhir tidak boleh dihapus.
     */
    suspend fun deleteWallet(id: Long, moveTo: Long): Boolean = db.withTransaction {
        val w = wal.get(id) ?: return@withTransaction false
        val target = wal.get(moveTo) ?: return@withTransaction false
        if (id == moveTo || wal.count() <= 1) return@withTransaction false
        tx.moveWallet(id, moveTo)
        rec.moveWallet(id, moveTo)
        trf.moveFrom(id, moveTo)
        trf.moveTo(id, moveTo)
        trf.deleteSelfTransfers()
        wal.update(target.copy(initialBalance = target.initialBalance + w.initialBalance))
        wal.delete(id)
        true
    }

    /** Menyimpan transfer baru (id = 0) atau perubahan. false bila tidak valid (dompet sama / nominal 0 / dompet hilang). */
    suspend fun saveTransfer(t: TransferEntity): Boolean = db.withTransaction {
        if (t.amount <= 0L || t.fromWalletId == t.toWalletId) return@withTransaction false
        if (wal.get(t.fromWalletId) == null || wal.get(t.toWalletId) == null) return@withTransaction false
        if (t.id == 0L) trf.insert(t) else trf.update(t)
        true
    }

    suspend fun deleteTransfer(id: Long) = trf.delete(id)

    // ---------- Kategori ----------
    /** Tambah (id = 0) atau ubah. Mengganti nama ikut memperbarui transaksi, anggaran, dan transaksi berulang. */
    suspend fun saveCategory(c: CategoryEntity) {
        db.withTransaction {
            if (c.id == 0L) {
                val order = (cat.all().filter { it.type == c.type }.maxOfOrNull { it.sortOrder } ?: -1) + 1
                cat.insert(c.copy(sortOrder = order))
            } else {
                val old = cat.get(c.id)
                if (old != null) {
                    if (old.name != c.name) {
                        tx.reassignCategory(old.name, c.name, old.type)
                        rec.reassignCategory(old.name, c.name, old.type)
                        if (old.type == TYPE_EXPENSE) {
                            bud.all().firstOrNull { it.category == old.name }?.let {
                                bud.delete(old.name); bud.upsert(BudgetEntity(c.name, it.amount))
                            }
                        }
                    }
                    cat.update(c.copy(type = old.type, sortOrder = old.sortOrder))
                }
            }
            Unit // pastikan blok transaksi tidak berakhir pada if tanpa else
        }
    }

    suspend fun categoryUsage(c: CategoryEntity): Int = tx.countByCategory(c.name, c.type) + rec.countByCategory(c.name, c.type)

    /**
     * Menghapus kategori. Transaksi & transaksi berulang di dalamnya dipindah ke [moveTo] (kategori lain dengan
     * jenis yang sama); anggarannya dihapus. Kategori terakhir dari sebuah jenis tidak boleh dihapus.
     */
    suspend fun deleteCategory(id: Long, moveTo: String?) {
        db.withTransaction {
            val c = cat.get(id)
            if (c != null && cat.all().count { it.type == c.type } > 1) {
                if (moveTo != null && moveTo != c.name) {
                    tx.reassignCategory(c.name, moveTo, c.type)
                    rec.reassignCategory(c.name, moveTo, c.type)
                }
                if (c.type == TYPE_EXPENSE) bud.delete(c.name)
                cat.delete(id)
            }
        }
    }

    // ---------- Anggaran ----------
    suspend fun allTransactions() = tx.all()
    suspend fun allBudgets() = bud.all()
    suspend fun saveBudget(category: String, amount: Long) {
        if (amount <= 0L) bud.delete(category) else bud.upsert(BudgetEntity(category, amount))
    }

    // ---------- Transaksi berulang ----------
    suspend fun saveRecurring(r: RecurringEntity) {
        if (r.id == 0L) rec.insert(r) else rec.update(r)
    }
    suspend fun deleteRecurring(id: Long) = rec.delete(id)

    /** Menjeda/mengaktifkan. Saat diaktifkan lagi, masa jeda dilewati (tidak dicatat mundur). */
    suspend fun setRecurringActive(id: Long, active: Boolean) {
        val r = rec.get(id) ?: return
        var due = r.nextDue
        if (active) {
            val today = Dates.today()
            var guard = 0
            while (due < today && guard++ < 1000) due = Dates.nextOccurrence(r.frequency, due, r.anchorDay)
        }
        rec.update(r.copy(active = active, nextDue = due))
    }

    /**
     * Mencatat semua transaksi berulang yang jatuh tempo (hari ini atau sebelumnya, termasuk yang terlewat
     * saat aplikasi lama tidak dibuka). nextDue maju dalam transaksi database yang sama sehingga tidak
     * mungkin tercatat dua kali. Mengembalikan jumlah transaksi yang dibuat.
     */
    suspend fun processRecurring(now: Long = System.currentTimeMillis()): Int = db.withTransaction {
        val endOfToday = Dates.range(Period.DAY, now).second
        val walletIds = wal.all().map { it.id }
        val fallbackWallet = walletIds.firstOrNull() ?: 1L
        var created = 0
        for (r in rec.due(endOfToday)) {
            val target = if (r.walletId in walletIds) r.walletId else fallbackWallet
            var due = r.nextDue
            var n = 0
            while (due < endOfToday && n < 500) {
                tx.insert(
                    TransactionEntity(
                        type = r.type, amount = r.amount, category = r.category, dateMillis = due,
                        note = (r.note.ifBlank { r.category }) + " (berulang)",
                        walletId = target
                    )
                )
                due = Dates.nextOccurrence(r.frequency, due, r.anchorDay)
                n++
            }
            rec.update(r.copy(nextDue = due))
            created += n
        }
        created
    }

    // ---------- Utang & piutang ----------
    suspend fun allDebts() = debt.all()
    suspend fun saveDebt(d: DebtEntity) { if (d.id == 0L) debt.insert(d) else debt.update(d) }
    suspend fun deleteDebt(id: Long) = debt.delete(id)

    /**
     * Mencatat pembayaran (cicilan atau pelunasan). Jumlah dibatasi sebesar sisa. Bila recordTx, dibuat juga
     * transaksi (pemasukan untuk piutang, pengeluaran untuk utang) dalam transaksi database yang sama.
     */
    suspend fun addPayment(id: Long, amount: Long, recordTx: Boolean, dateMillis: Long, walletId: Long) = db.withTransaction {
        val d = debt.get(id) ?: return@withTransaction
        val pay = minOf(amount, (d.amount - d.paid).coerceAtLeast(0L))
        if (pay <= 0L) return@withTransaction
        debt.update(d.copy(paid = d.paid + pay))
        if (recordTx) {
            val lent = d.type == DEBT_LENT
            tx.insert(
                TransactionEntity(
                    type = if (lent) TYPE_INCOME else TYPE_EXPENSE, amount = pay, category = "Lainnya", dateMillis = dateMillis,
                    note = if (lent) "Pembayaran dari ${d.person}" else "Bayar utang ke ${d.person}",
                    walletId = validWalletId(walletId)
                )
            )
        }
    }

    // ---------- Pengaturan ----------
    suspend fun currentSettings(): UserSettingsEntity = set.get() ?: UserSettingsEntity()
    suspend fun updateSettings(s: UserSettingsEntity) = set.upsert(s)

    // ---------- Belanja ----------
    suspend fun createList(title: String): Long = shop.insertList(ShoppingListEntity(title = title))
    suspend fun renameList(id: Long, title: String) {
        shop.getList(id)?.let { shop.updateList(it.copy(title = title)) }
    }
    suspend fun deleteList(id: Long) = shop.deleteList(id)

    /**
     * Menyalin daftar beserta barangnya untuk belanja berulang. Salinan mulai bersih: belum dicentang dan belum
     * dicatat sebagai pengeluaran. Dalam satu transaksi database. Mengembalikan id daftar baru (atau null bila asal hilang).
     */
    suspend fun duplicateList(id: Long): Long? = db.withTransaction {
        val src = shop.getList(id) ?: return@withTransaction null
        val newId = shop.insertList(ShoppingListEntity(title = "${src.title} (salinan)".take(60)))
        shop.insertItems(shop.getItems(id).map { it.copy(id = 0, listId = newId, checked = false, converted = false) })
        newId
    }
    suspend fun saveItem(i: ShoppingItemEntity) {
        if (i.id == 0L) shop.insertItem(i) else shop.updateItem(i)
    }
    suspend fun deleteItem(id: Long) = shop.deleteItem(id)
    suspend fun setChecked(id: Long, checked: Boolean) = shop.setChecked(id, checked)

    /**
     * Konversi barang yang sudah dicentang (dan belum pernah dikonversi) menjadi SATU transaksi pengeluaran.
     * Semua dilakukan dalam satu transaksi database; barang ditandai converted sehingga tidak bisa terduplikasi.
     */
    suspend fun convert(listId: Long, category: String, dateMillis: Long, walletId: Long): ConvertResult =
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
                    note = "Belanja: ${list.title}",
                    walletId = validWalletId(walletId)
                )
            )
            shop.markConverted(toConvert.map { it.id })
            shop.updateList(list.copy(lastConvertedAt = System.currentTimeMillis()))
            ConvertResult.OK
        }

    // ---------- Backup / restore ----------
    suspend fun allData() = AllData(tx.all(), cat.all(), shop.allLists(), shop.allItems(), set.get(), bud.all(), rec.all(), debt.all(), rcp.all(), wal.all(), trf.all())

    suspend fun restore(d: AllData) = db.withTransaction {
        shop.clearItems(); shop.clearLists(); rcp.clear(); trf.clear(); tx.clear(); wal.clear(); cat.clear(); bud.clear(); rec.clear(); debt.clear()
        if (d.categories.isNotEmpty()) cat.insertAll(d.categories) else cat.insertAll(DefaultCategories.all())
        // Backup lama (sebelum fitur dompet) tidak punya dompet: semua transaksinya masuk ke satu "Dompet Utama"
        val restoredWallets = if (d.wallets.isNotEmpty()) d.wallets else listOf(DefaultWallets.legacy())
        wal.insertAll(restoredWallets)
        val walletIds = restoredWallets.map { it.id }.toSet()
        val fallbackWallet = restoredWallets.first().id
        tx.insertAll(d.transactions.map { if (it.walletId in walletIds) it else it.copy(walletId = fallbackWallet) })
        // Hanya struk yang transaksinya ada (kalau tidak, batasan foreign key menolak seluruh restore)
        val txIds = d.transactions.map { it.id }.toSet()
        rcp.insertAll(d.receipts.filter { it.transactionId in txIds })
        shop.insertLists(d.lists)
        shop.insertItems(d.items)
        bud.insertAll(d.budgets)
        rec.insertAll(d.recurring.map { if (it.walletId in walletIds) it else it.copy(walletId = fallbackWallet) })
        trf.insertAll(d.transfers.filter { it.fromWalletId in walletIds && it.toWalletId in walletIds && it.fromWalletId != it.toWalletId })
        debt.insertAll(d.debts)
        d.settings?.let { set.upsert(it) }
    }

    suspend fun clearAll() = db.withTransaction {
        shop.clearItems(); shop.clearLists(); rcp.clear(); trf.clear(); tx.clear(); wal.resetBalances()
    }
}
