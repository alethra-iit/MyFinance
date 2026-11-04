@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.WalletEntity
import com.myfinance.app.ui.*
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.Period

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val txs by vm.transactions.collectAsState()
    val receiptIds by vm.receiptIds.collectAsState()
    val categories by vm.categories.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val multiWallet = wallets.size > 1
    val walletNames = remember(wallets) { wallets.associate { it.id to it.name } }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    // Daftar nama kategori terpilih (kosong = semua kategori)
    var selCats by rememberSaveable(stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })) {
        mutableStateOf<List<String>>(emptyList())
    }
    // Id dompet terpilih (kosong = semua dompet)
    var selWallets by rememberSaveable(stateSaver = listSaver<List<Long>, Long>(save = { it }, restore = { it })) {
        mutableStateOf<List<Long>>(emptyList())
    }
    var minAmt by rememberSaveable { mutableStateOf("") }
    var maxAmt by rememberSaveable { mutableStateOf("") }
    var showRange by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val minV = minAmt.toLongOrNull()
    val maxV = maxAmt.toLongOrNull()

    val filtered = remember(txs, filter, query, from, to, selCats, selWallets, minAmt, maxAmt) {
        val f = from
        val t = to
        val q = query.trim()
        txs.filter { tx ->
            (filter == "ALL" || tx.type == filter) &&
                (q.isEmpty() || tx.category.contains(q, true) || tx.note.contains(q, true) ||
                    tx.amount.toString().contains(q.replace(".", ""))) &&
                (f == null || tx.dateMillis >= f) &&
                (t == null || tx.dateMillis < t + Dates.DAY) &&
                (selCats.isEmpty() || tx.category in selCats) &&
                (selWallets.isEmpty() || tx.walletId in selWallets) &&
                (minV == null || tx.amount >= minV) &&
                (maxV == null || tx.amount <= maxV)
        }
    }

    val advancedCount = (if (from != null) 1 else 0) + (if (selCats.isNotEmpty()) 1 else 0) +
        (if (selWallets.isNotEmpty()) 1 else 0) +
        (if (minV != null || maxV != null) 1 else 0)
    val anyFilter = advancedCount > 0 || query.isNotBlank() || filter != "ALL"
    val totals = remember(filtered) {
        filtered.filter { it.type == TYPE_INCOME }.sumOf { it.amount } to
            filtered.filter { it.type == TYPE_EXPENSE }.sumOf { it.amount }
    }

    fun resetAdvanced() { from = null; to = null; selCats = emptyList(); selWallets = emptyList(); minAmt = ""; maxAmt = "" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar("Riwayat Transaksi", actions = {
                IconButton(onClick = { showFilter = true }) {
                    BadgedBox(badge = { if (advancedCount > 0) Badge { Text(advancedCount.toString()) } }) {
                        Icon(
                            Icons.Rounded.FilterList, "Filter lanjutan",
                            tint = if (advancedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Semua", TYPE_INCOME to "Pemasukan", TYPE_EXPENSE to "Pengeluaran").forEach { (k, label) ->
                    val sel = filter == k
                    Surface(
                        onClick = {
                            filter = k
                            // Kategori terpilih yang bukan milik jenis ini dibuang agar tidak "tersembunyi"
                            if (k != "ALL") {
                                val valid = categories.filter { it.type == k }.map { it.name }.toSet()
                                selCats = selCats.filter { it in valid }
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
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

            // Ringkasan filter lanjutan yang aktif; ketuk chip untuk membuka panel, ketuk X untuk menghapus
            if (advancedCount > 0) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val f = from
                    val t = to
                    if (f != null && t != null) {
                        ActiveChip(if (f == t) Fmt.date(f) else "${Fmt.dayMonth(f)} – ${Fmt.date(t)}", { showFilter = true }) { from = null; to = null }
                    }
                    if (selCats.isNotEmpty()) {
                        ActiveChip(if (selCats.size <= 2) selCats.joinToString(", ") else "${selCats.size} kategori", { showFilter = true }) { selCats = emptyList() }
                    }
                    if (selWallets.isNotEmpty()) {
                        val label = selWallets.mapNotNull { walletNames[it] }.let { n -> if (n.size <= 2) n.joinToString(", ") else "${n.size} dompet" }
                        ActiveChip(label, { showFilter = true }) { selWallets = emptyList() }
                    }
                    if (minV != null || maxV != null) {
                        val label = when {
                            minV != null && maxV != null -> "${Fmt.money(minV, cur)} – ${Fmt.money(maxV, cur)}"
                            minV != null -> "≥ ${Fmt.money(minV, cur)}"
                            else -> "≤ ${Fmt.money(maxV ?: 0L, cur)}"
                        }
                        ActiveChip(label, { showFilter = true }) { minAmt = ""; maxAmt = "" }
                    }
                    TextButton(onClick = { resetAdvanced() }) { Text("Hapus semua") }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (anyFilter) {
                Text(
                    "${filtered.size} transaksi • Masuk ${Fmt.money(totals.first, cur)} • Keluar ${Fmt.money(totals.second, cur)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            if (filtered.isEmpty()) {
                EmptyState("Tidak ada transaksi yang cocok.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.id }) { tx -> TransactionRow(tx, cur, hasReceipt = tx.id in receiptIds, walletName = if (multiWallet) walletNames[tx.walletId] else null) { selectedId = tx.id } }
                }
            }
        }
    }

    if (showFilter) {
        val names = categories.filter { filter == "ALL" || it.type == filter }
            .sortedWith(compareBy({ it.type }, { it.sortOrder })).map { it.name }.distinct()
        HistoryFilterSheet(
            cur = cur, categoryNames = names, selected = selCats, onSelected = { selCats = it },
            wallets = if (multiWallet) wallets else emptyList(), selWallets = selWallets, onWallets = { selWallets = it },
            from = from, to = to, onPickRange = { showRange = true }, onRange = { s, e -> from = s; to = e },
            minAmt = minAmt, maxAmt = maxAmt, onMin = { minAmt = it }, onMax = { maxAmt = it },
            resultCount = filtered.size, onReset = { resetAdvanced() }, onDismiss = { showFilter = false }
        )
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
private fun ActiveChip(label: String, onClick: () -> Unit, onClear: () -> Unit) {
    InputChip(
        selected = true, onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Rounded.Close, "Hapus filter", Modifier.size(18.dp).clickable(onClick = onClear)) }
    )
}

@Composable
private fun HistoryFilterSheet(
    cur: String,
    categoryNames: List<String>, selected: List<String>, onSelected: (List<String>) -> Unit,
    wallets: List<WalletEntity>, selWallets: List<Long>, onWallets: (List<Long>) -> Unit,
    from: Long?, to: Long?, onPickRange: () -> Unit, onRange: (Long?, Long?) -> Unit,
    minAmt: String, maxAmt: String, onMin: (String) -> Unit, onMax: (String) -> Unit,
    resultCount: Int, onReset: () -> Unit, onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val min = minAmt.toLongOrNull()
    val max = maxAmt.toLongOrNull()
    val invalid = min != null && max != null && min > max

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter Lanjutan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Reset") }
            }

            // ---- Rentang tanggal ----
            Text("Rentang Tanggal", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val today = Dates.today()
                fun lastDayOf(p: Period) = Dates.startOfDay(Dates.range(p, today).second - 1)
                AssistChip(onClick = { onRange(today, today) }, label = { Text("Hari ini") })
                AssistChip(onClick = { onRange(Dates.shift(Period.DAY, today, -6), today) }, label = { Text("7 hari terakhir") })
                AssistChip(onClick = { onRange(Dates.range(Period.MONTH, today).first, lastDayOf(Period.MONTH)) }, label = { Text("Bulan ini") })
                AssistChip(onClick = { onRange(Dates.range(Period.YEAR, today).first, lastDayOf(Period.YEAR)) }, label = { Text("Tahun ini") })
            }
            OutlinedButton(onClick = onPickRange, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                val f = from
                val t = to
                Text(
                    if (f != null && t != null) (if (f == t) Fmt.date(f) else "${Fmt.date(f)} – ${Fmt.date(t)}") else "Pilih tanggal sendiri…"
                )
            }

            // ---- Kategori ----
            Text("Kategori", fontWeight = FontWeight.SemiBold)
            if (categoryNames.isEmpty()) {
                Text("Belum ada kategori.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryNames.forEach { name ->
                        val on = name in selected
                        FilterChip(
                            selected = on,
                            onClick = { onSelected(if (on) selected - name else selected + name) },
                            label = { Text(name) }
                        )
                    }
                }
                Text(
                    if (selected.isEmpty()) "Tidak ada yang dipilih = semua kategori" else "${selected.size} kategori dipilih",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Dompet (hanya bila punya lebih dari satu) ----
            if (wallets.isNotEmpty()) {
                Text("Dompet", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    wallets.forEach { w ->
                        val on = w.id in selWallets
                        FilterChip(
                            selected = on,
                            onClick = { onWallets(if (on) selWallets - w.id else selWallets + w.id) },
                            label = { Text(w.name) }
                        )
                    }
                }
                Text(
                    if (selWallets.isEmpty()) "Tidak ada yang dipilih = semua dompet" else "${selWallets.size} dompet dipilih",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Rentang nominal ----
            Text("Rentang Nominal", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minAmt, onValueChange = { v -> onMin(v.filter { it.isDigit() }.take(12)) },
                    modifier = Modifier.weight(1f), label = { Text("Minimum") },
                    prefix = { Text(Fmt.symbol(cur) + " ") }, placeholder = { Text("0") }, singleLine = true, isError = invalid,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors()
                )
                OutlinedTextField(
                    value = maxAmt, onValueChange = { v -> onMax(v.filter { it.isDigit() }.take(12)) },
                    modifier = Modifier.weight(1f), label = { Text("Maksimum") },
                    prefix = { Text(Fmt.symbol(cur) + " ") }, placeholder = { Text("Tanpa batas") }, singleLine = true, isError = invalid,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors()
                )
            }
            if (invalid) {
                Text("Minimum lebih besar dari maksimum, jadi tidak akan ada hasil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Tampilkan $resultCount transaksi", fontWeight = FontWeight.Bold)
            }
        }
    }
}
