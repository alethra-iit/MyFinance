@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.ConvertResult
import com.myfinance.app.data.ShoppingItemEntity
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.Purple
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

private fun ShoppingItemEntity.subtotal() = quantity.toLong() * price

// ======================== Daftar catatan belanja ========================
@Composable
fun ShoppingListScreen(shop: ShoppingViewModel, nav: NavController, cur: String) {
    val lists by shop.lists.collectAsState()
    val items by shop.items.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }

    // Barang yang sudah dicatat jadi pengeluaran tidak lagi dihitung (kalau tidak, angkanya menumpuk selamanya)
    val totalEstimate = items.filter { !it.converted }.sumOf { it.subtotal() }
    val unbought = items.filter { !it.checked }.sumOf { it.subtotal() }
    val shown = lists.filter { it.title.contains(query.trim(), true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Belanja") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, "Buat daftar belanja")
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.ShoppingCart, Purple, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Estimasi Belum Dicatat", style = MaterialTheme.typography.bodySmall)
                            Text(Fmt.money(totalEstimate, cur), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Belum dibeli: ${Fmt.money(unbought, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SearchField(query, { query = it }, "Cari daftar belanja…") }
            if (shown.isEmpty()) {
                item { EmptyState(if (lists.isEmpty()) "Belum ada daftar belanja. Ketuk + untuk membuat." else "Daftar tidak ditemukan.") }
            }
            items(shown, key = { it.id }) { l ->
                val li = items.filter { it.listId == l.id }
                val est = li.sumOf { it.subtotal() }
                SoftCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("shopping/${l.id}") }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.ShoppingBag, Color(0xFFF59E0B))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Fmt.date(l.createdAt)} • ${Fmt.money(est, cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${li.count { it.checked }} dari ${li.size} barang dibeli" + if (l.lastConvertedAt != null) " • Sudah dicatat" else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showNew) {
        TextInputDialog(
            title = "Daftar Belanja Baru", label = "Judul daftar", initial = "",
            onDismiss = { showNew = false },
            onConfirm = { title -> showNew = false; shop.createList(title) { id -> nav.navigate("shopping/$id") } }
        )
    }
}

// ======================== Detail daftar belanja ========================
@Composable
fun ShoppingDetailScreen(shop: ShoppingViewModel, app: AppViewModel, listId: Long, nav: NavController, cur: String) {
    val ctx = LocalContext.current
    val lists by shop.lists.collectAsState()
    val allItems by shop.items.collectAsState()
    val cats by app.categories.collectAsState()
    val list = lists.firstOrNull { it.id == listId }

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deletingList by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var addedInRow by remember { mutableStateOf(0) }   // jumlah barang yang ditambah beruntun dalam satu sesi dialog

    if (list == null) { Box(Modifier.fillMaxSize()); return }

    // Yang sudah dicentang (dan yang sudah dicatat) turun ke bawah; yang belum dibeli tetap di atas
    val items = allItems.filter { it.listId == listId }.sortedWith(compareBy<ShoppingItemEntity>({ it.checked }, { it.id }))
    val total = items.sumOf { it.subtotal() }
    val bought = items.filter { it.checked }
    val notBought = items.filter { !it.checked }
    val pending = items.filter { it.checked && !it.converted }
    val pendingTotal = pending.sumOf { it.subtotal() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(list.title, onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = {
                    shop.duplicateList(listId) { newId ->
                        Toast.makeText(ctx, "Daftar disalin", Toast.LENGTH_SHORT).show()
                        nav.navigate("shopping/$newId")
                    }
                }) { Icon(Icons.Rounded.ContentCopy, "Salin daftar") }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Ubah judul") }
                IconButton(onClick = { deletingList = true }) { Icon(Icons.Rounded.Delete, "Hapus daftar") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Barang")
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { converting = true }, enabled = pending.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(16.dp)
                ) { Text("Catat sebagai Pengeluaran", fontWeight = FontWeight.Bold) }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total Estimasi", style = MaterialTheme.typography.bodySmall)
                        Text(Fmt.money(total, cur), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Sudah dibeli", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${bought.size} barang", fontWeight = FontWeight.SemiBold)
                                Text(Fmt.money(bought.sumOf { it.subtotal() }, cur), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Belum dibeli", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${notBought.size} barang", fontWeight = FontWeight.SemiBold)
                                Text(Fmt.money(notBought.sumOf { it.subtotal() }, cur), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("Belum ada barang. Ketuk \"Barang\" untuk menambah.") }
            items(items, key = { it.id }) { it ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = it.checked, enabled = !it.converted, onCheckedChange = { c -> shop.setChecked(it.id, c) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                it.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (it.checked) TextDecoration.LineThrough else null
                            )
                            Text("${it.quantity} × ${Fmt.money(it.price, cur)} = ${Fmt.money(it.subtotal(), cur)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (it.converted) Text("Sudah dicatat sebagai pengeluaran", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (it.converted) {
                            // Sudah jadi transaksi: mengubah/menghapus di sini tidak akan mengubah transaksinya, jadi dikunci
                            Icon(
                                Icons.Rounded.Lock, "Terkunci karena sudah dicatat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp).size(20.dp)
                            )
                        } else {
                            IconButton(onClick = { editing = it }) { Icon(Icons.Rounded.Edit, "Edit barang") }
                            IconButton(onClick = { deletingItem = it }) { Icon(Icons.Rounded.Delete, "Hapus barang", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ItemDialog(
            initial = editing,
            addedCount = addedInRow,
            onSaveNext = { name, qty, price ->
                shop.saveItem(ShoppingItemEntity(listId = listId, name = name, quantity = qty, price = price))
                addedInRow++
            },
            onDismiss = { adding = false; editing = null; addedInRow = 0 },
            onSave = { name, qty, price ->
                val e = editing
                if (e == null) shop.saveItem(ShoppingItemEntity(listId = listId, name = name, quantity = qty, price = price))
                else shop.saveItem(e.copy(name = name, quantity = qty, price = price))
                adding = false; editing = null; addedInRow = 0
            }
        )
    }
    val toDelete = deletingItem
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Hapus barang?") },
            text = { Text("\"${toDelete.name}\" akan dihapus dari daftar ini.") },
            confirmButton = { TextButton(onClick = { deletingItem = null; shop.deleteItem(toDelete.id) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Batal") } }
        )
    }
    if (renaming) {
        TextInputDialog("Ubah Judul", "Judul daftar", list.title, { renaming = false }) { t -> renaming = false; shop.renameList(listId, t) }
    }
    if (deletingList) {
        AlertDialog(
            onDismissRequest = { deletingList = false },
            title = { Text("Hapus daftar belanja?") },
            text = { Text("Transaksi yang sudah dicatat dari daftar ini tidak ikut terhapus.") },
            confirmButton = { TextButton(onClick = { deletingList = false; shop.deleteList(listId); nav.popBackStack() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingList = false }) { Text("Batal") } }
        )
    }
    if (converting) {
        val expCats = cats.filter { it.type == TYPE_EXPENSE }.sortedBy { it.sortOrder }.map { it.name }
        var cat by remember { mutableStateOf(if ("Belanja" in expCats) "Belanja" else expCats.firstOrNull() ?: "Belanja") }
        var date by remember { mutableStateOf(Dates.today()) }
        AlertDialog(
            onDismissRequest = { converting = false },
            title = { Text("Catat sebagai Pengeluaran") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${pending.size} barang • ${Fmt.money(pendingTotal, cur)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Kategori", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        expCats.forEach { c ->
                            FilterChip(
                                selected = c == cat, onClick = { cat = c }, label = { Text(c) },
                                leadingIcon = if (c == cat) {
                                    { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Text("Tanggal transaksi", style = MaterialTheme.typography.labelLarge)
                    DateField(date) { date = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    converting = false
                    shop.convert(listId, cat, date) { r, alerts ->
                        val msg = when (r) {
                            ConvertResult.OK -> "Berhasil dicatat sebagai pengeluaran"
                            ConvertResult.NOTHING -> "Tidak ada barang baru untuk dicatat"
                            ConvertResult.ZERO_TOTAL -> "Isi harga estimasi terlebih dahulu"
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        alerts.forEach { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { converting = false }) { Text("Batal") } }
        )
    }
}

// ======================== Dialog pembantu ========================
@Composable
fun TextInputDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(60) }, label = { Text(label) },
                singleLine = true, shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ItemDialog(
    initial: ShoppingItemEntity?,
    addedCount: Int,
    onSaveNext: (String, Int, Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var qty by remember { mutableStateOf((initial?.quantity ?: 1).toString()) }
    var price by remember { mutableStateOf(if ((initial?.price ?: 0L) > 0L) initial!!.price.toString() else "") }
    val valid = name.isNotBlank() && (qty.toIntOrNull() ?: 0) >= 1
    val focus = remember { FocusRequester() }
    // Fokus langsung ke nama barang supaya keyboard terbuka dan bisa langsung mengetik
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Barang" else "Edit Barang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it.take(60) }, label = { Text("Nama barang") }, singleLine = true,
                    modifier = Modifier.focusRequester(focus), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    qty, { qty = it.filter(Char::isDigit).take(4) }, label = { Text("Jumlah") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    price, { price = it.filter(Char::isDigit).take(12) }, label = { Text("Harga estimasi per barang") }, singleLine = true,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp)
                )
                // Hanya saat menambah (bukan mengedit): simpan lalu kosongkan form untuk barang berikutnya
                if (initial == null) {
                    if (addedCount > 0) {
                        Text("$addedCount barang sudah ditambahkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveNext(name.trim(), qty.toIntOrNull() ?: 1, price.toLongOrNull() ?: 0L)
                            name = ""; qty = "1"; price = ""
                            runCatching { focus.requestFocus() }
                        },
                        enabled = valid, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    ) { Text("Simpan & tambah lagi") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), qty.toIntOrNull() ?: 1, price.toLongOrNull() ?: 0L) }, enabled = valid) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (addedCount > 0) "Selesai" else "Batal") } }
    )
}
