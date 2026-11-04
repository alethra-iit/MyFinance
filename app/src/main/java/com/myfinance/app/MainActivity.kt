package com.myfinance.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinance.app.ui.AppNav
import com.myfinance.app.ui.AppViewModel
import com.myfinance.app.ui.LockScreen
import com.myfinance.app.ui.theme.MyFinanceTheme
import com.myfinance.app.util.BudgetAlerts
import kotlinx.coroutines.launch

// FragmentActivity (turunan ComponentActivity) dibutuhkan oleh BiometricPrompt
class MainActivity : FragmentActivity() {
    private val lock get() = (application as MyFinanceApp).lock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Saat kunci aktif: layar tidak bisa di-screenshot dan tampil kosong di daftar aplikasi terakhir
        if (lock.enabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val appLock = lock
            LaunchedEffect(appLock.enabled) {
                if (appLock.enabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val s = settings
            val sysDark = isSystemInDarkTheme()
            if (s == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F7FC)))
            } else {
                val dark = when (s.theme) { "DARK" -> true; "LIGHT" -> false; else -> sysDark }
                val showLock = appLock.enabled && appLock.locked
                MyFinanceTheme(dark) {
                    Box(Modifier.fillMaxSize()) {
                        // Konten tetap hidup di bawah layar kunci (agar hasil pemilih berkas dll. tidak hilang),
                        // tetapi disembunyikan dari layanan aksesibilitas selama terkunci.
                        Box(Modifier.fillMaxSize().then(if (showLock) Modifier.clearAndSetSemantics {} else Modifier)) {
                            AppNav(vm, s, appLock)
                        }
                        if (showLock) LockScreen(appLock)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onActivityStart()
        // Catat transaksi berulang yang jatuh tempo (mis. aplikasi lama tidak dibuka), lalu nilai anggaran
        val app = application as MyFinanceApp
        app.appScope.launch {
            runCatching {
                if (app.repo.processRecurring() > 0) BudgetAlerts.check(app, notify = false)
            }
        }
    }

    override fun onStop() {
        lock.onActivityStop()
        super.onStop()
    }
}
