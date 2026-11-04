@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.TransactionEntity
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

@Composable
fun AddTransactionScreen(vm: AppViewModel, nav: NavController, initialType: String, editId: Long, cur: String) {
    val ctx = LocalContext.current
    val cats by vm.categories.collectAsState()
    var type by rememberSaveable { mutableStateOf(initialType) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var note by rememberSaveable { mutableStateOf("") }
    var loaded by remember { mutableStateOf(editId < 0) }
    val editing = editId >= 0

    LaunchedEffect(editId) {
        if (editId >= 0) {
            vm.getTransaction(editId)?.let {
                type = it.type; amount = it.amount.toString(); category = it.category
                date = it.dateMillis; note = it.note
            }
            loaded = true
        }
    }

    val typeCats = cats.filter { it.type == type }.sortedBy { it.sortOrder }
    LaunchedEffect(type, typeCats.size, loaded) {
        if (loaded && typeCats.isNotEmpty() && typeCats.none { it.name == category }) category = typeCats.first().name
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(if (editing) "Ubah Transaksi" else "Tambah Transaksi", onBack = { nav.popBackStack() }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Jenis transaksi
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeButton("Pemasukan", Icons.Rounded.ArrowUpward, MaterialTheme.extra.income, type == TYPE_INCOME, Modifier.weight(1f)) { type = TYPE_INCOME }
                TypeButton("Pengeluaran", Icons.Rounded.ArrowDownward, MaterialTheme.extra.expense, type == TYPE_EXPENSE, Modifier.weight(1f)) { type = TYPE_EXPENSE }
            }
            // Nominal
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() }.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Jumlah") },
                prefix = { Text(Fmt.symbol(cur) + " ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                placeholder = { Text("0", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                visualTransformation = ThousandsTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp), colors = fieldColors()
            )
            // Kategori
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kategori", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate("categories") }) { Text("Kelola") }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4
            ) {
                typeCats.forEach { c ->
                    val sel = c.name == category
                    val cc = categoryColor(c.name, c.type)
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) cc.copy(alpha = .14f) else Color.Transparent)
                            .clickable { category = c.name }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Box(
                                Modifier.padding(3.dp).size(54.dp).clip(CircleShape)
                                    .background(if (sel) cc else cc.copy(alpha = .12f))
                                    .then(if (sel) Modifier.border(3.dp, cc.copy(alpha = .45f), CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) { Icon(categoryIcon(c.name, c.type), null, tint = if (sel) Color.White else cc) }
                            if (sel) {
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            c.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            // Tanggal
            Text("Tanggal", fontWeight = FontWeight.SemiBold)
            DateField(date) { date = it }
            // Keterangan
            Text("Catatan (Opsional)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (type == TYPE_INCOME) "Contoh: Gaji bulan ini" else "Contoh: Makan siang") },
                minLines = 2, shape = RoundedCornerShape(16.dp), colors = fieldColors()
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = amount.toLongOrNull() ?: 0L
                    when {
                        value <= 0L -> Toast.makeText(ctx, "Nominal harus lebih dari 0", Toast.LENGTH_SHORT).show()
                        category.isBlank() -> Toast.makeText(ctx, "Pilih kategori", Toast.LENGTH_SHORT).show()
                        else -> vm.saveTransaction(
                            TransactionEntity(
                                id = if (editing) editId else 0L, type = type, amount = value,
                                category = category, dateMillis = date, note = note.trim()
                            ),
                            onAlert = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
                        ) {
                            Toast.makeText(ctx, "Transaksi disimpan", Toast.LENGTH_SHORT).show()
                            nav.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("Simpan", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TypeButton(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color,
    selected: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label, fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
