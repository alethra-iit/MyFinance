package com.myfinance.app.util

import android.content.Context
import com.myfinance.app.MyFinanceApp
import com.myfinance.app.data.DEBT_LENT
import com.myfinance.app.data.DebtEntity

val DebtEntity.remaining: Long get() = (amount - paid).coerceAtLeast(0L)
val DebtEntity.isSettled: Boolean get() = paid >= amount

/** Selisih hari ke jatuh tempo: negatif = terlambat, 0 = hari ini, null = tanpa jatuh tempo. */
fun DebtEntity.daysUntilDue(now: Long): Int? {
    val due = dueDate ?: return null
    return Math.round((Dates.startOfDay(due) - Dates.startOfDay(now)) / Dates.DAY.toDouble()).toInt()
}

fun dueText(days: Int?, due: Long?): String = when {
    days == null || due == null -> "Tanpa jatuh tempo"
    days < 0 -> "Terlambat ${-days} hari"
    days == 0 -> "Jatuh tempo hari ini"
    days == 1 -> "Jatuh tempo besok"
    else -> "Jatuh tempo ${Fmt.date(due)} ($days hari lagi)"
}

/** Notifikasi saat utang/piutang jatuh tempo besok/hari ini atau terlambat; sekali per tahap per jatuh tempo. */
object DebtReminders {
    suspend fun check(ctx: Context) {
        val app = ctx.applicationContext as MyFinanceApp
        val repo = app.repo
        val cur = repo.currentSettings().currency
        val prefs = app.getSharedPreferences("debt_alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (d in repo.allDebts()) {
            if (d.isSettled) continue
            val days = d.daysUntilDue(now) ?: continue
            val stage = when { days < 0 -> 2; days <= 1 -> 1; else -> 0 }
            if (stage == 0) continue
            val key = "${d.id}|${d.dueDate}|$stage"
            if (prefs.getBoolean(key, false)) continue
            prefs.edit().putBoolean(key, true).apply()
            val money = Fmt.money(d.remaining, cur)
            val lent = d.type == DEBT_LENT
            val whenText = when { days < 0 -> "sudah terlambat ${-days} hari"; days == 0 -> "jatuh tempo hari ini"; else -> "jatuh tempo besok" }
            Notify.post(
                app, "debts", "Utang & Piutang", 4000 + (d.id.toInt() and 0xFFFF),
                if (lent) "Piutang ${if (days < 0) "terlambat" else "jatuh tempo"}" else "Utang ${if (days < 0) "terlambat" else "jatuh tempo"}",
                if (lent) "${d.person} belum melunasi $money ($whenText)." else "Utang ke ${d.person} $money $whenText."
            )
        }
    }
}
