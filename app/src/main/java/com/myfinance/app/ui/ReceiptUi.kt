package com.myfinance.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.myfinance.app.util.ReceiptImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decode Base64 -> gambar di thread latar belakang. Null selama memuat / bila data rusak. */
@Composable
fun rememberReceiptBitmap(b64: String?): ImageBitmap? {
    var bmp by remember(b64) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(b64) {
        bmp = if (b64 == null) null
        else withContext(Dispatchers.Default) { ReceiptImage.decode(b64)?.asImageBitmap() }
    }
    return bmp
}

/** Memuat struk (Base64) milik satu transaksi. [hasReceipt] dari AppViewModel.receiptIds agar tidak query percuma. */
@Composable
fun rememberReceipt(vm: AppViewModel, txId: Long?, hasReceipt: Boolean): String? {
    val value by produceState<String?>(null, txId, hasReceipt) {
        value = if (txId != null && hasReceipt) vm.getReceipt(txId) else null
    }
    return value
}

@Composable
fun ReceiptThumb(b64: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bmp = rememberReceiptBitmap(b64)
    Box(
        modifier.fillMaxWidth().heightIn(min = 96.dp, max = 200.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, "Foto struk", Modifier.fillMaxWidth().heightIn(max = 200.dp), contentScale = ContentScale.Fit)
        else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** Tampilan layar penuh; cubit untuk memperbesar, ketuk X untuk menutup. */
@Composable
fun ReceiptViewerDialog(b64: String, onDismiss: () -> Unit) {
    val bmp = rememberReceiptBitmap(b64)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f))) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            if (bmp != null) {
                Image(
                    bmp, "Foto struk", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                )
            } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            IconButton(onClick = onDismiss, Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Rounded.Close, "Tutup", tint = Color.White)
            }
        }
    }
}

/** Bagian "Foto Struk" di form tambah/ubah transaksi: ambil dari kamera atau galeri, pratinjau, hapus. */
@Composable
fun ReceiptPickerSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val draft = vm.receiptDraft
    var viewer by remember { mutableStateOf(false) }
    // Uri sementara untuk kamera; Parcelable, jadi selamat dari recreate Activity saat aplikasi kamera terbuka
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun attach(uri: Uri) = vm.attachReceipt(uri) { ok ->
        if (!ok) Toast.makeText(ctx, "Foto tidak bisa dibaca", Toast.LENGTH_SHORT).show()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) attach(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) attach(u)
    }

    fun openCamera() {
        try {
            val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // buang sisa foto kamera sebelumnya
            val file = File(dir, "struk_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
            cameraUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Kamera tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    // Satu Column agar jarak antar-elemen di dalam bagian ini rapat (form induk memakai jarak 16dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Foto Struk (Opsional)", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { openCamera() }, enabled = !vm.receiptBusy, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Kamera")
            }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !vm.receiptBusy, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Galeri")
            }
        }
        if (vm.receiptBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Memproses foto…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (draft != null) {
            ReceiptThumb(draft) { viewer = true }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.removeReceiptDraft() }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Hapus foto", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (viewer && draft != null) ReceiptViewerDialog(draft) { viewer = false }
}
