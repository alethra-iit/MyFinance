package com.myfinance.app

import android.app.Application
import com.myfinance.app.data.AppDatabase
import com.myfinance.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFinanceApp : Application() {
    val repo: Repository by lazy { Repository(AppDatabase.build(this)) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.seed() }
    }
}
