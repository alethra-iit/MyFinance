@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.TransferEntity
import com.myfinance.app.data.WALLET_OTHER
import com.myfinance.app.data.WalletEntity
import com.myfinance.app.ui.*
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Fmt

@Composable
fun WalletScreen(vm: AppViewModel, nav: NavController, cur: String, openTransfer: Boolean = false) {
    val ctx = LocalContext.current
    val wallets by vm.wallets.collectAsState()
    val balances by vm.balances.collectAsState()
    val transfers by vm.transfers.collectAsState()

    var editing by remember { mutableStateOf<WalletEntity?>(null) }
    var deleting by remember { mutableStateOf<WalletEntity?>(null) }
    var transferOpen by rememberSaveable { mutableStateOf(openTransfer) }
    var transferEdit by remember { mutableStateOf<TransferEntity?>(null) }

    val names = wallets.associate { it.id to it.name }
    val total = wallets.sumOf { balances[it.id] ?: 0L }
    val red = MaterialTheme.extra.expense

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Dompet & Transfer", onBack = { nav.popBackStack() }) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6554E8), Color(0xFF8F7FF5))))
                        .padding(20.dp)
                ) {
                    Column {
                        Text("Total Saldo", color = Color.White.copy(alpha = .9f))
                        Text(Fmt.money(total, cur), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${wallets.size} dompet", color = Color.White.copy(alpha = .85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { transferEdit = null; transferOpen = true },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)
                    ) { Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(8.dp)); Text("Transfer") }
                    OutlinedButton(
                        onClick = { editing = WalletEntity(name = "", kind = WALLET_OTHER) },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)
                    ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Tambah Dompet") }
                }
            }

            item { Text("Dompet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(wallets, key = { "w${it.id}" }) { w ->
                val bal = balances[w.id] ?: 0L
                SoftCard(Modifier.fillMaxWidth(), onClick = { editing = w }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(walletIcon(w.kind), walletColor(w.kind))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                walletKindLabel(w.kind), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(Fmt.money(bal, cur), fontWeight = FontWeight.Bold, color = if (bal < 0) red else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            item { Text("Riwayat Transfer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (transfers.isEmpty()) {
                item { EmptyState("Belum ada transfer. Pindahkan uang antar dompet, misalnya tarik tunai dari bank atau top up e-wallet.") }
            }
            items(transfers, key = { "t${it.id}" }) { t ->
                SoftCard(Modifier.fillMaxWidth(), onClick = { transferEdit = t; transferOpen = true }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.SwapHoriz, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${names[t.fromWalletId] ?: "?"} → ${names[t.toWalletId] ?: "?"}",
                                fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                            Text(
                                Fmt.date(t.dateMillis) + if (t.note.isNotBlank()) " • ${t.note}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                        Text(Fmt.money(t.amount, cur), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // ---------- Dialog dompet ----------
    editing?.let { w ->
        WalletEditorDialog(
            w, cur, canDelete = wallets.size > 1,
            onDismiss = { editing = null },
            onSave = { saved -> vm.saveWallet(saved) { editing = null } },
            onDelete = { deleting = w; editing = null }
        )
    }

    deleting?.let { w ->
        val others = wallets.filter { it.id != w.id }
        var target by remember(w.id) { mutableStateOf(others.firstOrNull()?.id ?: 0L) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus dompet ${w.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Semua transaksi, transaksi berulang, dan transfer di dompet ini akan dipindahkan ke dompet pilihanmu, " +
                            "dan saldo awalnya digabung ke sana. Total uangmu tidak berubah.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Pindahkan ke", style = MaterialTheme.typography.labelLarge)
                    WalletPicker(others, target, { target = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.deleteWallet(w.id, target) { ok ->
                        Toast.makeText(ctx, if (ok) "Dompet dihapus" else "Dompet tidak bisa dihapus", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }

    // ---------- Dialog transfer ----------
    if (transferOpen) {
        if (wallets.size < 2) {
            AlertDialog(
                onDismissRequest = { transferOpen = false },
                title = { Text("Perlu minimal dua dompet") },
                text = { Text("Tambahkan satu dompet lagi (misalnya Rekening Bank atau E-Wallet) untuk bisa mentransfer.") },
                confirmButton = { TextButton(onClick = { transferOpen = false }) { Text("OK") } }
            )
        } else {
            val editingTransfer = transferEdit
            TransferDialog(
                wallets, balances, editingTransfer, cur,
                onDismiss = { transferOpen = false; transferEdit = null },
                onSave = { t ->
                    vm.saveTransfer(t) { ok ->
                        if (ok) { transferOpen = false; transferEdit = null }
                        else Toast.makeText(ctx, "Transfer tidak valid", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = editingTransfer?.let { et ->
                    { vm.deleteTransfer(et.id); transferOpen = false; transferEdit = null }
                }
            )
        }
    }
}
