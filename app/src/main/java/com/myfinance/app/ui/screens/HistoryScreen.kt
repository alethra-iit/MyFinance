@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.ui.*
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val txs by vm.transactions.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRange by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(txs, filter, query, from, to) {
        val f = from
        val t = to
        val q = query.trim()
        txs.filter { tx ->
            (filter == "ALL" || tx.type == filter) &&
                (q.isEmpty() || tx.category.contains(q, true) || tx.note.contains(q, true) ||
                    tx.amount.toString().contains(q.replace(".", ""))) &&
                (f == null || tx.dateMillis >= f) &&
                (t == null || tx.dateMillis < t + Dates.DAY)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar("Riwayat Transaksi", actions = {
                IconButton(onClick = { showRange = true }) {
                    Icon(
                        Icons.Rounded.FilterList, "Filter tanggal",
                        tint = if (from != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Semua", TYPE_INCOME to "Pemasukan", TYPE_EXPENSE to "Pengeluaran").forEach { (k, label) ->
                    val sel = filter == k
                    Surface(
                        onClick = { filter = k }, modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "Cari kategori, catatan, atau nominal…")
            val f = from
            val t = to
            if (f != null && t != null) {
                Spacer(Modifier.height(8.dp))
                InputChip(
                    selected = true, onClick = { showRange = true },
                    label = { Text(if (f == t) Fmt.date(f) else "${Fmt.dayMonth(f)} – ${Fmt.date(t)}") },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) }
                )
                TextButton(onClick = { from = null; to = null }) { Text("Hapus filter tanggal") }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("Tidak ada transaksi yang cocok.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.id }) { tx -> TransactionRow(tx, cur) { selectedId = tx.id } }
                }
            }
        }
    }

    if (showRange) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = st.selectedStartDateMillis
                    val e = st.selectedEndDateMillis
                    if (s != null) { from = Dates.utcToLocal(s); to = Dates.utcToLocal(e ?: s) }
                    showRange = false
                }) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { from = null; to = null; showRange = false }) { Text("Hapus Filter") }
            }
        ) { DateRangePicker(state = st, modifier = Modifier.height(520.dp)) }
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
