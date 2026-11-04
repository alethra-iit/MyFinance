@file:OptIn(ExperimentalMaterial3Api::class)

package com.myfinance.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfinance.app.data.CategoryEntity
import com.myfinance.app.data.TYPE_EXPENSE
import com.myfinance.app.data.TYPE_INCOME
import com.myfinance.app.data.TransactionEntity
import com.myfinance.app.ui.theme.extra
import com.myfinance.app.util.Dates
import com.myfinance.app.util.Fmt

/** Gaya kategori buatan pengguna (ikon & warna), diisi dari AppViewModel. Pembacaan memicu recompose saat berubah. */
object CategoryStyles {
    private var all by mutableStateOf<List<CategoryEntity>>(emptyList())
    fun update(list: List<CategoryEntity>) { all = list }
    fun find(name: String, type: String?): CategoryEntity? {
        val l = all
        return if (type != null) l.firstOrNull { it.name == name && it.type == type }
        else l.firstOrNull { it.name == name && it.type == TYPE_EXPENSE } ?: l.firstOrNull { it.name == name }
    }
}

object CategoryIcons {
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "wallet" to Icons.Rounded.AccountBalanceWallet, "work" to Icons.Rounded.Work, "gift" to Icons.Rounded.CardGiftcard,
            "store" to Icons.Rounded.Storefront, "savings" to Icons.Rounded.Savings, "restaurant" to Icons.Rounded.Restaurant,
            "bus" to Icons.Rounded.DirectionsBus, "bag" to Icons.Rounded.ShoppingBag, "receipt" to Icons.Rounded.Receipt,
            "medical" to Icons.Rounded.MedicalServices, "school" to Icons.Rounded.School, "movie" to Icons.Rounded.Movie,
            "cafe" to Icons.Rounded.LocalCafe, "fastfood" to Icons.Rounded.Fastfood, "grocery" to Icons.Rounded.LocalGroceryStore,
            "car" to Icons.Rounded.DirectionsCar, "fuel" to Icons.Rounded.LocalGasStation, "flight" to Icons.Rounded.Flight,
            "home" to Icons.Rounded.Home, "bolt" to Icons.Rounded.Bolt, "wifi" to Icons.Rounded.Wifi,
            "phone" to Icons.Rounded.PhoneAndroid, "computer" to Icons.Rounded.Computer, "fitness" to Icons.Rounded.FitnessCenter,
            "pets" to Icons.Rounded.Pets, "child" to Icons.Rounded.ChildCare, "games" to Icons.Rounded.SportsEsports,
            "music" to Icons.Rounded.MusicNote, "clothes" to Icons.Rounded.Checkroom, "spa" to Icons.Rounded.Spa,
            "build" to Icons.Rounded.Build, "payments" to Icons.Rounded.Payments, "trend" to Icons.Rounded.TrendingUp,
            "cake" to Icons.Rounded.Cake, "book" to Icons.Rounded.Book, "laundry" to Icons.Rounded.LocalLaundryService,
            "park" to Icons.Rounded.Park, "beach" to Icons.Rounded.BeachAccess, "favorite" to Icons.Rounded.Favorite,
            "star" to Icons.Rounded.Star, "subscriptions" to Icons.Rounded.Subscriptions, "more" to Icons.Rounded.MoreHoriz
        )
    }
    private val map by lazy { all.toMap() }
    fun get(key: String): ImageVector = map[key] ?: Icons.Rounded.MoreHoriz
}

val CategoryPalette = listOf(
    Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF0EA5E9), Color(0xFF3B82F6),
    Color(0xFF8B5CF6), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFF43F5E), Color(0xFF8B7CF6)
)

private fun defaultIconKey(name: String) = when (name) {
    "Gaji" -> "wallet"; "Bonus" -> "work"; "Hadiah" -> "gift"; "Bisnis" -> "store"; "Investasi" -> "savings"
    "Makanan" -> "restaurant"; "Transportasi" -> "bus"; "Belanja" -> "bag"; "Tagihan" -> "receipt"
    "Kesehatan" -> "medical"; "Pendidikan" -> "school"; "Hiburan" -> "movie"
    else -> "more"
}

private fun defaultCategoryColor(name: String): Color = when (name) {
    "Gaji" -> Color(0xFF3B82F6)
    "Bonus" -> Color(0xFFF59E0B)
    "Hadiah" -> Color(0xFFEC4899)
    "Bisnis" -> Color(0xFF14B8A6)
    "Investasi" -> Color(0xFF22C55E)
    "Makanan" -> Color(0xFFEF4444)
    "Transportasi" -> Color(0xFF3B82F6)
    "Belanja" -> Color(0xFFF59E0B)
    "Tagihan" -> Color(0xFF8B5CF6)
    "Kesehatan" -> Color(0xFFF43F5E)
    "Pendidikan" -> Color(0xFF0EA5E9)
    "Hiburan" -> Color(0xFFA855F7)
    else -> Color(0xFF8B7CF6)
}

/** Kunci ikon yang berlaku untuk kategori (kustom, atau bawaan berdasarkan nama). */
fun categoryIconKey(name: String, type: String? = null): String =
    CategoryStyles.find(name, type)?.icon?.takeIf { it.isNotBlank() } ?: defaultIconKey(name)

fun categoryIcon(name: String, type: String? = null): ImageVector = CategoryIcons.get(categoryIconKey(name, type))

fun categoryColor(name: String, type: String? = null): Color =
    CategoryStyles.find(name, type)?.color?.takeIf { it != 0L }?.let { Color(it) } ?: defaultCategoryColor(name)

@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick == null) {
        Surface(modifier, shape = shape, color = color, shadowElevation = 1.dp) { Column(content = content) }
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, shadowElevation = 1.dp) {
            Column(content = content)
        }
    }
}

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Hapus") } },
        shape = RoundedCornerShape(16.dp), colors = fieldColors()
    )
}

object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        val formatted = raw.reversed().chunked(3).joinToString(".").reversed()
        val o2t = IntArray(n + 1) { o ->
            if (n == 0) 0 else if (o == n) formatted.length else o + ((n - 1) / 3 - (n - o - 1) / 3)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int): Int {
                var r = 0
                for (i in 0..n) if (o2t[i] <= offset) r = i
                return r
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
fun AppDatePickerDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = Dates.localToUtc(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val sel = st.selectedDateMillis
                if (sel != null) onPick(Dates.utcToLocal(sel)) else onDismiss()
            }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    ) { DatePicker(state = st) }
}

@Composable
fun DateField(value: Long, onChange: (Long) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Surface(
        onClick = { show = true }, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(Fmt.date(value), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (show) AppDatePickerDialog(value, { onChange(it); show = false }, { show = false })
}

@Composable
fun TransactionRow(tx: TransactionEntity, cur: String, onClick: () -> Unit) {
    val income = tx.type == TYPE_INCOME
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(categoryIcon(tx.category, tx.type), categoryColor(tx.category, tx.type))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.note.ifBlank { tx.category }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.date(tx.dateMillis)} • ${if (income) "Pemasukan" else "Pengeluaran"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Fmt.signed(tx.amount, income, cur), fontWeight = FontWeight.Bold,
            color = if (income) MaterialTheme.extra.income else MaterialTheme.extra.expense
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TransactionDetailSheet(
    tx: TransactionEntity, cur: String,
    onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val income = tx.type == TYPE_INCOME
    val color = if (income) MaterialTheme.extra.income else MaterialTheme.extra.expense
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBubble(categoryIcon(tx.category, tx.type), categoryColor(tx.category, tx.type), 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(Fmt.signed(tx.amount, income, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(12.dp))
            DetailRow("Jenis", if (income) "Pemasukan" else "Pengeluaran")
            DetailRow("Kategori", tx.category)
            DetailRow("Tanggal", Fmt.date(tx.dateMillis))
            DetailRow("Keterangan", tx.note.ifBlank { "-" })
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit")
                }
                Button(
                    onClick = { confirm = true }, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Hapus")
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hapus transaksi?") },
            text = { Text("Saldo dan laporan akan diperbarui otomatis.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } }
        )
    }
}
