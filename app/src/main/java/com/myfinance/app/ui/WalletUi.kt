@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myfinance.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myfinance.app.data.TransferEntity
import com.myfinance.app.data.WALLET_BANK
import com.myfinance.app.data.WALLET_CASH
import com.myfinance.app.data.WALLET_EWALLET
import com.myfinance.app.data.WALLET_OTHER
import com.myfinance.app.data.WalletEntity
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

val WalletKinds = listOf(WALLET_CASH, WALLET_BANK, WALLET_EWALLET, WALLET_OTHER)

fun walletIcon(kind: String): ImageVector = when (kind) {
    WALLET_CASH -> Icons.Rounded.Payments
    WALLET_BANK -> Icons.Rounded.AccountBalance
    WALLET_EWALLET -> Icons.Rounded.PhoneAndroid
    else -> Icons.Rounded.AccountBalanceWallet
}

fun walletColor(kind: String): Color = when (kind) {
    WALLET_CASH -> Color(0xFF22C55E)
    WALLET_BANK -> Color(0xFF3B82F6)
    WALLET_EWALLET -> Color(0xFFA855F7)
    else -> Color(0xFFF59E0B)
}

fun walletKindLabel(kind: String): String = when (kind) {
    WALLET_CASH -> "Tunai"
    WALLET_BANK -> "Rekening Bank"
    WALLET_EWALLET -> "E-Wallet"
    else -> "Lainnya"
}

/** Id dompet yang berlaku: [id] bila masih ada di [wallets], kalau tidak dompet pertama. */
fun effectiveWalletId(wallets: List<WalletEntity>, id: Long): Long =
    if (wallets.any { it.id == id }) id else (wallets.firstOrNull()?.id ?: id)

/** Pilihan dompet berupa deretan chip. Pemanggil biasanya menampilkannya hanya bila dompet lebih dari satu. */
@Composable
fun WalletPicker(wallets: List<WalletEntity>, selectedId: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        wallets.forEach { w ->
            FilterChip(
                selected = w.id == selectedId,
                onClick = { onSelect(w.id) },
                label = { Text(w.name, maxLines = 1) },
                leadingIcon = { Icon(walletIcon(w.kind), null, Modifier.size(FilterChipDefaults.IconSize)) }
            )
        }
    }
}

/** Tambah / ubah dompet: nama, jenis, dan saldo awal. */
@Composable
fun WalletEditorDialog(
    initial: WalletEntity, cur: String, canDelete: Boolean,
    onDismiss: () -> Unit, onSave: (WalletEntity) -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var balance by remember { mutableStateOf(if (initial.initialBalance != 0L) initial.initialBalance.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Dompet Baru" else "Ubah Dompet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, label = { Text("Nama dompet") },
                    placeholder = { Text("Contoh: BCA, GoPay, Dompet Harian") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text("Jenis", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletKinds.forEach { k ->
                        FilterChip(
                            selected = kind == k, onClick = { kind = k },
                            label = { Text(walletKindLabel(k)) },
                            leadingIcon = { Icon(walletIcon(k), null, Modifier.size(FilterChipDefaults.IconSize)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = balance, onValueChange = { v -> balance = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Saldo awal") }, prefix = { Text(Fmt.symbol(cur) + " ") }, placeholder = { Text("0") },
                    singleLine = true, visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Saldo dompet = saldo awal + pemasukan − pengeluaran ± transfer. Isi saldo awal dengan uang yang sudah ada di dompet ini sebelum dicatat di aplikasi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isNew) {
                    if (canDelete) {
                        TextButton(onClick = onDelete) { Text("Hapus dompet ini…", color = MaterialTheme.colorScheme.error) }
                    } else {
                        Text(
                            "Dompet terakhir tidak bisa dihapus.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) Toast.makeText(ctx, "Nama dompet tidak boleh kosong", Toast.LENGTH_SHORT).show()
                else onSave(initial.copy(name = name.trim(), kind = kind, initialBalance = balance.toLongOrNull() ?: 0L))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/**
 * Transfer antar dompet (baru atau ubah). [balances] dipakai hanya untuk memberi peringatan bila saldo
 * dompet asal tidak cukup; transfer tetap boleh disimpan (saldo bisa negatif, mis. kartu kredit).
 */
@Composable
fun TransferDialog(
    wallets: List<WalletEntity>, balances: Map<Long, Long>, initial: TransferEntity?, cur: String,
    onDismiss: () -> Unit, onSave: (TransferEntity) -> Unit, onDelete: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var src by remember { mutableStateOf(initial?.fromWalletId ?: wallets.firstOrNull()?.id ?: 0L) }
    var dest by remember { mutableStateOf(initial?.toWalletId ?: wallets.getOrNull(1)?.id ?: 0L) }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(initial?.dateMillis ?: Dates.today()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val value = amount.toLongOrNull() ?: 0L
    // Saat mengubah transfer lama, saldo sudah memuat transfer itu; kembalikan dulu supaya perbandingannya adil
    val available = (balances[src] ?: 0L) + (if (initial != null && initial.fromWalletId == src) initial.amount else 0L)
    val srcName = wallets.firstOrNull { it.id == src }?.name ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Transfer Antar Dompet" else "Ubah Transfer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dari", style = MaterialTheme.typography.labelLarge)
                WalletPicker(wallets, src, { src = it })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Saldo $srcName: ${Fmt.money(balances[src] ?: 0L, cur)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { val t = src; src = dest; dest = t }) { Icon(Icons.Rounded.SwapVert, "Tukar asal dan tujuan") }
                }
                Text("Ke", style = MaterialTheme.typography.labelLarge)
                WalletPicker(wallets, dest, { dest = it })
                OutlinedTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Jumlah") }, prefix = { Text(Fmt.symbol(cur) + " ") }, singleLine = true,
                    visualTransformation = ThousandsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (value > available) {
                    Text(
                        "Jumlah melebihi saldo $srcName; saldonya akan menjadi negatif.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
                Text("Tanggal", style = MaterialTheme.typography.labelLarge)
                DateField(date) { date = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(60) }, label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Contoh: Top up GoPay") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Transfer tidak dihitung sebagai pemasukan atau pengeluaran. Biaya admin (bila ada) catat sebagai pengeluaran terpisah.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Hapus transfer ini", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    src == dest -> Toast.makeText(ctx, "Dompet asal dan tujuan harus berbeda", Toast.LENGTH_SHORT).show()
                    value <= 0L -> Toast.makeText(ctx, "Nominal harus lebih dari 0", Toast.LENGTH_SHORT).show()
                    else -> onSave(
                        TransferEntity(
                            id = initial?.id ?: 0L, fromWalletId = src, toWalletId = dest, amount = value,
                            dateMillis = date, note = note.trim(), createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
