package com.myfinance.app.util

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Kunci aplikasi (PIN 6 angka + opsional sidik jari).
 * - PIN tidak pernah disimpan polos: hanya hash PBKDF2 + salt acak.
 * - Percobaan salah dibatasi: mulai dari percobaan ke-5 ada jeda yang makin lama (30 dtk … 15 mnt).
 * - Satu instance per proses (lihat MyFinanceApp.lock), jadi memutar layar tidak mengunci ulang,
 *   sedangkan aplikasi yang dimatikan sistem akan selalu terkunci saat dibuka lagi.
 */
class AppLock(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs.getString(K_HASH, null) != null); private set
    var biometric by mutableStateOf(prefs.getBoolean(K_BIO, false)); private set
    /** Detik di latar belakang sebelum aplikasi terkunci lagi (0 = segera). */
    var timeoutSec by mutableIntStateOf(prefs.getInt(K_TIMEOUT, 30)); private set
    var locked by mutableStateOf(enabled); private set
    /** Naik setiap aplikasi dibuka lagi dalam keadaan terkunci; dipakai untuk memunculkan prompt sidik jari. */
    var epoch by mutableIntStateOf(0); private set
    var biometricShowing = false

    private var stoppedAt = 0L

    // ---------- Siklus hidup (dipanggil dari MainActivity) ----------

    fun onActivityStop() {
        if (enabled && !locked) stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onActivityStart() {
        biometricShowing = false
        if (!enabled) return
        if (!locked && SystemClock.elapsedRealtime() - stoppedAt >= timeoutSec * 1000L) locked = true
        if (locked) epoch++
    }

    // ---------- PIN ----------

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val h = withContext(Dispatchers.Default) { hash(pin, salt) }
        prefs.edit()
            .putString(K_SALT, b64(salt)).putString(K_HASH, b64(h))
            .putInt(K_FAILS, 0).putLong(K_UNTIL, 0L)
            .apply()
        enabled = true
    }

    /** Memeriksa PIN. Mengembalikan false juga bila sedang dalam masa jeda. */
    suspend fun verify(pin: String): Boolean {
        if (remainingLockMs() > 0) return false
        val salt = prefs.getString(K_SALT, null)?.let(::unb64) ?: return false
        val stored = prefs.getString(K_HASH, null)?.let(::unb64) ?: return false
        val ok = withContext(Dispatchers.Default) { MessageDigest.isEqual(hash(pin, salt), stored) }
        if (ok) {
            prefs.edit().putInt(K_FAILS, 0).putLong(K_UNTIL, 0L).apply()
        } else {
            val fails = prefs.getInt(K_FAILS, 0) + 1
            val delayMs = if (fails >= 5) (30_000L shl minOf(fails - 5, 5)).coerceAtMost(900_000L) else 0L
            prefs.edit().putInt(K_FAILS, fails).putLong(K_UNTIL, if (delayMs > 0) System.currentTimeMillis() + delayMs else 0L).apply()
        }
        return ok
    }

    fun remainingLockMs(): Long = (prefs.getLong(K_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun failedAttempts(): Int = prefs.getInt(K_FAILS, 0)

    fun unlock() { locked = false }

    /** Mematikan kunci sepenuhnya (PIN harus sudah diverifikasi oleh pemanggil). */
    fun disable() {
        prefs.edit().clear().apply()
        enabled = false; biometric = false; locked = false; timeoutSec = 30
    }

    // ---------- Pengaturan ----------

    fun setBiometric(on: Boolean) {
        biometric = on && enabled
        prefs.edit().putBoolean(K_BIO, biometric).apply()
    }

    fun setTimeout(sec: Int) {
        timeoutSec = sec
        prefs.edit().putInt(K_TIMEOUT, sec).apply()
    }

    // ---------- Util ----------

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val PIN_LENGTH = 6
        private const val ITERATIONS = 60_000
        private const val K_HASH = "hash"
        private const val K_SALT = "salt"
        private const val K_BIO = "bio"
        private const val K_TIMEOUT = "timeout"
        private const val K_FAILS = "fails"
        private const val K_UNTIL = "until"

        /** BiometricManager.BIOMETRIC_SUCCESS bila sidik jari/wajah kelas kuat tersedia dan sudah terdaftar. */
        fun biometricStatus(ctx: Context): Int =
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        fun biometricAvailable(ctx: Context) = biometricStatus(ctx) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
