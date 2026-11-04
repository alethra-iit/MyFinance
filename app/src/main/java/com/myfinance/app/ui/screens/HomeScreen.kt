@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.DEBT_BORROWED
import com.myfinance.app.data.DEBT_LENT
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.WalletEntity
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.Purple
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.budgetStatuses
import com.myfinance.app.util.daysUntilDue
import com.myfinance.app.util.isSettled
import com.myfinance.app.util.remaining
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.Period
import com.myfinance.app.util.Reminder

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController, cur: String, userName: String, notificationsOn: Boolean = false) {
    val ctx = LocalContext.current
    var showNotif by remember { mutableStateOf(false) }
    var reminderLabel by remember { mutableStateOf(Reminder.label(ctx)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.updateSettings { it.copy(notifications = true) }; Reminder.schedule(ctx)
            Toast.makeText(ctx, "Pengingat harian aktif (pukul ${Reminder.label(ctx)})", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(ctx, "Izin notifikasi ditolak", Toast.LENGTH_SHORT).show()
    }
    val txs by vm.transactions.collectAsState()
    val receiptIds by vm.receiptIds.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val debts by vm.debts.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val balances by vm.balances.collectAsState()
    var hidden by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val totalIncome = txs.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val totalExpense = txs.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }
    // Saldo = jumlah saldo semua dompet. Selama dompet belum termuat, pakai hitungan lama agar tidak sempat tampil 0.
    val balance = if (wallets.isEmpty()) totalIncome - totalExpense else wallets.sumOf { balances[it.id] ?: 0L }
    val multiWallet = wallets.size > 1
    val walletNames = remember(wallets) { wallets.associate { it.id to it.name } }

    val now = System.currentTimeMillis()
    val (ms, me) = Dates.range(Period.MONTH, now)
    val month = txs.filter { it.dateMillis in ms until me }
    val mIncome = month.filter { it.type == TYPE_INCOME }.sumOf { it.amount }
    val mExpense = month.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }

    val green = MaterialTheme.extra.income
    val red = MaterialTheme.extra.expense
    fun money(v: Long) = if (hidden) "${Fmt.symbol(cur)} ••••••" else Fmt.money(v, cur)

    if (showNotif) {
        AlertDialog(
            onDismissRequest = { showNotif = false },
            title = { Text("Pengingat Harian") },
            text = {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ingatkan saya mencatat transaksi setiap hari", Modifier.weight(1f))
                    Switch(checked = notificationsOn, onCheckedChange = { on ->
                        if (!on) {
                            vm.updateSettings { it.copy(notifications = false) }; Reminder.cancel(ctx)
                        } else if (Build.VERSION.SDK_INT >= 33) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(notifications = true) }; Reminder.schedule(ctx)
                            Toast.makeText(ctx, "Pengingat harian aktif (pukul ${Reminder.label(ctx)})", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Reminder.pickTime(ctx, notificationsOn) { reminderLabel = Reminder.label(ctx) } }) {
                    Icon(Icons.Rounded.Schedule, null); Spacer(Modifier.width(8.dp)); Text("Jam pengingat: $reminderLabel")
                }
              }
            },
            confirmButton = { TextButton(onClick = { showNotif = false }) { Text("Tutup") } }
        )
    }

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
                IconButton(onClick = { showNotif = true }) {
                    Icon(if (notificationsOn) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, "Pengaturan notifikasi")
                }
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
                        Text(if (multiWallet) "Total Saldo" else "Saldo Saat Ini", color = Color.White.copy(alpha = .9f), modifier = Modifier.weight(1f))
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
                QuickAction(Icons.Rounded.ShoppingCart, "Belanja", Purple, { nav.goTab("shopping") }, Modifier.weight(1f))
                QuickAction(Icons.Rounded.BarChart, "Lihat Laporan", Color(0xFF3B82F6), { nav.navigate("report") }, Modifier.weight(1f))
            }
        }
        // Dompet
        if (wallets.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dompet Saya", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("wallets") }) { Text("Kelola"); Icon(Icons.Rounded.ChevronRight, null) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(wallets, key = { it.id }) { w ->
                        val bal = balances[w.id] ?: 0L
                        WalletMiniCard(w, money(bal), bal < 0, red) { nav.navigate("wallets") }
                    }
                    item {
                        SoftCard(Modifier.width(120.dp), onClick = { nav.navigate("wallets?transfer=true") }) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconBubble(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.primary, size = 36.dp)
                                Column {
                                    Text("Transfer", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Antar dompet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
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
        // Anggaran bulan ini
        item {
            val statuses = budgetStatuses(budgets, txs, now)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Anggaran Bulan Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("budget") }) { Text(if (statuses.isEmpty()) "Atur" else "Kelola"); Icon(Icons.Rounded.ChevronRight, null) }
                }
                if (statuses.isEmpty()) {
                    SoftCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("budget") }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.Savings, Purple)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Belum ada anggaran", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Tetapkan batas bulanan per kategori dan dapatkan peringatan sebelum kantong jebol.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    statuses.filter { it.level > 0 }.take(3).forEach { BudgetWarningBanner(it, cur, hidden) }
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            statuses.take(5).forEach { BudgetRow(it, { v -> money(v) }) }
                            if (statuses.size > 5) {
                                Text(
                                    "+${statuses.size - 5} anggaran lainnya", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        // Utang & piutang (hanya bila ada yang belum lunas)
        val openDebts = debts.filter { !it.isSettled }
        if (openDebts.isNotEmpty()) item {
            val lentSum = openDebts.filter { it.type == DEBT_LENT }.sumOf { it.remaining }
            val borrowedSum = openDebts.filter { it.type == DEBT_BORROWED }.sumOf { it.remaining }
            val late = openDebts.count { (it.daysUntilDue(now) ?: 0) < 0 }
            val soon = openDebts.count { (it.daysUntilDue(now) ?: 99) in 0..3 }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Utang & Piutang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("debts") }) { Text("Kelola"); Icon(Icons.Rounded.ChevronRight, null) }
                }
                SoftCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("debts") }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Piutang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(lentSum), fontWeight = FontWeight.Bold, color = green)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Utang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(borrowedSum), fontWeight = FontWeight.Bold, color = red)
                            }
                        }
                        if (late > 0) Text("$late terlambat dari jatuh tempo", style = MaterialTheme.typography.bodySmall, color = red, fontWeight = FontWeight.SemiBold)
                        if (soon > 0) Text("$soon jatuh tempo dalam 3 hari ke depan", style = MaterialTheme.typography.bodySmall, color = BudgetAmber, fontWeight = FontWeight.SemiBold)
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
            items(txs.take(6), key = { it.id }) { t -> TransactionRow(t, cur, hasReceipt = t.id in receiptIds, walletName = if (multiWallet) walletNames[t.walletId] else null) { selectedId = t.id } }
        }
    }

    val selected = txs.firstOrNull { it.id == selectedId }
    val receipt = rememberReceipt(vm, selected?.id, selected != null && selected.id in receiptIds)
    if (selected != null) {
        TransactionDetailSheet(
            selected, cur, receipt = receipt,
            walletName = if (multiWallet) walletNames[selected.walletId] else null,
            onDismiss = { selectedId = null },
            onEdit = { selectedId = null; nav.navigate("add/${selected.type}?id=${selected.id}") },
            onDelete = { vm.deleteTransaction(selected.id); selectedId = null }
        )
    }
}

@Composable
private fun WalletMiniCard(w: WalletEntity, balanceText: String, negative: Boolean, negativeColor: Color, onClick: () -> Unit) {
    SoftCard(Modifier.width(150.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBubble(walletIcon(w.kind), walletColor(w.kind), size = 36.dp)
            Column {
                Text(w.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    balanceText, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (negative) negativeColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
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
