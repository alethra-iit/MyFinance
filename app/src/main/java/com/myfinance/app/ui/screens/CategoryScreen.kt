@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.CategoryEntity
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.ui.*

@Composable
fun CategoryScreen(vm: AppViewModel, nav: NavController) {
    val cats by vm.categories.collectAsState()
    var type by rememberSaveable { mutableStateOf(TYPE_EXPENSE) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    val list = cats.filter { it.type == type }.sortedBy { it.sortOrder }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Kelola Kategori", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = CategoryEntity(name = "", type = type) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Tambah") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == TYPE_EXPENSE, { type = TYPE_EXPENSE }, label = { Text("Pengeluaran") })
                    FilterChip(type == TYPE_INCOME, { type = TYPE_INCOME }, label = { Text("Pemasukan") })
                }
            }
            item {
                Text(
                    "Ketuk kategori untuk mengubah nama, ikon, dan warna. Mengganti nama otomatis memperbarui transaksi, anggaran, dan transaksi berulang yang memakainya.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(list, key = { it.id }) { c ->
                SoftCard(Modifier.fillMaxWidth(), onClick = { editing = c }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(categoryIcon(c.name, c.type), categoryColor(c.name, c.type))
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { init ->
        CategoryEditor(
            init, cats, canDelete = init.id != 0L && list.size > 1,
            onDismiss = { editing = null },
            onDelete = { deleting = init; editing = null },
            onSave = { c -> vm.saveCategory(c) { editing = null } }
        )
    }
    deleting?.let { c ->
        DeleteCategoryDialog(vm, c, cats.filter { it.type == c.type && it.id != c.id }.sortedBy { it.sortOrder }) { deleting = null }
    }
}

@Composable
private fun CategoryEditor(
    initial: CategoryEntity, all: List<CategoryEntity>, canDelete: Boolean,
    onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (CategoryEntity) -> Unit
) {
    val ctx = LocalContext.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var iconKey by remember { mutableStateOf(if (isNew) "more" else categoryIconKey(initial.name, initial.type)) }
    var color by remember { mutableStateOf(if (isNew) CategoryPalette.last() else categoryColor(initial.name, initial.type)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Kategori Baru" else "Ubah Kategori") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(CategoryIcons.get(iconKey), color, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(20) }, label = { Text("Nama kategori") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.weight(1f)
                    )
                }
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryPalette.forEach { c ->
                        val sel = c.toArgb() == color.toArgb()
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c).clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) { if (sel) Icon(Icons.Rounded.Check, "Dipilih", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.all.forEach { (key, vec) ->
                        val sel = key == iconKey
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (sel) color.copy(alpha = .2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .then(if (sel) Modifier.border(BorderStroke(2.dp, color), CircleShape) else Modifier)
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center
                        ) { Icon(vec, key, tint = if (sel) color else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("Hapus kategori ini", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                when {
                    n.isBlank() -> Toast.makeText(ctx, "Isi nama kategori", Toast.LENGTH_SHORT).show()
                    all.any { it.type == initial.type && it.id != initial.id && it.name.equals(n, ignoreCase = true) } ->
                        Toast.makeText(ctx, "Nama kategori sudah ada", Toast.LENGTH_SHORT).show()
                    else -> onSave(initial.copy(name = n, icon = iconKey, color = color.toArgb().toLong() and 0xFFFFFFFFL))
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DeleteCategoryDialog(vm: AppViewModel, c: CategoryEntity, others: List<CategoryEntity>, onClose: () -> Unit) {
    var usage by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf(others.firstOrNull { it.name == "Lainnya" }?.name ?: others.firstOrNull()?.name) }
    LaunchedEffect(c.id) { usage = vm.categoryUsage(c) }
    val n = usage

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Hapus \"${c.name}\"?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    n == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    n == 0 -> Text("Kategori ini belum dipakai transaksi mana pun.")
                    else -> {
                        Text("$n transaksi/aturan berulang memakai kategori ini. Pindahkan ke:")
                        others.forEach { o ->
                            Row(
                                Modifier.fillMaxWidth().clickable { target = o.name },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(target == o.name, { target = o.name })
                                Text(o.name)
                            }
                        }
                    }
                }
                if (c.type == TYPE_EXPENSE) {
                    Text(
                        "Anggaran untuk kategori ini (bila ada) ikut dihapus.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = n != null && (n == 0 || target != null),
                onClick = { vm.deleteCategory(c.id, if (n == 0) null else target) { onClose() } }
            ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
