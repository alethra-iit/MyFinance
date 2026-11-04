@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.*
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

private fun scheduleText(r: RecurringEntity) =
    if (r.frequency == FREQ_WEEKLY) "Tiap ${Fmt.weekday(r.nextDue)}" else "Tiap tanggal ${r.anchorDay}"

@Composable
fun RecurringScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val list by vm.recurring.collectAsState()
    val cats by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Transaksi Berulang", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val today = Dates.today()
                    editing = RecurringEntity(type = TYPE_EXPENSE, amount = 0, category = "", nextDue = today, anchorDay = Dates.dayOfMonthOf(today))
                },
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
                Text(
                    "Gaji, cicilan, langganan, dan tagihan dicatat otomatis pada tanggalnya. Jika aplikasi lama tidak dibuka, yang terlewat dicatat saat berikutnya.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (list.isEmpty()) {
                item { EmptyState("Belum ada transaksi berulang. Ketuk Tambah untuk membuat, misalnya gaji bulanan atau langganan.") }
            }
            items(list, key = { it.id }) { r ->
                val income = r.type == TYPE_INCOME
                SoftCard(Modifier.fillMaxWidth(), onClick = { editing = r }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(categoryIcon(r.category, r.type), categoryColor(r.category, r.type))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.note.ifBlank { r.category }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${if (r.frequency == FREQ_WEEKLY) "Mingguan" else "Bulanan"} · ${scheduleText(r)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (r.active) "Berikutnya ${Fmt.date(r.nextDue)}" else "Dijeda",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Fmt.signed(r.amount, income, cur), fontWeight = FontWeight.Bold,
                                color = if (income) MaterialTheme.extra.income else MaterialTheme.extra.expense
                            )
                            Switch(checked = r.active, onCheckedChange = { vm.setRecurringActive(r.id, it) })
                        }
                    }
                }
            }
        }
    }

    editing?.let { init ->
        RecurringEditor(
            init, cats, cur, onDismiss = { editing = null },
            onDelete = { vm.deleteRecurring(init.id); editing = null },
            onSave = { r -> vm.saveRecurring(r) { editing = null } }
        )
    }
}

@Composable
private fun RecurringEditor(
    initial: RecurringEntity, cats: List<CategoryEntity>, cur: String,
    onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (RecurringEntity) -> Unit
) {
    val ctx = LocalContext.current
    val isNew = initial.id == 0L
    var type by remember { mutableStateOf(initial.type) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var category by remember { mutableStateOf(initial.category) }
    var freq by remember { mutableStateOf(initial.frequency) }
    var date by remember { mutableStateOf(initial.nextDue) }
    var note by remember { mutableStateOf(initial.note) }

    val typeCats = cats.filter { it.type == type }.sortedBy { it.sortOrder }
    LaunchedEffect(type, typeCats.size) {
        if (typeCats.isNotEmpty() && typeCats.none { it.name == category }) category = typeCats.first().name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Transaksi Berulang Baru" else "Ubah Transaksi Berulang") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == TYPE_INCOME, { type = TYPE_INCOME }, label = { Text("Pemasukan") })
                    FilterChip(type == TYPE_EXPENSE, { type = TYPE_EXPENSE }, label = { Text("Pengeluaran") })
                }
                OutlinedTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Jumlah") }, prefix = { Text(Fmt.symbol(cur) + " ") }, singleLine = true,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text("Kategori", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeCats.forEach { c -> FilterChip(category == c.name, { category = c.name }, label = { Text(c.name) }) }
                }
                Text("Pengulangan", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(freq == FREQ_MONTHLY, { freq = FREQ_MONTHLY }, label = { Text("Tiap bulan") })
                    FilterChip(freq == FREQ_WEEKLY, { freq = FREQ_WEEKLY }, label = { Text("Tiap minggu") })
                }
                Text(if (isNew) "Mulai tanggal" else "Jatuh tempo berikutnya", style = MaterialTheme.typography.labelLarge)
                DateField(date) { date = it }
                if (date < Dates.today()) {
                    Text(
                        "Tanggal sudah lewat: transaksi sejak tanggal ini sampai hari ini akan langsung dicatat.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(60) }, label = { Text("Nama / catatan (opsional)") },
                    placeholder = { Text("Contoh: Gaji, Netflix, Cicilan motor") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text("Hapus aturan ini (transaksi lama tetap ada)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.toLongOrNull() ?: 0L
                when {
                    v <= 0L -> Toast.makeText(ctx, "Nominal harus lebih dari 0", Toast.LENGTH_SHORT).show()
                    category.isBlank() -> Toast.makeText(ctx, "Pilih kategori", Toast.LENGTH_SHORT).show()
                    else -> {
                        val day = Dates.startOfDay(date)
                        onSave(
                            initial.copy(
                                type = type, amount = v, category = category, note = note.trim(), frequency = freq,
                                anchorDay = Dates.dayOfMonthOf(day), nextDue = day
                            )
                        )
                    }
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
