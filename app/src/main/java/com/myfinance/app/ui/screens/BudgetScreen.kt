@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.ui.*
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.budgetStatuses

@Composable
fun BudgetScreen(vm: AppViewModel, nav: NavController, cur: String) {
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val txs by vm.transactions.collectAsState()
    val now = System.currentTimeMillis()
    val statuses = budgetStatuses(budgets, txs, now).associateBy { it.category }
    val expenseCats = cats.filter { it.type == TYPE_EXPENSE }.sortedBy { it.sortOrder }
    var editing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Anggaran Bulanan", onBack = { nav.popBackStack() }) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Tetapkan batas pengeluaran per kategori untuk ${Fmt.longMonth(now)}. Kamu akan diberi tahu saat pemakaian mencapai 80% dan saat melewati batas. Batas berlaku tiap bulan.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(expenseCats, key = { it.id }) { c ->
                val st = statuses[c.name]
                SoftCard(Modifier.fillMaxWidth(), onClick = { editing = c.name }) {
                    Box(Modifier.padding(16.dp)) {
                        if (st != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BudgetRow(st, { Fmt.money(it, cur) }, Modifier.weight(1f))
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(categoryIcon(c.name, c.type), categoryColor(c.name, c.type), size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold)
                                    Text("Belum diatur · ketuk untuk menetapkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { name ->
        val existing = budgets.firstOrNull { it.category == name }
        var text by remember(name) { mutableStateOf(existing?.amount?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Anggaran $name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Batas pengeluaran per bulan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(12) },
                        prefix = { Text(Fmt.symbol(cur) + " ") }, placeholder = { Text("0") },
                        singleLine = true, visualTransformation = ThousandsTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp), colors = fieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setBudget(name, text.toLongOrNull() ?: 0L); editing = null }) { Text("Simpan") }
            },
            dismissButton = {
                Row {
                    if (existing != null) {
                        TextButton(onClick = { vm.setBudget(name, 0L); editing = null }) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text("Batal") }
                }
            }
        )
    }
}
