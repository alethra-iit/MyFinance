package com.myfinance.app.util

import android.content.Context
import com.myfinance.app.MyFinanceApp
import com.myfinance.app.data.BudgetEntity
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TransactionEntity

/** Pemakaian satu anggaran pada bulan berjalan. level: 0 = aman, 1 = >= 80%, 2 = melewati batas. */
data class BudgetStatus(val category: String, val limit: Long, val spent: Long) {
    val pct: Int get() = if (limit > 0) (spent * 100 / limit).coerceAtMost(9999).toInt() else 0
    val level: Int get() = when { pct >= 100 -> 2; pct >= 80 -> 1; else -> 0 }
    val fraction: Float get() = if (limit > 0) (spent.toFloat() / limit).coerceIn(0f, 1f) else 0f
}

fun budgetStatuses(budgets: List<BudgetEntity>, txs: List<TransactionEntity>, now: Long): List<BudgetStatus> {
    val (s, e) = Dates.range(Period.MONTH, now)
    val spent = txs.filter { it.type == TYPE_EXPENSE && it.dateMillis in s until e }
        .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
    return budgets.filter { it.amount > 0 }
        .map { BudgetStatus(it.category, it.amount, spent[it.category] ?: 0L) }
        .sortedByDescending { it.pct }
}

fun budgetMessage(st: BudgetStatus, cur: String): String =
    if (st.level == 2) "Anggaran ${st.category} terlampaui: ${Fmt.money(st.spent, cur)} dari ${Fmt.money(st.limit, cur)}"
    else "Anggaran ${st.category} sudah ${st.pct}% (${Fmt.money(st.spent, cur)} dari ${Fmt.money(st.limit, cur)})"

/**
 * Memberi tahu SEKALI tiap kali sebuah anggaran naik ke level 80% atau 100% pada bulan berjalan.
 * Level terakhir yang sudah diberitahukan disimpan per bulan + kategori + batas, jadi mengubah batas
 * akan menilai ulang, dan bulan baru mulai dari nol.
 */
object BudgetAlerts {
    class Alert(val status: BudgetStatus, val message: String)

    /** notify = true: kirim notifikasi sistem (dipakai pekerja latar). false: hanya kembalikan daftar (untuk toast). */
    suspend fun check(ctx: Context, notify: Boolean): List<Alert> {
        val app = ctx.applicationContext as MyFinanceApp
        val repo = app.repo
        val cur = repo.currentSettings().currency
        val now = System.currentTimeMillis()
        val prefs = app.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val ym = Fmt.fileStamp(Dates.range(Period.MONTH, now).first).take(6)
        val out = mutableListOf<Alert>()
        for (st in budgetStatuses(repo.allBudgets(), repo.allTransactions(), now)) {
            val key = "$ym|${st.category}|${st.limit}"
            val last = prefs.getInt(key, 0)
            if (st.level > last) {
                prefs.edit().putInt(key, st.level).apply()
                out += Alert(st, budgetMessage(st, cur))
            }
        }
        if (notify) out.forEach { a ->
            Notify.post(
                app, "budget_alerts", "Peringatan Anggaran", 2000 + (a.status.category.hashCode() and 0xFFF),
                if (a.status.level == 2) "Anggaran terlampaui" else "Anggaran hampir habis", a.message
            )
        }
        return out
    }
}
