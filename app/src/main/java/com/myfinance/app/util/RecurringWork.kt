package com.myfinance.app.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfinance.app.MyFinanceApp
import java.util.concurrent.TimeUnit

/** Berjalan tiap ~6 jam di latar belakang: mencatat transaksi berulang, memeriksa anggaran, dan pengingat utang/piutang. */
class RecurringWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = (ctx.applicationContext as MyFinanceApp).repo
            val n = repo.processRecurring()
            if (n > 0) {
                Notify.post(
                    ctx, "recurring", "Transaksi Berulang", 3001,
                    "Transaksi berulang dicatat", "$n transaksi otomatis ditambahkan ke catatan keuanganmu."
                )
            }
            BudgetAlerts.check(ctx, notify = true)
            DebtReminders.check(ctx)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "recurring_work"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
