package com.myfinance.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfinance.app.data.UserSettingsEntity
import com.myfinance.app.ui.screens.*
import com.myfinance.app.util.AppLock

private data class Tab(val route: String, val label: String, val icon: ImageVector)

fun NavController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNav(vm: AppViewModel, settings: UserSettingsEntity, lock: AppLock) {
    val nav = rememberNavController()
    val shopVm: ShoppingViewModel = viewModel()
    val appCtx = LocalContext.current
    val updScope = rememberCoroutineScope()
    val upd = remember { UpdateController(appCtx, updScope) }
    LaunchedEffect(Unit) { upd.onAppStart() }
    UpdateDialogs(upd)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val cur = settings.currency
    val tabs = listOf(
        Tab("home", "Beranda", Icons.Rounded.Home),
        Tab("history", "Riwayat", Icons.AutoMirrored.Rounded.List),
        Tab("shopping", "Belanja", Icons.Rounded.ShoppingCart),
        Tab("profile", "Profil", Icons.Rounded.Person)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(
                    modifier = Modifier.height(64.dp), // default M3 = 80dp, dibuat lebih ramping
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontWeight = if (route == t.route) FontWeight.Bold else FontWeight.Normal) },
                            // Tab aktif: pil berwarna penuh + ikon putih, tab lain redup, supaya jelas bedanya
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav, cur, settings.userName, settings.notifications) }
            composable("history") { HistoryScreen(vm, nav, cur) }
            composable("shopping") { ShoppingListScreen(shopVm, nav, cur) }
            composable("profile") { ProfileScreen(vm, settings, upd, lock, nav) }
            composable("budget") { BudgetScreen(vm, nav, cur) }
            composable("categories") { CategoryScreen(vm, nav) }
            composable("debts") { DebtScreen(vm, nav, cur) }
            composable("recurring") { RecurringScreen(vm, nav, cur) }
            composable("report") { ReportScreen(vm, nav, cur) }
            composable(
                "add/{type}?id={id}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { be ->
                AddTransactionScreen(
                    vm, nav,
                    be.arguments?.getString("type") ?: "EXPENSE",
                    be.arguments?.getLong("id") ?: -1L,
                    cur
                )
            }
            composable(
                "shopping/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { be ->
                ShoppingDetailScreen(shopVm, vm, be.arguments?.getLong("id") ?: -1L, nav, cur)
            }
        }
    }
}
