package com.myfinance.app

import android.app.Application
import com.myfinance.app.data.AppDatabase
import com.myfinance.app.data.Repository
import com.myfinance.app.util.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFinanceApp : Application() {
    val repo: Repository by lazy { Repository(AppDatabase.build(this)) }

    /** Satu instance per proses: tetap terbuka saat layar diputar, terkunci lagi bila proses dimatikan. */
    val lock: AppLock by lazy { AppLock(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.seed() }
    }
}
