package com.myfinance.app.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myfinance.app.util.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Mengatur alur pembaruan aplikasi lewat GitHub Releases: cek rilis, unduh, verifikasi, lalu pasang. */
class UpdateController(context: Context, private val scope: CoroutineScope) {
    private val ctx: Context = context.applicationContext
    private val prefs = ctx.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private var cancel = AtomicBoolean(false)

    var checking by mutableStateOf(false); private set
    var available by mutableStateOf<Updater.Release?>(null); private set
    var downloading by mutableStateOf(false); private set
    var done by mutableLongStateOf(0L); private set
    var total by mutableLongStateOf(0L); private set
    var info by mutableStateOf<Pair<String, String>?>(null); private set
    var needInstallPermission by mutableStateOf(false); private set
    var auto by mutableStateOf(prefs.getBoolean("auto", true)); private set

    val enabled: Boolean get() = Updater.enabled()

    @Suppress("DEPRECATION")
    val build: Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (e: Exception) { 0L }

    val versionName: String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    private fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    fun updateAutoCheck(v: Boolean) { auto = v; prefs.edit().putBoolean("auto", v).apply() }

    fun metered(): Boolean = runCatching { ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(false)

    /** Dipanggil saat aplikasi dibuka: bersihkan sisa unduhan, lalu cek rilis baru (maksimal sekali sehari bila diaktifkan). */
    fun onAppStart() {
        scope.launch(Dispatchers.IO) { runCatching { Updater.cleanup(ctx, build) } }
        if (!auto || !enabled) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) < 24L * 3600_000L) return
        check(false)
    }

    /** manual = true: selalu beri kabar hasilnya (termasuk "sudah terbaru") dan abaikan "Lewati versi ini". */
    fun check(manual: Boolean) {
        if (!enabled) {
            if (manual) toast("Pembaruan tidak tersedia di build ini (alamat repositori GitHub tidak diketahui)")
            return
        }
        if (checking || downloading) { if (manual) toast("Sedang memeriksa…"); return }
        checking = true
        if (manual) toast("Memeriksa pembaruan…")
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Updater.latest() } }
            checking = false
            val err = res.exceptionOrNull()
            val r = res.getOrNull()
            if (err == null) prefs.edit().putLong("last", System.currentTimeMillis()).apply()
            when {
                err != null -> if (manual) toast("Gagal memeriksa pembaruan: " + (err.message ?: "tidak diketahui").take(80))
                r == null || r.build <= build -> if (manual) toast("MyFinance sudah versi terbaru")
                !manual && r.tag == prefs.getString("skip", "") -> {}
                else -> available = r
            }
        }
    }

    fun dismissAvailable() { available = null }
    fun skip(r: Updater.Release) { prefs.edit().putString("skip", r.tag).apply(); available = null }
    fun dismissInfo() { info = null }
    fun dismissPermission() { needInstallPermission = false }
    fun cancelDownload() { cancel.set(true) }

    fun download(r: Updater.Release) {
        available = null
        downloading = true; done = 0L; total = r.size
        cancel = AtomicBoolean(false)
        val c = cancel
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { Updater.download(ctx, r, c) { d, t -> done = d; total = t } }
            }
            downloading = false
            val err = res.exceptionOrNull()
            val f = res.getOrNull()
            when {
                c.get() -> toast("Unduhan dibatalkan")
                err != null -> toast("Gagal mengunduh: " + (err.message ?: "tidak diketahui").take(80))
                f != null -> install(f)
            }
        }
    }

    private fun install(f: File) {
        if (Updater.sameSigner(ctx, f) == false) {
            f.delete()
            info = "Pembaruan dibatalkan" to "Tanda tangan APK ini tidak sama dengan aplikasi yang terpasang, jadi tidak dipasang demi keamanan. " +
                "Ini biasanya terjadi jika aplikasi yang terpasang berasal dari build lama dengan kunci berbeda. " +
                "Berkas unduhan sudah dihapus."
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            needInstallPermission = true
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) { toast("Tidak bisa memulai pemasangan") }
    }

    fun openInstallSettings() {
        needInstallPermission = false
        try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { toast("Pengaturan tidak tersedia") }
    }
}

@Composable
fun UpdateDialogs(u: UpdateController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    u.available?.let { r ->
        val notes = r.notes.trim().take(600)
        AlertDialog(
            onDismissRequest = { u.dismissAvailable() },
            title = { Text("Pembaruan tersedia: " + r.tag.removePrefix("v")) },
            text = {
                Text(
                    "Versi terpasang: ${u.versionName}" +
                        (if (r.size > 0) "\nUkuran ±" + Formatter.formatShortFileSize(ctx, r.size) else "") +
                        (if (notes.isNotEmpty()) "\n\n$notes" else "") +
                        (if (u.metered()) "\n\nKamu sedang memakai data seluler. Disarankan memakai Wi-Fi." else "")
                )
            },
            confirmButton = { TextButton(onClick = { u.download(r) }) { Text("Unduh & pasang") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { u.skip(r) }) { Text("Lewati") }
                    TextButton(onClick = { u.dismissAvailable() }) { Text("Nanti") }
                }
            }
        )
    }

    if (u.downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengunduh pembaruan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (u.total > 0) LinearProgressIndicator(progress = { (u.done.toFloat() / u.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        Formatter.formatShortFileSize(ctx, u.done) + (if (u.total > 0) " / " + Formatter.formatShortFileSize(ctx, u.total) else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { u.cancelDownload() }) { Text("Batal") } }
        )
    }

    u.info?.let { (title, msg) ->
        AlertDialog(
            onDismissRequest = { u.dismissInfo() },
            title = { Text(title) }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { u.dismissInfo() }) { Text("OK") } }
        )
    }

    if (u.needInstallPermission) {
        AlertDialog(
            onDismissRequest = { u.dismissPermission() },
            title = { Text("Izinkan pemasangan") },
            text = {
                Text(
                    "Android perlu izin \"Pasang aplikasi tidak dikenal\" untuk MyFinance supaya pembaruan bisa dipasang. " +
                        "Setelah mengizinkan, kembali ke MyFinance dan periksa pembaruan lagi; berkas yang sudah diunduh tidak diunduh ulang."
                )
            },
            confirmButton = { TextButton(onClick = { u.openInstallSettings() }) { Text("Buka pengaturan") } },
            dismissButton = { TextButton(onClick = { u.dismissPermission() }) { Text("Batal") } }
        )
    }
}
