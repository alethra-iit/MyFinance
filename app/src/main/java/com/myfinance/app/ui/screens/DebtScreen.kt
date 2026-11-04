@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.myfinance.app.data.*
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.*

@Composable
fun DebtScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val ctx = LocalContext.current
    val debts by vm.debts.collectAsState()
    val wallets by vm.wallets.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ACTIVE") }
    var editing by remember { mutableStateOf<DebtEntity?>(null) }
    var paying by remember { mutableStateOf<DebtEntity?>(null) }
    val now = System.currentTimeMillis()
    val green = MaterialTheme.extra.income
    val red = MaterialTheme.extra.expense

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val active = debts.filter { !it.isSettled }
    val lentTotal = active.filter { it.type == DEBT_LENT }.sumOf { it.remaining }
    val borrowedTotal = active.filter { it.type == DEBT_BORROWED }.sumOf { it.remaining }
    val shown = when (filter) {
        "LENT" -> active.filter { it.type == DEBT_LENT }
        "BORROWED" -> active.filter { it.type == DEBT_BORROWED }
        "DONE" -> debts.filter { it.isSettled }
        else -> active
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Utang & Piutang", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = DebtEntity(person = "", type = DEBT_LENT, amount = 0) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Tambah") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Piutang (belum diterima)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.money(lentTotal, cur), fontWeight = FontWeight.Bold, color = green, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Utang (belum dibayar)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.money(borrowedTotal, cur), fontWeight = FontWeight.Bold, color = red, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACTIVE" to "Aktif", "LENT" to "Piutang", "BORROWED" to "Utang", "DONE" to "Lunas").forEach { (k, label) ->
                        FilterChip(filter == k, { filter = k }, label = { Text(label) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        if (filter == "DONE") "Belum ada yang lunas."
                        else "Belum ada catatan. Ketuk Tambah untuk mencatat siapa yang berutang kepadamu, atau kepada siapa kamu berutang."
                    )
                }
            }
            items(shown, key = { it.id }) { d ->
                DebtRow(d, cur, now, onClick = { editing = d }, onPay = { paying = d })
            }
        }
    }

    editing?.let { init ->
        DebtEditor(
            init, cur, onDismiss = { editing = null },
            onDelete = { vm.deleteDebt(init.id); editing = null },
            onSave = { d ->
                vm.saveDebt(d) { editing = null }
                // Pengingat jatuh tempo butuh izin notifikasi di Android 13+
                if (d.dueDate != null && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }
    paying?.let { d ->
        PaymentDialog(d, cur, wallets, onDismiss = { paying = null }) { amount, record, walletId ->
            vm.addPayment(d.id, amount, record, walletId) { paying = null }
        }
    }
}

@Composable
private fun DebtRow(d: DebtEntity, cur: String, now: Long, onClick: () -> Unit, onPay: () -> Unit) {
    val lent = d.type == DEBT_LENT
    val tint = if (lent) MaterialTheme.extra.income else MaterialTheme.extra.expense
    val days = d.daysUntilDue(now)
    val dueColor = when {
        d.isSettled || days == null -> MaterialTheme.colorScheme.onSurfaceVariant
        days < 0 -> MaterialTheme.extra.expense
        days <= 1 -> BudgetAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.person, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        (if (lent) "Piutang" else "Utang") + if (d.note.isNotBlank()) " · ${d.note}" else "",
                        style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(if (d.isSettled) d.amount else d.remaining, cur), fontWeight = FontWeight.Bold, color = tint)
                    if (d.paid > 0 && !d.isSettled) {
                        Text("dari ${Fmt.money(d.amount, cur)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (d.paid > 0 && !d.isSettled) BudgetBar((d.paid.toFloat() / d.amount), tint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d.isSettled) "Lunas" else dueText(days, d.dueDate),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = dueColor,
                    fontWeight = if (!d.isSettled && days != null && days < 0) FontWeight.Bold else FontWeight.Normal
                )
                if (!d.isSettled) TextButton(onClick = onPay) { Text(if (lent) "Terima" else "Bayar") }
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    d: DebtEntity, cur: String, wallets: List<WalletEntity>, onDismiss: () -> Unit, onConfirm: (Long, Boolean, Long) -> Unit
) {
    val ctx = LocalContext.current
    val lent = d.type == DEBT_LENT
    var text by remember { mutableStateOf(d.remaining.toString()) }
    var record by remember { mutableStateOf(false) }
    var walletId by remember { mutableStateOf(-1L) }
    val selectedWallet = effectiveWalletId(wallets, walletId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lent) "Terima dari ${d.person}" else "Bayar utang ke ${d.person}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sisa: ${Fmt.money(d.remaining, cur)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Jumlah") }, prefix = { Text(Fmt.symbol(cur) + " ") }, singleLine = true,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text("Isi sebagian untuk mencatat cicilan. Jumlah sama dengan sisa berarti lunas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(record, { record = it })
                    Text(
                        "Catat juga sebagai ${if (lent) "pemasukan" else "pengeluaran"} (memengaruhi saldo)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Dompet mana yang uangnya bertambah/berkurang (hanya relevan bila dicatat sebagai transaksi)
                if (record && wallets.size > 1) {
                    Text(if (lent) "Uang masuk ke dompet" else "Dibayar dari dompet", style = MaterialTheme.typography.labelLarge)
                    WalletPicker(wallets, selectedWallet, { walletId = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toLongOrNull() ?: 0L
                if (v <= 0L) Toast.makeText(ctx, "Nominal harus lebih dari 0", Toast.LENGTH_SHORT).show()
                else onConfirm(minOf(v, d.remaining), record, selectedWallet)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DebtEditor(initial: DebtEntity, cur: String, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (DebtEntity) -> Unit) {
    val ctx = LocalContext.current
    val isNew = initial.id == 0L
    var type by remember { mutableStateOf(initial.type) }
    var person by remember { mutableStateOf(initial.person) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var hasDue by remember { mutableStateOf(isNew || initial.dueDate != null) }
    var due by remember { mutableStateOf(initial.dueDate ?: Dates.nextOccurrence(FREQ_WEEKLY, Dates.today(), 1)) }
    var note by remember { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Catatan Baru" else "Ubah Catatan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == DEBT_LENT, { type = DEBT_LENT }, label = { Text("Piutang") })
                    FilterChip(type == DEBT_BORROWED, { type = DEBT_BORROWED }, label = { Text("Utang") })
                }
                Text(
                    if (type == DEBT_LENT) "Dia berutang kepada saya" else "Saya berutang kepada dia",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = person, onValueChange = { person = it.take(40) }, label = { Text("Nama") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Jumlah") }, prefix = { Text(Fmt.symbol(cur) + " ") }, singleLine = true,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (initial.paid > 0) {
                    Text("Sudah dibayar ${Fmt.money(initial.paid, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ada jatuh tempo (dengan pengingat)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(hasDue, { hasDue = it })
                }
                if (hasDue) DateField(due) { due = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(60) }, label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Contoh: pinjam untuk servis motor") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Hapus catatan ini", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.toLongOrNull() ?: 0L
                when {
                    person.isBlank() -> Toast.makeText(ctx, "Isi nama", Toast.LENGTH_SHORT).show()
                    v <= 0L -> Toast.makeText(ctx, "Nominal harus lebih dari 0", Toast.LENGTH_SHORT).show()
                    v < initial.paid -> Toast.makeText(ctx, "Jumlah tidak boleh kurang dari yang sudah dibayar", Toast.LENGTH_SHORT).show()
                    else -> onSave(
                        initial.copy(
                            person = person.trim(), type = type, amount = v, note = note.trim(),
                            dueDate = if (hasDue) Dates.startOfDay(due) else null
                        )
                    )
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
