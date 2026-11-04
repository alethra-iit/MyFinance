@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myfinance.app.data.UserSettingsEntity
import com.myfinance.app.ui.*
import com.myfinance.app.util.AppLock
import com.myfinance.app.util.Fmt
import com.myfinance.app.util.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ProfileScreen(vm: AppViewModel, settings: UserSettingsEntity, upd: UpdateController, lock: AppLock, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var reminderLabel by remember { mutableStateOf(Reminder.label(ctx)) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var exportFormat by remember { mutableStateOf("XLSX") }
    var exportRange by remember { mutableStateOf("MONTH") }
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    val bioStatus = remember(lock.enabled, pinMode) { AppLock.biometricStatus(ctx) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun writeTo(uri: Uri, ok: String, producer: suspend () -> ByteArray) {
        scope.launch {
            val success = runCatching {
                val bytes = producer()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            }.isSuccess
            toast(if (success) ok else "Gagal menyimpan berkas")
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeTo(uri, "Backup berhasil disimpan") { vm.buildBackup().toByteArray() }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) writeTo(uri, "Laporan Excel disimpan") { vm.buildExport("XLSX", exportRange) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) writeTo(uri, "Laporan PDF disimpan") { vm.buildExport("PDF", exportRange) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.updateSettings { it.copy(notifications = true) }; Reminder.schedule(ctx)
            toast("Pengingat harian aktif (pukul ${Reminder.label(ctx)})")
        } else toast("Izin notifikasi ditolak")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Informasi pengguna
        SoftCard(Modifier.fillMaxWidth(), onClick = { dialog = "name" }, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ketuk untuk ubah nama", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Edit, null)
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Rounded.Palette, "Tema", themeLabel(settings.theme)) { dialog = "theme" }
            SettingRow(Icons.Rounded.AttachMoney, "Mata Uang", currencyLabel(settings.currency)) { dialog = "currency" }
            SettingRow(Icons.Rounded.Notifications, "Notifikasi", "Pengingat harian $reminderLabel · ketuk untuk ubah jam", trailing = {
                Switch(checked = settings.notifications, onCheckedChange = { on ->
                    if (!on) {
                        vm.updateSettings { it.copy(notifications = false) }; Reminder.cancel(ctx)
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.updateSettings { it.copy(notifications = true) }; Reminder.schedule(ctx)
                        toast("Pengingat harian aktif (pukul ${Reminder.label(ctx)})")
                    }
                })
            }) {
                Reminder.pickTime(ctx, settings.notifications) { reminderLabel = Reminder.label(ctx) }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            SettingRow(
                Icons.Rounded.Lock, "Kunci Aplikasi",
                if (lock.enabled) "Aktif · PIN" + (if (lock.biometric) " + sidik jari" else "") else "Lindungi data keuangan dengan PIN",
                trailing = {
                    Switch(checked = lock.enabled, onCheckedChange = { on -> pinMode = if (on) PinMode.SET else PinMode.DISABLE })
                }
            ) { pinMode = if (lock.enabled) PinMode.DISABLE else PinMode.SET }
            if (lock.enabled) {
                SettingRow(Icons.Rounded.Password, "Ubah PIN") { pinMode = PinMode.CHANGE }
                SettingRow(
                    Icons.Rounded.Fingerprint, "Buka dengan sidik jari",
                    if (bioStatus == BiometricManager.BIOMETRIC_SUCCESS) "PIN tetap bisa dipakai sebagai cadangan"
                    else "Tidak tersedia: belum ada sidik jari terdaftar di perangkat",
                    trailing = {
                        Switch(
                            checked = lock.biometric, enabled = bioStatus == BiometricManager.BIOMETRIC_SUCCESS || lock.biometric,
                            onCheckedChange = { lock.setBiometric(it) }
                        )
                    }
                ) {
                    if (bioStatus == BiometricManager.BIOMETRIC_SUCCESS) lock.setBiometric(!lock.biometric)
                    else toast("Daftarkan sidik jari di Pengaturan perangkat terlebih dahulu")
                }
                SettingRow(Icons.Rounded.Timer, "Kunci otomatis", lockTimeoutLabel(lock.timeoutSec)) { dialog = "locktimeout" }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Rounded.Category, "Kelola Kategori", "Tambah, ubah, hapus; pilih ikon dan warna") { nav.navigate("categories") }
            SettingRow(Icons.Rounded.Savings, "Anggaran Bulanan", "Batas pengeluaran per kategori + peringatan") { nav.navigate("budget") }
            SettingRow(Icons.Rounded.SwapHoriz, "Utang & Piutang", "Catat pinjaman, jatuh tempo, dan pengingat") { nav.navigate("debts") }
            SettingRow(Icons.Rounded.Autorenew, "Transaksi Berulang", "Gaji, cicilan, langganan, tagihan otomatis") { nav.navigate("recurring") }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Rounded.CloudUpload, "Backup Data", "Simpan semua data ke berkas") {
                backupLauncher.launch("myfinance-backup-${Fmt.fileStamp(System.currentTimeMillis())}.json")
            }
            SettingRow(Icons.Rounded.Restore, "Pulihkan Data", "Impor dari berkas backup") {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
            SettingRow(Icons.Rounded.FileDownload, "Ekspor Laporan", "Excel (.xlsx) atau PDF") { dialog = "export" }
            SettingRow(Icons.Rounded.Info, "Tentang Aplikasi", "MyFinance ${upd.versionName} · cek pembaruan") { dialog = "about" }
        }

        Button(
            onClick = { dialog = "reset" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Hapus Semua Data", fontWeight = FontWeight.Bold)
        }
    }

    // ---------- Dialog ----------
    when (dialog) {
        "name" -> TextInputDialog("Nama Pengguna", "Nama", settings.userName, { dialog = null }) { n ->
            dialog = null; vm.updateSettings { it.copy(userName = n) }
        }
        "theme" -> ChoiceDialog(
            "Pilih Tema", listOf("LIGHT" to "Terang", "DARK" to "Gelap", "SYSTEM" to "Ikuti Sistem"), settings.theme,
            { dialog = null }
        ) { v -> dialog = null; vm.updateSettings { it.copy(theme = v) } }
        "currency" -> ChoiceDialog(
            "Pilih Mata Uang", listOf("IDR" to "Rupiah (Rp)", "USD" to "Dolar AS (\$)"), settings.currency,
            { dialog = null }
        ) { v -> dialog = null; vm.updateSettings { it.copy(currency = v) } }
        "export" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Ekspor Laporan") },
            text = {
                Column {
                    Text("Format", style = MaterialTheme.typography.labelLarge)
                    RadioLine("Excel (.xlsx)", exportFormat == "XLSX") { exportFormat = "XLSX" }
                    RadioLine("PDF", exportFormat == "PDF") { exportFormat = "PDF" }
                    Spacer(Modifier.height(8.dp))
                    Text("Periode", style = MaterialTheme.typography.labelLarge)
                    RadioLine("Bulan ini", exportRange == "MONTH") { exportRange = "MONTH" }
                    RadioLine("Tahun ini", exportRange == "YEAR") { exportRange = "YEAR" }
                    RadioLine("Semua data", exportRange == "ALL") { exportRange = "ALL" }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    val stamp = Fmt.fileStamp(System.currentTimeMillis())
                    if (exportFormat == "PDF") pdfLauncher.launch("laporan-myfinance-$stamp.pdf")
                    else xlsxLauncher.launch("laporan-myfinance-$stamp.xlsx")
                }) { Text("Ekspor") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } }
        )
        "locktimeout" -> ChoiceDialog(
            "Kunci otomatis", listOf("0" to "Segera", "30" to "Setelah 30 detik", "60" to "Setelah 1 menit", "300" to "Setelah 5 menit"),
            lock.timeoutSec.toString(), { dialog = null }
        ) { v -> dialog = null; lock.setTimeout(v.toInt()) }
        "about" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("MyFinance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Versi ${upd.versionName} (build ${upd.build})", fontWeight = FontWeight.SemiBold)
                    Text("Aplikasi pencatat keuangan pribadi. Seluruh data tersimpan di perangkat Anda dan dapat digunakan tanpa internet.")
                    HorizontalDivider()
                    if (!upd.enabled) {
                        Text("Pembaruan otomatis tidak tersedia di build ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(
                            onClick = { upd.check(true) }, enabled = !upd.checking && !upd.downloading,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        ) {
                            if (upd.checking) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp)); Text("Memeriksa…")
                            } else {
                                Icon(Icons.Rounded.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("Periksa Pembaruan")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Periksa otomatis setiap hari", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = upd.auto, onCheckedChange = { upd.updateAutoCheck(it) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Tutup") } }
        )
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Hapus semua data?") },
            text = { Text("Seluruh transaksi dan catatan belanja akan dihapus permanen. Pengaturan, anggaran, transaksi berulang, serta utang/piutang tetap disimpan. Sebaiknya lakukan backup terlebih dahulu.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.clearAll(); toast("Semua data dihapus") }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } }
        )
    }

    pinMode?.let { m ->
        PinFlowDialog(m, lock, onDismiss = { pinMode = null }) { msg ->
            pinMode = null; toast(msg)
            // Setelah kunci pertama kali aktif, tawarkan sidik jari otomatis bila tersedia
            if (m == PinMode.SET && AppLock.biometricAvailable(ctx)) lock.setBiometric(true)
        }
    }

    val restoreUri = pendingRestore
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Pulihkan data?") },
            text = { Text("Data saat ini akan diganti dengan isi berkas backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val json = runCatching {
                            withContext(Dispatchers.IO) {
                                ctx.contentResolver.openInputStream(restoreUri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                            }
                        }.getOrNull()
                        val ok = json != null && vm.restore(json)
                        toast(if (ok) "Data berhasil dipulihkan" else "Berkas backup tidak valid")
                    }
                }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Batal") } }
        )
    }
}

private fun lockTimeoutLabel(s: Int) = when (s) { 0 -> "Segera"; 60 -> "Setelah 1 menit"; 300 -> "Setelah 5 menit"; else -> "Setelah $s detik" }
private fun currencyLabel(c: String) = when (c) { "USD" -> "Dolar AS"; else -> "Rupiah" }
private fun themeLabel(t: String) = when (t) { "DARK" -> "Gelap"; "SYSTEM" -> "Ikuti Sistem"; else -> "Terang" }

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, value: String? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ChoiceDialog(
    title: String, options: List<Pair<String, String>>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // Bisa di-scroll: daftar mata uang lebih panjang dari tinggi dialog
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { options.forEach { (k, label) -> RadioLine(label, k == selected) { onSelect(k) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
