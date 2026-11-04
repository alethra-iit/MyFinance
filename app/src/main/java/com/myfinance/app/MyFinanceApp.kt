package com.myfinance.app

import android.app.Application
import com.myfinance.app.data.AppDatabase
import com.myfinance.app.data.Repository
import com.myfinance.app.util.AppLock
import com.myfinance.app.util.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFinanceApp : Application() {
    val repo: Repository by lazy { Repository(AppDatabase.build(this)) }

    /** Satu instance per proses: tetap terbuka saat layar diputar, terkunci lagi bila proses dimatikan. */
    val lock: AppLock by lazy { AppLock(this) }

    /** Scope untuk pekerjaan singkat yang tidak boleh ikut terhenti saat layar ditutup. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repo.seed() }
        RecurringWorker.schedule(this)
    }
}
