package com.myfinance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfinance.app.ui.AppNav
import com.myfinance.app.ui.AppViewModel
import com.myfinance.app.ui.theme.MyFinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val s = settings
            val sysDark = isSystemInDarkTheme()
            if (s == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F7FC)))
            } else {
                val dark = when (s.theme) { "DARK" -> true; "LIGHT" -> false; else -> sysDark }
                MyFinanceTheme(dark) { AppNav(vm, s) }
            }
        }
    }
}
