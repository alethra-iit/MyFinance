package com.myfinance.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.myfinance.app.util.AppLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun AppLock.promptBiometric(activity: FragmentActivity) {
    if (!enabled || !biometric || biometricShowing) return
    if (AppLock.biometricStatus(activity) != BiometricManager.BIOMETRIC_SUCCESS) return
    biometricShowing = true
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricShowing = false; unlock()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { biometricShowing = false }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Buka MyFinance")
        .setSubtitle("Pindai sidik jari Anda")
        .setNegativeButtonText("Gunakan PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info)
}

private fun lockoutText(ms: Long): String {
    val s = (ms + 999) / 1000
    return "Terlalu banyak percobaan. Coba lagi dalam " + if (s >= 60) "${s / 60} mnt ${s % 60} dtk" else "$s dtk"
}

// ---------------------------------------------------------------- Komponen PIN

@Composable
fun PinDots(filled: Int, total: Int = AppLock.PIN_LENGTH) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(
                    if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
private fun KeyButton(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.25f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun PinKeypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit, leftSlot: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { d ->
                    KeyButton(enabled, { onDigit(d) }) { Text(d.toString(), style = MaterialTheme.typography.headlineMedium) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { leftSlot() }
            KeyButton(enabled, { onDigit('0') }) { Text("0", style = MaterialTheme.typography.headlineMedium) }
            Box(Modifier.size(76.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onBackspace), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, "Hapus")
            }
        }
    }
}

// ---------------------------------------------------------------- Layar kunci

@Composable
fun LockScreen(lock: AppLock) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockMs by remember { mutableLongStateOf(lock.remainingLockMs()) }
    var busy by remember { mutableStateOf(false) }
    val bioReady = lock.biometric && AppLock.biometricAvailable(ctx)

    BackHandler(true) { activity?.moveTaskToBack(true) }

    // Prompt sidik jari otomatis setiap aplikasi dibuka dalam keadaan terkunci
    LaunchedEffect(lock.epoch) { if (lock.epoch > 0 && activity != null) lock.promptBiometric(activity) }

    LaunchedEffect(lockMs > 0) {
        while (lockMs > 0) { delay(250); lockMs = lock.remainingLockMs() }
        if (error?.startsWith("Terlalu") == true) error = null
    }

    fun submit(p: String) {
        busy = true
        scope.launch {
            val ok = lock.verify(p)
            busy = false
            if (ok) { lock.unlock(); return@launch }
            pin = ""
            lockMs = lock.remainingLockMs()
            error = if (lockMs > 0) lockoutText(lockMs) else "PIN salah"
        }
    }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("MyFinance terkunci", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Masukkan PIN untuk melanjutkan", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.height(40.dp)
            )
            PinKeypad(
                enabled = !busy && lockMs == 0L,
                onDigit = { d ->
                    if (pin.length < AppLock.PIN_LENGTH) {
                        error = null
                        pin += d
                        if (pin.length == AppLock.PIN_LENGTH) submit(pin)
                    }
                },
                onBackspace = { pin = pin.dropLast(1) },
                leftSlot = {
                    if (bioReady && activity != null) {
                        IconButton(onClick = { lock.promptBiometric(activity) }) {
                            Icon(Icons.Rounded.Fingerprint, "Sidik jari", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------- Alur atur / ubah / matikan PIN

enum class PinMode { SET, CHANGE, DISABLE }
private enum class PinStep { VERIFY, NEW, CONFIRM }

/** Dialog layar penuh. onDone dipanggil dengan pesan sukses; onDismiss bila dibatalkan. */
@Composable
fun PinFlowDialog(mode: PinMode, lock: AppLock, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (mode == PinMode.SET) PinStep.NEW else PinStep.VERIFY) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun handle(p: String) {
        busy = true
        scope.launch {
            when (step) {
                PinStep.VERIFY -> {
                    val ok = lock.verify(p)
                    pin = ""
                    if (!ok) {
                        val ms = lock.remainingLockMs()
                        error = if (ms > 0) lockoutText(ms) else "PIN salah"
                    } else if (mode == PinMode.DISABLE) {
                        lock.disable(); onDone("Kunci aplikasi dimatikan")
                    } else step = PinStep.NEW
                }
                PinStep.NEW -> { first = p; pin = ""; step = PinStep.CONFIRM }
                PinStep.CONFIRM -> {
                    if (p == first) {
                        lock.setPin(p)
                        onDone(if (mode == PinMode.SET) "Kunci aplikasi aktif" else "PIN berhasil diubah")
                    } else {
                        pin = ""; first = ""; step = PinStep.NEW; error = "PIN tidak sama, coba lagi"
                    }
                }
            }
            busy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Batal") }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    when (step) {
                        PinStep.VERIFY -> "Masukkan PIN saat ini"
                        PinStep.NEW -> "Buat PIN baru"
                        PinStep.CONFIRM -> "Ulangi PIN baru"
                    },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (step == PinStep.NEW) "${AppLock.PIN_LENGTH} angka. Jika lupa, PIN tidak bisa dipulihkan." else " ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                PinDots(pin.length)
                Spacer(Modifier.height(12.dp))
                Text(
                    error ?: " ", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.height(40.dp)
                )
                PinKeypad(
                    enabled = !busy,
                    onDigit = { d ->
                        if (pin.length < AppLock.PIN_LENGTH) {
                            error = null
                            pin += d
                            if (pin.length == AppLock.PIN_LENGTH) handle(pin)
                        }
                    },
                    onBackspace = { pin = pin.dropLast(1) }
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
