package com.myfinance.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.myfinance.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pembaruan lewat GitHub Releases. Workflow membuat rilis bertag "v1.0.N" (N = nomor run = versionCode) dan melampirkan
 * MyFinance.apk, jadi pembaruan = ada rilis dengan N lebih besar dari versionCode yang terpasang.
 *
 * Keamanan: APK hanya diunduh lewat HTTPS dari repositori yang ditanam saat build, diperiksa ukuran dan SHA-256
 * (bila GitHub menyediakannya), dan tanda tangannya harus sama dengan aplikasi terpasang sebelum pemasangan dimulai.
 */
object Updater {
    class Release(val tag: String, val build: Long, val notes: String, val url: String, val size: Long, val sha256: String?)

    fun repo(): String = BuildConfig.UPDATE_REPO.trim()
    fun enabled(): Boolean = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo())

    private fun dir(ctx: Context) = File(ctx.cacheDir, "update")
    private fun apkFile(ctx: Context, build: Long) = File(dir(ctx), "MyFinance-$build.apk")

    /** Rilis terbaru yang punya APK; null bila belum ada rilis. Melempar Exception bila jaringan atau GitHub bermasalah. */
    fun latest(): Release? {
        val c = URL("https://api.github.com/repos/" + repo() + "/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "MyFinance")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code == 403 || code == 429) throw RuntimeException("Batas permintaan GitHub tercapai, coba lagi nanti")
            if (code != 200) throw RuntimeException("GitHub menjawab $code")
            val js = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = js.optString("tag_name")
            val build = Regex("(\\d+)$").find(tag)?.value?.toLongOrNull() ?: return null
            val assets = js.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val url = a.optString("browser_download_url")
                if (!a.optString("name").endsWith(".apk", true) || !url.startsWith("https://")) continue
                val dg = a.optString("digest")
                val sha = if (dg.startsWith("sha256:", true)) dg.substring(7).lowercase() else null
                return Release(tag, build, js.optString("body"), url, a.optLong("size", 0L), sha)
            }
            return null
        } finally { c.disconnect() }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun intact(f: File, r: Release): Boolean =
        f.isFile && (r.size <= 0 || f.length() == r.size) && (r.sha256 == null || sha256(f) == r.sha256)

    /**
     * Unduh APK rilis [r] ke cache aplikasi (jalankan di thread latar). Bila berkasnya sudah ada dan utuh, langsung dipakai.
     * [onProgress] dipanggil sekitar 4 kali per detik dengan (terunduh, total). Mengembalikan null bila [cancel] diset.
     */
    fun download(ctx: Context, r: Release, cancel: AtomicBoolean, onProgress: (Long, Long) -> Unit): File? {
        val d = dir(ctx)
        d.mkdirs()
        val out = apkFile(ctx, r.build)
        if (intact(out, r)) return out
        out.delete()
        d.listFiles()?.forEach { if (it != out) it.delete() }   // sisa unduhan versi lain
        val part = File(d, out.name + ".part")
        part.delete()
        val c = URL(r.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "MyFinance")
        try {
            if (c.responseCode != 200) throw RuntimeException("Server menjawab " + c.responseCode)
            val total = if (r.size > 0) r.size else c.contentLengthLong
            var got = 0L
            var last = 0L
            FileOutputStream(part).use { o ->
                c.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancel.get()) { part.delete(); return null }
                        val n = ins.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        got += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) { last = now; onProgress(got, total) }
                    }
                }
            }
            onProgress(got, total)
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally { c.disconnect() }
        if (!part.renameTo(out)) { part.delete(); throw RuntimeException("Gagal menyimpan berkas") }
        if (!intact(out, r)) { out.delete(); throw RuntimeException("Berkas unduhan rusak (ukuran atau checksum tidak cocok)") }
        return out
    }

    private fun certHashes(sigs: Array<android.content.pm.Signature>?): Set<String> =
        sigs?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }?.toSet() ?: emptySet()

    /** true = APK ditandatangani kunci yang sama dengan aplikasi terpasang, false = beda, null = tidak bisa dipastikan. */
    @Suppress("DEPRECATION")
    fun sameSigner(ctx: Context, apk: File): Boolean? {
        return try {
            val pm = ctx.packageManager
            val mine: Set<String>
            val theirs: Set<String>
            if (Build.VERSION.SDK_INT >= 28) {
                val f = PackageManager.GET_SIGNING_CERTIFICATES
                mine = certHashes(pm.getPackageInfo(ctx.packageName, f).signingInfo?.apkContentsSigners)
                theirs = certHashes(pm.getPackageArchiveInfo(apk.path, f)?.signingInfo?.apkContentsSigners)
            } else {
                val f = PackageManager.GET_SIGNATURES
                mine = certHashes(pm.getPackageInfo(ctx.packageName, f).signatures)
                theirs = certHashes(pm.getPackageArchiveInfo(apk.path, f)?.signatures)
            }
            if (mine.isEmpty() || theirs.isEmpty()) null else mine == theirs
        } catch (e: Exception) { null }
    }

    /** Hapus APK sisa pembaruan yang sudah terpasang atau kedaluwarsa (build <= [installed]). */
    fun cleanup(ctx: Context, installed: Long) {
        dir(ctx).listFiles()?.forEach { f ->
            val b = Regex("MyFinance-(\\d+)\\.apk").find(f.name)?.groupValues?.get(1)?.toLongOrNull()
            if (b == null || b <= installed) f.delete()
        }
    }
}
