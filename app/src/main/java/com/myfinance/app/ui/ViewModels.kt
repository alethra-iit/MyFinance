package com.myfinance.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myfinance.app.MyFinanceApp
import com.myfinance.app.data.*
import com.myfinance.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MyFinanceApp).repo

    val transactions: StateFlow<List<TransactionEntity>> =
        repo.transactions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<UserSettingsEntity?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Ikon & warna kategori dibaca dari mana-mana lewat nama, jadi simpan di satu tempat yang reaktif
        viewModelScope.launch { categories.collect { CategoryStyles.update(it) } }
    }

    fun saveCategory(c: CategoryEntity, onDone: () -> Unit = {}) { viewModelScope.launch { repo.saveCategory(c); onDone() } }
    suspend fun categoryUsage(c: CategoryEntity): Int = repo.categoryUsage(c)
    fun deleteCategory(id: Long, moveTo: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.deleteCategory(id, moveTo); onDone() }
    }

    val budgets: StateFlow<List<BudgetEntity>> =
        repo.budgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recurring: StateFlow<List<RecurringEntity>> =
        repo.recurring.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debts: StateFlow<List<DebtEntity>> =
        repo.debts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveDebt(d: DebtEntity, onDone: () -> Unit = {}) { viewModelScope.launch { repo.saveDebt(d); onDone() } }
    fun deleteDebt(id: Long) { viewModelScope.launch { repo.deleteDebt(id) } }
    fun addPayment(id: Long, amount: Long, recordTx: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.addPayment(id, amount, recordTx, Dates.today())
            if (recordTx) runCatching { BudgetAlerts.check(getApplication<Application>(), notify = false) }
            onDone()
        }
    }

    /** onAlert dipanggil (sebelum onDone) untuk tiap anggaran yang baru mencapai 80% / melewati batas. */
    fun saveTransaction(t: TransactionEntity, onAlert: (String) -> Unit = {}, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveTransaction(t)
            if (t.type == TYPE_EXPENSE) {
                runCatching { BudgetAlerts.check(getApplication<Application>(), notify = false) }.getOrDefault(emptyList()).forEach { onAlert(it.message) }
            }
            onDone()
        }
    }

    fun setBudget(category: String, amount: Long) {
        viewModelScope.launch { repo.saveBudget(category, amount) }
    }

    fun saveRecurring(r: RecurringEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveRecurring(r)
            repo.processRecurring()   // tanggal mulai hari ini/lampau langsung dicatat
            runCatching { BudgetAlerts.check(getApplication<Application>(), notify = false) }
            onDone()
        }
    }
    fun deleteRecurring(id: Long) { viewModelScope.launch { repo.deleteRecurring(id) } }
    fun setRecurringActive(id: Long, active: Boolean) {
        viewModelScope.launch { repo.setRecurringActive(id, active); if (active) repo.processRecurring() }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repo.deleteTransaction(id) }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = repo.getTransaction(id)

    fun updateSettings(block: (UserSettingsEntity) -> UserSettingsEntity) {
        viewModelScope.launch { repo.updateSettings(block(repo.currentSettings())) }
    }

    suspend fun buildBackup(): String = withContext(Dispatchers.Default) { BackupManager.toJson(repo.allData()) }

    suspend fun restore(json: String): Boolean = try {
        repo.restore(BackupManager.fromJson(json)); true
    } catch (e: Exception) { false }

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }

    suspend fun buildExport(format: String, range: String): ByteArray = withContext(Dispatchers.Default) {
        val cur = settings.value?.currency ?: "IDR"
        val all = transactions.value
        val now = System.currentTimeMillis()
        val list: List<TransactionEntity>
        val title: String
        when (range) {
            "MONTH" -> {
                val (s, e) = Dates.range(Period.MONTH, now)
                list = all.filter { it.dateMillis in s until e }; title = "Laporan Keuangan - ${Fmt.longMonth(now)}"
            }
            "YEAR" -> {
                val (s, e) = Dates.range(Period.YEAR, now)
                list = all.filter { it.dateMillis in s until e }; title = "Laporan Keuangan - ${Fmt.year(now)}"
            }
            else -> { list = all; title = "Laporan Keuangan - Semua Data" }
        }
        val sorted = list.sortedBy { it.dateMillis }
        if (format == "PDF") Exporter.pdf(title, sorted, cur) else Exporter.xlsx(title, sorted)
    }
}

class ShoppingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MyFinanceApp).repo

    val lists: StateFlow<List<ShoppingListEntity>> =
        repo.lists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items: StateFlow<List<ShoppingItemEntity>> =
        repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createList(title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createList(title)) }
    }
    fun renameList(id: Long, title: String) { viewModelScope.launch { repo.renameList(id, title) } }
    fun deleteList(id: Long) { viewModelScope.launch { repo.deleteList(id) } }
    fun saveItem(i: ShoppingItemEntity) { viewModelScope.launch { repo.saveItem(i) } }
    fun deleteItem(id: Long) { viewModelScope.launch { repo.deleteItem(id) } }
    fun setChecked(id: Long, checked: Boolean) { viewModelScope.launch { repo.setChecked(id, checked) } }
    fun convert(listId: Long, category: String, date: Long, onResult: (ConvertResult) -> Unit) {
        viewModelScope.launch { onResult(repo.convert(listId, category, date)) }
    }
}
