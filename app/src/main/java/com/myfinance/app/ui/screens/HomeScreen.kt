@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.Purple
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.Period

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController, cur: String, userName: String) {
    val txs by vm.transactions.collectAsState()
    var hidden by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val totalIncome = txs.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val totalExpense = txs.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    val now = System.currentTimeMillis()
    val (ms, me) = Dates.range(Period.MONTH, now)
    val month = txs.filter { it.dateMillis in ms until me }
    val mIncome = month.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val mExpense = month.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }

    val green = MaterialTheme.extra.income
    val red = MaterialTheme.extra.expense
    fun money(v: Long) = if (hidden) "${Fmt.symbol(cur)} ••••••" else Fmt.money(v, cur)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Purple),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MyFinance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Halo, $userName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { nav.goTab("profile") }) { Icon(Icons.Rounded.Notifications, "Pengaturan notifikasi") }
            }
        }
        // Kartu saldo
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6554E8), Color(0xFF8F7FF5))))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saldo Saat Ini", color = Color.White.copy(alpha = .9f), modifier = Modifier.weight(1f))
                        IconButton(onClick = { hidden = !hidden }, modifier = Modifier.size(32.dp)) {
                            Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Tampilkan/sembunyikan saldo", tint = Color.White)
                        }
                    }
                    Text(money(balance), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniStat("Pemasukan", money(mIncome), Icons.Rounded.ArrowUpward, Color(0xFF22C55E), Modifier.weight(1f))
                        MiniStat("Pengeluaran", money(mExpense), Icons.Rounded.ArrowDownward, Color(0xFFEF4444), Modifier.weight(1f))
                    }
                }
            }
        }
        // Aksi cepat
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Rounded.ArrowUpward, "Tambah Pemasukan", green, { nav.navigate("add/$TYPE_INCOME") }, Modifier.weight(1f))
                QuickAction(Icons.Rounded.ArrowDownward, "Tambah Pengeluaran", red, { nav.navigate("add/$TYPE_EXPENSE") }, Modifier.weight(1f))
                QuickAction(Icons.Rounded.Description, "Catatan Belanja", Purple, { nav.goTab("shopping") }, Modifier.weight(1f))
                QuickAction(Icons.Rounded.BarChart, "Lihat Laporan", Color(0xFF3B82F6), { nav.navigate("report") }, Modifier.weight(1f))
            }
        }
        // Ringkasan bulan ini
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ringkasan Bulan Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("report") }) { Text("Lihat Semua"); Icon(Icons.Rounded.ChevronRight, null) }
                }
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = Purple)
                            Spacer(Modifier.width(8.dp))
                            Text(Fmt.monthYear(now), fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(14.dp))
                        val total = (mIncome + mExpense).toFloat()
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (total > 0f) {
                                if (mIncome > 0) Box(Modifier.weight(mIncome / total).fillMaxHeight().background(green))
                                if (mExpense > 0) Box(Modifier.weight(mExpense / total).fillMaxHeight().background(red))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Pemasukan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(mIncome), fontWeight = FontWeight.Bold, color = green)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Pengeluaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(mExpense), fontWeight = FontWeight.Bold, color = red)
                            }
                        }
                    }
                }
            }
        }
        // Transaksi terbaru
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transaksi Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.goTab("history") }) { Text("Lihat Semua"); Icon(Icons.Rounded.ChevronRight, null) }
            }
        }
        if (txs.isEmpty()) {
            item { EmptyState("Belum ada transaksi. Mulai dengan menambah pemasukan atau pengeluaran.") }
        } else {
            items(txs.take(6), key = { it.id }) { t -> TransactionRow(t, cur) { selectedId = t.id } }
        }
    }

    val selected = txs.firstOrNull { it.id == selectedId }
    if (selected != null) {
        TransactionDetailSheet(
            selected, cur,
            onDismiss = { selectedId = null },
            onEdit = { selectedId = null; nav.navigate("add/${selected.type}?id=${selected.id}") },
            onDelete = { vm.deleteTransaction(selected.id); selectedId = null }
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}
